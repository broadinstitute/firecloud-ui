(ns broadfcui.page.workspace.notebooks.delete_cluster
  (:require
   [dmohs.react :as react]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.common.components :as comps]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils.ajax :as ajax]
   ))

(react/defc ClusterDeleter
  {:render
   (fn [{:keys [state this props]}]
     (let [{:keys [deleting? server-error]} @state
           {:keys [cluster-to-delete]} props]
       [modals/OKCancelForm
        {:header "Delete Cluster"
         :dismiss (:dismiss props)
         :ok-button {:text "Delete" :onClick #(this :-delete-cluster)}
         :content
         (react/create-element
          [:div {}
           (when deleting? (blocker "Deleting cluster..."))
           [:div {} (str "Are you sure you want to delete cluster \"" cluster-to-delete "\"?")]
           [comps/ErrorViewer {:error server-error}]])}]))

   :-delete-cluster
   (fn [{:keys [state props]}]
     (let [{:keys [cluster-to-delete]} props]
       (swap! state assoc :deleting? true)
       (swap! state dissoc :server-error)
       (endpoints/call-ajax-leo
        {:endpoint (endpoints/delete-cluster (get-in props [:workspace-id :namespace]) cluster-to-delete)
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :deleting?)
                    (if success?
                      (do
                        ((:dismiss props))
                        ((:reload-after-delete props)))
                      (swap! state assoc :server-error (get-parsed-response false))))})))})