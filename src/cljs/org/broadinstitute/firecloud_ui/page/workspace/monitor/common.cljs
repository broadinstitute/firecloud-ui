(ns org.broadinstitute.firecloud-ui.page.workspace.monitor.common
  (:require
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn render-date [date]
  (if date
    (let [m (js/moment date)]
      (str (.format m "L [at] LTS") " (" (.fromNow m) ")"))
    [:span {:style {:fontStyle "italic"}} "Pending..."]))


(def wf-success-statuses #{"Succeeded"})
(def wf-running-statuses #{"Running" "Submitted" "Queued" "Launching"})
(def wf-failure-statuses #{"Failed" "Aborting" "Aborted" "Unknown"})
(def wf-all-statuses ["Queued" "Launching" "Submitted" "Running" "Aborting" "Succeeded" "Failed" "Aborted"])

(def sub-running-statuses #{"Accepted" "Evaluating" "Submitting" "Submitted"})

(def call-success-statuses #{"Done"})
(def call-running-statuses #{"NotStarted" "Starting" "Running"})
(def call-failure-statuses #{"Failed" "Aborted"})

(defn all-success? [submission]
  (and (every? #(contains? wf-success-statuses (% "status")) (submission "workflows"))
    (zero? (count (submission "notstarted")))))

(defn any-running? [submission]
  (some #(contains? wf-running-statuses (% "status")) (submission "workflows")))

(defn any-failed? [submission]
  (or (some #(contains? wf-failure-statuses (% "status")) (submission "workflows"))
    (pos? (count (submission "notstarted")))))

(def ^:private success-icon
  (icons/font-icon {:style {:color (:success-green style/colors) :fontSize 12 :marginRight 4}}
    :status-done))
(def ^:private running-icon
  [:span {:style {:backgroundColor (:running-blue style/colors) :position "relative"
                  :width 16 :height 16 :display "inline-block" :borderRadius 3
                  :verticalAlign "middle" :marginTop -4 :marginRight 4}}
   (style/center {} [icons/RunningIcon {:size 12}])])
(def ^:private failure-icon
  [:span {:style {:backgroundColor (:exception-red style/colors) :position "relative"
                  :width 16 :height 16 :display "inline-block" :borderRadius 3
                  :verticalAlign "middle" :marginTop -4 :marginRight 4}}
   (style/center {} [icons/ExceptionIcon {:size 12}])])


(defn icon-for-wf-status [status]
  (cond
    (contains? wf-success-statuses status) success-icon
    (contains? wf-running-statuses status) running-icon
    (contains? wf-failure-statuses status) failure-icon
    :else (do (utils/log "Unknown workflow status: " status)
            failure-icon)))

(defn icon-for-call-status [status]
  (cond
    (contains? call-success-statuses status) success-icon
    (contains? call-running-statuses status) running-icon
    (contains? call-failure-statuses status) failure-icon
    :else (do (utils/log "Unknown call status: " status)
            failure-icon)))
