(ns broadfcui.page.workspace.notebooks.cluster-error
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   ))

(react/defc ClusterErrorViewer
  ; Note cluster errors are only returned on "get cluster" requests to Leo, not "list cluster" requests.
  ; Therefore we need issue an additional ajax call to Leo before we can display the errors.
  {:component-did-mount
   (fn [{:keys [this]}]
     (this :-get-cluster-details))
   :render
   (fn [{:keys [state this props]}]
     (let [{:keys [cluster-to-view-details server-error]} @state
           {:keys [cluster-to-view dismiss]} props]
       [modals/OKCancelForm
        {:header "Cluster Error"
         :dismiss dismiss
         :ok-button {:text "Done" :onClick dismiss}
         :show-cancel? false
         :content
         (react/create-element
          [:div {:style {:width 700}}
           (when-not cluster-to-view-details (blocker "Getting error details..."))
           [comps/ErrorViewer {:data-test-id "notebooks-error" :error server-error}]
           [:span {} (str "Cluster " (:clusterName cluster-to-view) " failed with message:")]
           [:div {:style {:marginTop "1em" :whiteSpace "pre-wrap" :fontFamily "monospace"
                          :fontSize "90%" :maxHeight 206
                          :backgroundColor "#fff" :padding "1em" :borderRadius 8}}
            (:errorMessage (first (:errors cluster-to-view-details)))]])}]))

   :-get-cluster-details
   (fn [{:keys [props state]}]
     (let [{:keys [cluster-to-view]} props]
       (endpoints/call-ajax-leo
        {:endpoint (endpoints/get-cluster-details (get-in props [:workspace-id :namespace]) (:clusterName cluster-to-view))
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (if success?
                      (swap! state assoc :cluster-to-view-details (get-parsed-response))
                      (swap! state assoc :server-error (get-parsed-response false))))})))})
