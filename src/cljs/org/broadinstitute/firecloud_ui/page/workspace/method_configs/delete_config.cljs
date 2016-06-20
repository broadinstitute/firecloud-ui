(ns org.broadinstitute.firecloud-ui.page.workspace.method-configs.delete-config
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    ))

(react/defc DeleteDialog
  {:render
   (fn [{:keys [props state]}]
     (dialog/standard-dialog
       {:width 500 :dismiss-self (:dismiss-self props)
        :header "Confirm Delete"
        :content
        (cond
          (:error @state) [comps/ErrorViewer {:error (:error @state)}]
          (:deleting? @state) [:div {:style {:backgroundColor "#fff" :padding "1em"}}
                               [comps/Spinner {:text "Deleting..."}]]
          :else "Are you sure you want to delete this method configuration?")
        :ok-button
        [comps/Button
         {:text "Delete"
          :onClick
          #(do (swap! state assoc :deleting? true :error nil)
            (endpoints/call-ajax-orch
              {:endpoint (endpoints/delete-workspace-method-config (:workspace-id props) (:config props))
               :on-done (fn [{:keys [success? get-parsed-response]}]
                          (swap! state dissoc :deleting?)
                          (if success?
                            ((:after-delete props))
                            (swap! state assoc :error (get-parsed-response))))}))}]}))
   :component-did-mount
   (fn []
     (common/scroll-to-top 100))})
