(ns broadfcui.page.groups.create-group
  (:require
    [dmohs.react :as react]
    clojure.string
    [broadfcui.common.components :as comps]
    [broadfcui.common.input :as input]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.config :as config]
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
         [input/TextField {:ref "name-field"
                           :style {:width "100%" :marginTop "1em" :marginBottom 0}
                           :predicates [{:test #(re-matches #"\S*" %) :message "Enter a name with no spaces."}]}]
         (style/create-validation-error-message (:validation-errors @state))
         [:div {:style {:marginBottom "1.5em"
                        :color (if (:validation-errors @state) (:exception-state style/colors) (:text-lighter style/colors))
                        :fontSize "0.8em"}}
          "Group names may not contain spaces."]

         [comps/ErrorViewer {:error (:server-error @state)}]])
       :ok-button #(this :create-group)}])
   :create-group
   (fn [{:keys [props state refs]}]
     (let [[name & fails] (input/get-and-validate refs "name-field")]
       (swap! state assoc :validation-errors fails)
       (when-not fails
         (do
           (swap! state assoc :creating? true)
           (endpoints/call-ajax-orch
            {:endpoint (endpoints/create-group name)
             :headers utils/content-type=json
             :on-done (fn [{:keys [success? get-parsed-response]}]
                        (swap! state dissoc :creating?)
                        (if success?
                          (do ((:on-success props))
                              (modal/pop-modal))
                          (swap! state assoc :server-error (get-parsed-response false))))})))))})
