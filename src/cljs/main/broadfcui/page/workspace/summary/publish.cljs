(ns broadfcui.page.workspace.summary.publish
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))


(react/defc PublishButton
  {:render
   (fn [{:keys [props state]}]
     [:div {}
      (when (:publishing? @state)
        [comps/Blocker {:banner "Publishing..."}])
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
                                  (do (comps/push-message
                                       {:header "Success!"
                                        :message "Successfully published to Library"})
                                      ((:request-refresh props)))
                                  (comps/push-error-response
                                   (get-parsed-response false))))}))}]])})

(react/defc UnpublishButton
  {:render
   (fn [{:keys [props state]}]
     [:div {}
      (when (:unpublishing? @state)
        [comps/Blocker {:banner "Unpublishing..."}])
      [buttons/SidebarButton
       {:style :light :color :exception-state :margin :top
        :icon :library :text "Unpublish"
        :onClick (fn [_]
                   (swap! state assoc :unpublishing? true)
                   (endpoints/call-ajax-orch
                    {:endpoint (endpoints/unpublish-workspace (:workspace-id props))
                     :on-done (fn [{:keys [success? get-parsed-response]}]
                                (swap! state dissoc :unpublishing?)
                                (if success?
                                  (do (comps/push-message
                                       {:header "Success!"
                                        :message "This dataset is no longer displayed in the Data Library catalog."})
                                      ((:request-refresh props)))
                                  (comps/push-error-response
                                   (get-parsed-response false))))}))}]])})
