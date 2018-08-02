(ns broadfcui.page.workspace.notebooks.cluster_details
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils.ajax :as ajax]
   ))

(react/defc ClusterDetailsViewer
  {:component-did-mount
   (fn [{:keys [this]}]
     (this :-get-cluster-details))
   :render
   (fn [{:keys [state this props]}]
     (let [{:keys [server-response cluster-details]} @state
           {:keys [server-error]} server-response]
       [modals/OKCancelForm
        {:header "Cluster Details"
         :dismiss (:dismiss props)
         :ok-button {:text "Close" :onClick (:dismiss props)}
         :show-cancel? false
         :content
         (react/create-element
          [:div {}
           [comps/ErrorViewer {:error server-error}]
           (if cluster-details
             (str cluster-details) ;TODO display in a nicer way than just raw json
             (spinner "Getting cluster details for cluster..."))])}]))

   :-get-cluster-details
   (fn [{:keys [state this props]}]
     (let [{:keys [cluster-to-display-details]} props]
       (swap! state assoc :querying? true)
       (swap! state dissoc :server-error)
       (endpoints/call-ajax-leo
        {:endpoint (endpoints/get-cluster-details (get-in props [:workspace-id :namespace]) cluster-to-display-details)
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :querying?)
                    (if success?
                      (swap! state assoc :cluster-details (get-parsed-response))
                      (swap! state assoc :server-error (get-parsed-response false))))})))})