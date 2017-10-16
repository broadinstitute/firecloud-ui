(ns broadfcui.components.queue-status
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.style :as style]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.common.duration :as duration]
   [broadfcui.common.links :as links]
   [broadfcui.common.components :as comps]))

(react/defc QueueStatus
  {:render
   (fn [{:keys [state this]}]
     (let [{:keys [queue-status queue-error]} @state
           {:keys [queue-time queue-position queued active]} queue-status]
       [:div {:style {:marginBottom "0.5em"}}
        (cond
          queue-error (style/create-server-error-message queue-error)
          (not queue-status) [comps/Spinner {:text "Loading submission queue status..."}]
          :else
          [:div {}
           (this :-row "Estimated wait time:" (duration/fuzzy-time-from-now-ms (+ (.now js/Date) queue-time) false))
           (this :-row "Workflows ahead of yours:" queue-position)
           (this :-row "Queue status:" (str queued " Queued; " active " Active")
                 [:div {:style {:display "inline-block" :marginLeft "1ex" :fontStyle "italic"}} (links/create-internal {:data-test-id "queue-status-refresh" :onClick (fn [] (this :-load-data) (swap! state assoc :queue-status nil))} "(refresh)")])])]))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :-load-data))
   :-load-data
   (fn [{:keys [state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/submissions-queue-status)
       :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                  (if success?
                    (swap! state assoc :queue-status (common/queue-status-counts (get-parsed-response false)))
                    (swap! state assoc :queue-error status-text)))}))
   :-row
   (fn [_ label content button]
     [:div {}
      [:div {:style {:display "inline-block" :width 200 :textAlign "right" :marginRight "1ex"}} label]
      [:div {:style {:display "inline-block"}} content]
      button])})