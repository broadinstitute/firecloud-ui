(ns broadfcui.page.groups.create-group
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.input :as input]
   [broadfcui.common.style :as style]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   ))

(react/defc CreateGroupDialog
  {:render
   (fn [{:keys [props state this]}]
     [modals/OKCancelForm
      {:header "Create Group"
       :content
       (react/create-element
        [:div {:style {:width 750}}
         (when (:creating? @state)
           (blocker "Creating group..."))
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
       :dismiss (:dismiss props)
       :ok-button #(this :create-group)}])
   :create-group
   (fn [{:keys [props state refs]}]
     (let [[name & fails] (input/get-and-validate refs "name-field")]
       (swap! state assoc :validation-errors fails)
       (when-not fails
         (swap! state assoc :creating? true)
         (endpoints/call-ajax-orch
          {:endpoint (endpoints/create-group name)
           :headers ajax/content-type=json
           :on-done (fn [{:keys [success? get-parsed-response]}]
                      (swap! state dissoc :creating?)
                      (if success?
                        (do ((:on-success props))
                            ((:dismiss props)))
                        (swap! state assoc :server-error (get-parsed-response false))))}))))})
