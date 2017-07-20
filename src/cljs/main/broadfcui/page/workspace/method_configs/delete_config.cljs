(ns broadfcui.page.workspace.method-configs.delete-config
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.modal :as modal]
   [broadfcui.endpoints :as endpoints]
   ))

(react/defc DeleteDialog
  {:render
   (fn [{:keys [props state]}]
     [comps/OKCancelForm
      {:header "Confirm Delete"
       :content
       [:div {:style {:width 500}}
        (cond
          (:error @state) [comps/ErrorViewer {:error (:error @state)}]
          (:deleting? @state) [:div {:style {:backgroundColor "#fff" :padding "1em"}}
                               [comps/Spinner {:text "Deleting..."}]]
          :else "Are you sure you want to delete this method configuration?")]
       :ok-button
       {:text "Delete"
        :onClick
        #(do (swap! state assoc :deleting? true :error nil)
             (endpoints/call-ajax-orch
              {:endpoint (endpoints/delete-workspace-method-config (:workspace-id props) (:config-id props))
               :on-done (fn [{:keys [success? get-parsed-response]}]
                          (swap! state dissoc :deleting?)
                          (if success?
                            (do (modal/pop-modal) ((:after-delete props)))
                            (swap! state assoc :error (get-parsed-response false))))}))}}])})
