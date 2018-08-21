(ns broadfcui.page.workspace.notebooks.delete-cluster
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   ))

(react/defc ClusterDeleter
  {:render
   (fn [{:keys [state this props]}]
     (let [{:keys [deleting? server-error]} @state
           {:keys [cluster-to-delete dismiss]} props]
       [modals/OKCancelForm
        {:header "Delete Cluster"
         :dismiss dismiss
         :ok-button {:text "Delete" :onClick #(this :-delete-cluster)}
         :content
         (react/create-element
          [:div {}
           (when deleting? (blocker "Deleting cluster..."))
           [:div {} (str "Are you sure you want to delete cluster \"" cluster-to-delete "\"?")]
           [comps/ErrorViewer {:error server-error}]])}]))

   :-delete-cluster
   (fn [{:keys [state props]}]
     (let [{:keys [cluster-to-delete reload-after-delete dismiss]} props]
       (utils/multi-swap! state (assoc :deleting? true) (dissoc :server-error))
       (endpoints/call-ajax-leo
        {:endpoint (endpoints/delete-cluster (get-in props [:workspace-id :namespace]) cluster-to-delete)
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :deleting?)
                    (if success?
                      (do
                        (dismiss)
                        (reload-after-delete))
                      (swap! state assoc :server-error (get-parsed-response false))))})))})
