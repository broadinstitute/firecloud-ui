(ns broadfcui.page.workspace.summary.publish
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))


(react/defc PublishButton
  {:render
   (fn [{:keys [props state]}]
     [:div {}
      (when (:publishing? @state)
        (blocker "Publishing..."))
      (when-let [error-response (:error-response @state)]
        (modals/render-error-response {:error-response error-response
                                       :dismiss #(swap! state dissoc :error-response)}))
      [buttons/SidebarButton
       {:data-test-id "publish-button"
        :style :light :color :button-primary :margin :top
        :icon :library :text "Publish in Library"
        :disabled? (:disabled? props)
        :onClick (fn [_]
                   (swap! state assoc :publishing? true)
                   (endpoints/call-ajax-orch
                    {:endpoint (endpoints/publish-workspace (:workspace-id props))
                     :on-done (fn [{:keys [success? get-parsed-response]}]
                                (swap! state dissoc :publishing?)
                                (if success?
                                  (do ((:show-publish-message props)
                                       {:header "Success!"
                                        :text "Successfully published to Library"})
                                      ((:request-refresh props)))
                                  (swap! state assoc :error-response (get-parsed-response false))))}))}]])})

(react/defc UnpublishButton
  {:render
   (fn [{:keys [props state]}]
     [:div {}
      (when (:unpublishing? @state)
        (blocker "Unpublishing..."))
      (when-let [error-response (:error-response @state)]
        (modals/render-error-response {:error-response error-response
                                       :dismiss #(swap! state dissoc :error-response)}))
      [buttons/SidebarButton
       {:style :light :color :state-exception :margin :top
        :icon :library :text "Unpublish"
        :onClick (fn [_]
                   (swap! state assoc :unpublishing? true)
                   (endpoints/call-ajax-orch
                    {:endpoint (endpoints/unpublish-workspace (:workspace-id props))
                     :on-done (fn [{:keys [success? get-parsed-response]}]
                                (swap! state dissoc :unpublishing?)
                                (if success?
                                  (do ((:show-publish-message props)
                                       {:header "Success!"
                                        :text "This dataset is no longer displayed in the Data Library catalog."})
                                      ((:request-refresh props)))
                                  (swap! state assoc :error-response (get-parsed-response false))))}))}]])})
