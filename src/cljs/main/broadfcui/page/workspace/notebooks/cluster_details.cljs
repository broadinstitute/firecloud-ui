(ns broadfcui.page.workspace.notebooks.cluster-details
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   ))

(react/defc ClusterDetailsViewer
  {:component-did-mount
   (fn [{:keys [this]}]
     (this :-get-cluster-details))
   :render
   (fn [{:keys [state this props]}]
     (let [{:keys [server-response cluster-details]} @state
           {:keys [server-error]} server-response
           {:keys [dismiss]} props]
       [modals/OKCancelForm
        {:header "Cluster Details"
         :dismiss dismiss
         :ok-button {:text "Close" :onClick dismiss}
         :show-cancel? false
         :content
         (react/create-element
          [:div {}
           [comps/ErrorViewer {:error server-error}]
           (if cluster-details
             (js/JSON.stringify (clj->js cluster-details) nil 2) ;TODO display in a nicer way than just json
             (spinner "Getting cluster details for cluster..."))])}]))

   :-get-cluster-details
   (fn [{:keys [state this props]}]
     (let [{:keys [cluster-to-display-details]} props]
       (utils/multi-swap! state (assoc :querying? true) (dissoc :server-error))
       (endpoints/call-ajax-leo
        {:endpoint (endpoints/get-cluster-details (get-in props [:workspace-id :namespace]) cluster-to-display-details)
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :querying?)
                    (if success?
                      (swap! state assoc :cluster-details (get-parsed-response))
                      (swap! state assoc :server-error (get-parsed-response false))))})))})
