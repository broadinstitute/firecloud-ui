(ns broadfcui.page.workspace.notebooks.cluster_details
  (:require
   [dmohs.react :as react]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.common.components :as comps]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils.ajax :as ajax]
   ))

(react/defc ClusterDetails
  {:component-did-mount
   (fn [{:keys [this]}]
     (this :-get-cluster-details))

   :render
   (fn [{:keys [state this props]}]
     (let [cluster (:cluster-to-display-details props)]
       [modals/OKCancelForm
        {:header "Cluster Details"
         :dismiss (:dismiss props)
         :ok-button {:text "Close" :onClick (:dismiss props)}
         :show-cancel? false
         :content
         (react/create-element
          [:div {}
           (if-let [cluster-details (:cluster-details @state)]
             (str cluster-details)
             (spinner "Getting cluster details for cluster..."))])
         }
        ]
       )

     )

   :-get-cluster-details
   (fn [{:keys [state this props]}]
     (let [cluster (:cluster-to-display-details props)]
       (swap! state assoc :querying? true)
       (swap! state dissoc :server-error)
       (endpoints/call-ajax-leo
        {:endpoint (endpoints/get-cluster-details (get-in props [:workspace-id :namespace]) cluster)
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :querying?)
                    (if success?
                      (swap! state assoc :cluster-details (get-parsed-response))
                      (swap! state assoc :server-error (get-parsed-response false))))})))})