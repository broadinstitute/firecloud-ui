(ns broadfcui.page.groups.create-group
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.input :as input]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))

(react/defc CreateGroupDialog
  {:render
   (fn [{:keys [state this]}]
     [comps/OKCancelForm
      {:header "Create Group"
       :content
       (react/create-element
        [:div {:style {:width 750}}
         (when (:creating? @state)
           [comps/Blocker {:banner "Creating group..."}])
         [:div {:style {:fontSize "120%"}}
          "Enter a unique name:"]
         [input/TextField {:ref "name-field" :autoFocus true
                           :style {:width "100%" :marginTop "1em" :marginBottom 0}
                           :predicates [(input/nonempty "Group name")
                                        (input/alphanumeric_- "Group name")
                                        (input/max-string-length "Group name" 50)]}]
         (style/create-validation-error-message (:validation-errors @state))
         [:div {:style {:marginBottom "1.5em"
                        :color (if (:validation-errors @state) (:state-exception style/colors) (:text-lighter style/colors))
                        :fontSize "0.8em"}}
          input/hint-alphanumeric_-]

         [comps/ErrorViewer {:error (:server-error @state)}]])
       :ok-button #(this :create-group)}])
   :create-group
   (fn [{:keys [props state refs]}]
     (let [[name & fails] (input/get-and-validate refs "name-field")]
       (swap! state assoc :validation-errors fails)
       (when-not fails
         (swap! state assoc :creating? true)
         (endpoints/call-ajax-orch
          {:endpoint (endpoints/create-group name)
           :headers utils/content-type=json
           :on-done (fn [{:keys [success? get-parsed-response]}]
                      (swap! state dissoc :creating?)
                      (if success?
                        (do ((:on-success props))
                            (modal/pop-modal))
                        (swap! state assoc :server-error (get-parsed-response false))))}))))})
