(ns broadfcui.page.workspace.monitor.common
  (:require
   [broadfcui.common :as common]
   [broadfcui.common.duration :as duration]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.utils :as utils]
   [broadfcui.components.foundation-dropdown :as dropdown]))


(defn render-date [date]
  (if date
    (str (common/format-date date) " (" (duration/fuzzy-time-from-now-ms (.parse js/Date date) true) ")")
    [:span {:style {:fontStyle "italic"}} "Pending..."]))

(defn render-cost [cost]
  (cond
    (nil? cost)
      [:span {}
       "n/a"
       (dropdown/render-info-box
        {:text "Costs may take up to one day to populate."})]
    (number? cost)
      (common/format-price cost)
    :else
      (style/create-inline-error-message cost)))

(def wf-success-statuses #{"Succeeded"})
(def wf-running-statuses #{"Running" "Submitted" "Queued" "Launching"})
(def wf-failure-statuses #{"Failed" "Aborting" "Aborted"})
(def wf-all-statuses ["Queued" "Launching" "Submitted" "Running" "Aborting" "Succeeded" "Failed" "Aborted"])

(def sub-running-statuses #{"Accepted" "Evaluating" "Submitting" "Submitted"})

(def call-success-statuses #{"Done"})
(def call-running-statuses #{"NotStarted" "Starting" "Running"})
(def call-failure-statuses #{"Failed" "Aborted"})

(defn google-account-chooser-prefix [destination] (str "https://accounts.google.com/AccountChooser?continue=" destination))
(def google-storage-context (google-account-chooser-prefix "https://console.cloud.google.com/storage/browser/"))
(defn google-billing-context [project-name] (google-account-chooser-prefix (str "https://console.cloud.google.com/home/dashboard?project=" project-name)))

(defn all-success? [submission]
  (and (every? #(contains? wf-success-statuses (:status %)) (:workflows submission))
       (zero? (count (:notstarted submission)))))

(defn any-running? [submission]
  (some #(contains? wf-running-statuses (:status %)) (:workflows submission)))

(defn any-failed? [submission]
  (or (some #(contains? wf-failure-statuses (:status %)) (:workflows submission))
      (pos? (count (:notstarted submission)))))

;; Icons are functions so that config/when-debug is evaluated at render

(defn render-success-icon []
  (icons/render-icon {:style {:color (:state-success style/colors) :marginRight 4
                              :width table-style/table-icon-size :height table-style/table-icon-size}
                      :data-test-id "status-icon" :data-test-value "success"}
                     :done))
(defn render-running-icon []
  [:span {:style {:display "inline-flex" :alignItems "center" :justifyContent "center" :verticalAlign "middle"
                  :backgroundColor (:state-running style/colors)
                  :width table-style/table-icon-size :height table-style/table-icon-size
                  :borderRadius 3 :margin "-4px 4px 0 0"}
          :data-test-id "status-icon" :data-test-value "running"}
   [icons/RunningIcon {:size 12}]])
(defn render-failure-icon []
  [:span {:style {:display "inline-flex" :alignItems "center" :justifyContent "center" :verticalAlign "middle"
                  :backgroundColor (:state-exception style/colors)
                  :width table-style/table-icon-size :height table-style/table-icon-size
                  :borderRadius 3 :margin "-4px 4px 0 0"}
          :data-test-id "status-icon" :data-test-value "failure"}
   [icons/ExceptionIcon {:size 12}]])
(defn render-unknown-icon []
  [:span {:style {:display "inline-flex" :alignItems "center" :justifyContent "center" :verticalAlign "middle"
                  :backgroundColor (:background-dark style/colors)
                  :width table-style/table-icon-size :height table-style/table-icon-size
                  :borderRadius 3 :margin "-4px 4px 0 0"}
          :data-test-id "status-icon" :data-test-value "unknown"}
   [icons/UnknownIcon {:size 12}]])

(defn sort-order-sub-status [wf-statuses]
  (cond
    (contains? wf-statuses :Failed) 20
    (contains? wf-statuses :Aborted) 30
    (contains? wf-statuses :Succeeded) 40
    :else (do (utils/log "Unknown status: " wf-statuses) 50)))

(defn sort-order-wf-status [status]
  (cond
    (contains? wf-running-statuses status) 60
    (contains? wf-failure-statuses status) 70
    (contains? wf-success-statuses status) 80
    :else (do (utils/log "Unknown status: " status) 90)))

(defn sort-order-submission [sub-status wf-statuses]
  (if (= "Done" sub-status)
    (sort-order-sub-status wf-statuses)
    (sort-order-wf-status sub-status)))

(defn- icon-for-sort-index [sort-index]
  (case sort-index           ; icon / submission status / workflow status
    20 (render-failure-icon) ; ! / Done / at least one Failed
    30 (render-failure-icon) ; ! / Done / at least one Aborted
    40 (render-success-icon) ; ✓ / Done / at least one Succeeded
    50 (render-unknown-icon) ; ? / Done / unknown workflow status
    60 (render-running-icon) ; = / Submitted / any
    70 (render-failure-icon) ; ! / Aborting | Aborted / any
    80 (render-success-icon) ; ✓ / Succeeded / any
    90 (render-unknown-icon) ; ? / unknown submission status / any
    (render-unknown-icon)))  ; ? / something went wrong in the code

(defn icon-for-wf-status [status]
  (icon-for-sort-index (sort-order-wf-status status)))

(defn icon-for-sub-status [wf-statuses]
  (icon-for-sort-index (sort-order-sub-status wf-statuses)))

(defn icon-for-submission [sub-status wf-statuses]
  (icon-for-sort-index (sort-order-submission sub-status wf-statuses)))

(defn icon-for-project-status [project-status]
  (cond
    (= project-status "Error") (render-failure-icon)
    (= project-status "Ready") (render-success-icon)
    (= project-status "Creating") (render-running-icon)
    :else (do (utils/log "Unknown project status: " project-status)
              (render-unknown-icon))))

(defn icon-for-call-status [status]
  (cond
    (contains? call-success-statuses status) (render-success-icon)
    (contains? call-running-statuses status) (render-running-icon)
    (contains? call-failure-statuses status) (render-failure-icon)
    :else (do (utils/log "Unknown call status: " status)
              (render-unknown-icon))))

(defn icons-for-call-statuses [statuses]
  [:span {}
    (when (some #(contains? call-success-statuses %) statuses)
     (render-success-icon))
    (when (some #(contains? call-failure-statuses %) statuses)
     (render-failure-icon))
    (when (some #(contains? call-running-statuses %) statuses)
      (render-running-icon))])

(defn format-call-cache [cache-hit]
  (if cache-hit "Hit" "Miss"))

