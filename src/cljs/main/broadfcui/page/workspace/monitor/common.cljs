(ns broadfcui.page.workspace.monitor.common
  (:require
   [broadfcui.common :as common]
   [broadfcui.common.duration :as duration]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.config :as config]
   [broadfcui.utils :as utils]
   ))


(defn render-date [date]
  (if date
    (str (common/format-date date) " (" (duration/fuzzy-time-from-now-ms (.parse js/Date date) true) ")")
    [:span {:style {:fontStyle "italic"}} "Pending..."]))


(def wf-success-statuses #{"Succeeded"})
(def wf-running-statuses #{"Running" "Submitted" "Queued" "Launching"})
(def wf-failure-statuses #{"Failed" "Aborting" "Aborted"})
(def wf-all-statuses ["Queued" "Launching" "Submitted" "Running" "Aborting" "Succeeded" "Failed" "Aborted"])

(def sub-running-statuses #{"Accepted" "Evaluating" "Submitting" "Submitted"})

(def call-success-statuses #{"Done"})
(def call-running-statuses #{"NotStarted" "Starting" "Running"})
(def call-failure-statuses #{"Failed" "Aborted"})

(def google-cloud-context "https://console.cloud.google.com/storage/browser/")

(defn all-success? [submission]
  (and (every? #(contains? wf-success-statuses (:status %)) (:workflows submission))
       (zero? (count (:notstarted submission)))))

(defn any-running? [submission]
  (some #(contains? wf-running-statuses (:status %)) (:workflows submission)))

(defn any-failed? [submission]
  (or (some #(contains? wf-failure-statuses (:status %)) (:workflows submission))
      (pos? (count (:notstarted submission)))))

(def success-icon
  (icons/icon {:style {:color (:success-state style/colors) :marginRight 4
                       :width table-style/table-icon-size :height table-style/table-icon-size}
               :data-test-id "status-icon" :data-test-value "success"}
              :done))
(def running-icon
  [:span {:style {:display "inline-flex" :alignItems "center" :justifyContent "center" :verticalAlign "middle"
                  :backgroundColor (:running-state style/colors)
                  :width table-style/table-icon-size :height table-style/table-icon-size
                  :borderRadius 3 :margin "-4px 4px 0 0"}
          :data-test-id "status-icon" :data-test-value "running"}
   [icons/RunningIcon {:size 12}]])
(def failure-icon
  [:span {:style {:display "inline-flex" :alignItems "center" :justifyContent "center" :verticalAlign "middle"
                  :backgroundColor (:exception-state style/colors)
                  :width table-style/table-icon-size :height table-style/table-icon-size
                  :borderRadius 3 :margin "-4px 4px 0 0"}
          :data-test-id "status-icon" :data-test-value "failure"}
   [icons/ExceptionIcon {:size 12}]])
(def unknown-icon
  [:span {:style {:display "inline-flex" :alignItems "center" :justifyContent "center" :verticalAlign "middle"
                  :backgroundColor (:background-dark style/colors)
                  :width table-style/table-icon-size :height table-style/table-icon-size
                  :borderRadius 3 :margin "-4px 4px 0 0"}
          :data-test-id "status-icon" :data-test-value "unknown"}
   [icons/UnknownIcon {:size 12}]])


(defn icon-for-wf-status [status]
  (cond
    (contains? wf-success-statuses status) success-icon
    (contains? wf-running-statuses status) running-icon
    (contains? wf-failure-statuses status) failure-icon
    :else (do (utils/log "Unknown workflow status: " status)
              unknown-icon)))

(defn icon-for-sub-status [wf-statuses]
  (cond
    (contains? wf-statuses :Failed) failure-icon
    (contains? wf-statuses :Aborted) failure-icon
    (contains? wf-statuses :Succeeded) success-icon
    :else (do (utils/log "Unknown submission status: " wf-statuses)
              unknown-icon)))

(defn icon-for-project-status [project-status]
  (cond
    (= project-status "Error") failure-icon
    (= project-status "Ready") success-icon
    (= project-status "Creating") running-icon
    :else (do (utils/log "Unknown project status: " project-status)
              unknown-icon)))

(defn icon-for-call-status [status]
  (cond
    (contains? call-success-statuses status) success-icon
    (contains? call-running-statuses status) running-icon
    (contains? call-failure-statuses status) failure-icon
    :else (do (utils/log "Unknown call status: " status)
              unknown-icon)))


(defn call-cache-result [cache-status]
  (if (= cache-status "ReadAndWriteCache") "Enabled" "Disabled"))

(defn format-call-cache [cache-hit]
  (if cache-hit "Hit" "Miss"))

