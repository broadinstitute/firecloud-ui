(ns broadfcui.page.workspace.method-configs.delete-config
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))

(react/defc DeleteDialog
  {:render
   (fn [{:keys [props state]}]
     [modals/OKCancelForm
      {:header "Confirm Delete"
       :content
       [:div {:style {:width 500}}
        (cond
          (:error @state) [comps/ErrorViewer {:error (:error @state)}]
          (:deleting? @state) [:div {:style {:backgroundColor "#fff" :padding "1em"}}
                               [comps/Spinner {:text "Deleting..."}]]
          :else "Are you sure you want to delete this method configuration?")]
       :dismiss (:dismiss props)
       :ok-button
       {:text "Delete"
        :data-test-id "modal-confirm-delete-button"
        :onClick
        (fn [_]
          (utils/multi-swap! state (assoc :deleting? true) (dissoc :error))
          (endpoints/call-ajax-orch
           {:endpoint (endpoints/delete-workspace-method-config (:workspace-id props) (:config-id props))
            :on-done (fn [{:keys [success? get-parsed-response]}]
                       (swap! state dissoc :deleting?)
                       (if success?
                         (do ((:dismiss props)) ((:after-delete props)))
                         (swap! state assoc :error (get-parsed-response false))))}))}}])})
