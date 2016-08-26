(ns org.broadinstitute.firecloud-ui.workspace-cache
  (:require
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


;; Several separate parts of the UI need to hit the 'get-workspace' endpoint for
;; various bits of info.  In order to eliminate multiple simultaneous calls in a
;; flexible way, cache the results for a short time.

;; Cache of workspace-id -> :pending flag or AJAX result
(defonce ^:private cache (atom {}))

;; Map of workspace-id -> awaiting callback functions
(defonce ^:private awaiting (atom {}))

(defn get-workspace [workspace-id on-done]
  (if-let [workspace-response (get @cache workspace-id)]
    (if (= workspace-response :pending)
      (swap! awaiting update-in [workspace-id] conj on-done)
      (on-done workspace-response))
    (do
      (swap! cache assoc workspace-id :pending)
      (swap! awaiting assoc workspace-id [on-done])
      (endpoints/call-ajax-orch
        {:endpoint (endpoints/get-workspace workspace-id)
         :on-done (fn [response]
                    (swap! cache assoc workspace-id response)
                    (js/setTimeout #(swap! cache dissoc workspace-id) 1000)
                    (doseq [on-done (get @awaiting workspace-id)]
                      (on-done response))
                    (swap! awaiting dissoc workspace-id))}))))
