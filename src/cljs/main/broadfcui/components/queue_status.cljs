(ns broadfcui.components.queue-status
  (:require
   [dmohs.react :as react]
   [broadfcui.components.buttons :as button]
   [broadfcui.common.components :as comps]
   [broadfcui.common.duration :as duration]
   [broadfcui.common.style :as style]
   [broadfcui.endpoints :as endpoints]
   ))

(react/defc QueueStatus
  {:render
   (fn [{:keys [state this]}]
     (let [{:keys [queue-status queue-error]} @state
           {:keys [queue-time queue-position queued active]} queue-status
           make-row (fn [label content]
                      [:div {}
                       [:div {:style {:display "inline-block" :width 200 :textAlign "right" :marginRight "1rem"}} label]
                       [:div {:style {:display "inline-block"}} content]])]
       [:div {:style {:display "flex" :alignItems "center" :height "6.3ex"}} ; about 3 lines tall
        (cond
          queue-error (style/create-server-error-message [:div {}
                                                          [:div {} "Could not load queue status."]
                                                          [:div {} queue-error]])
          (not queue-status) [:div {} [comps/Spinner {:text "Loading submission queue status..."}]]
          :else
          [:div {}
           (make-row "Estimated wait time:" (duration/fuzzy-duration-ms 0 queue-time))
           (make-row "Workflows ahead of yours:" queue-position)
           (make-row "Queue status:" (str queued " Queued; " active " Active"))])
        (when (or queue-error queue-status)
          [button/Button {:data-test-id "queue-status-refresh"
                          :style {:marginLeft "1rem"}
                          :type :secondary
                          :icon :refresh
                          :onClick #(this :-load-data)}])]))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :-load-data))
   :-load-data
   (fn [{:keys [state]}]
     (swap! state dissoc :queue-status :queue-error)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/submissions-queue-status)
       :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                  (if success?
                    (let [{:keys [workflowCountsByStatus estimatedQueueTimeMS workflowsBeforeNextUserWorkflow]} (get-parsed-response)]
                      (swap! state assoc :queue-status ; GAWB-666 Queued and Cromwell (active) counts})
                             {:queue-time (or estimatedQueueTimeMS 0)
                              :queue-position (or workflowsBeforeNextUserWorkflow 0)
                              :queued (apply + (map workflowCountsByStatus [:Queued :Launching]))
                              :active (apply + (map workflowCountsByStatus [:Submitted :Running :Aborting]))}))
                    (swap! state assoc :queue-error status-text)))}))})
