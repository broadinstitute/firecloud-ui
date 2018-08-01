(ns broadfcui.page.workspace.monitor.tab
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.components.queue-status :refer [QueueStatus]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.page.workspace.monitor.common :as moncommon]
   [broadfcui.page.workspace.monitor.submission-details :as submission-details]
   [broadfcui.utils :as utils]
   ))

(defn- render-date [submission]
  (common/format-date (:submissionDate submission)))

(defn- render-submissions-table [workspace-id submissions bucketName]
  [Table
   {:persistence-key (str (common/workspace-id->string workspace-id) ":monitor") :v 1
    :data submissions
    :body
    {:style table-style/table-heavy
     :behavior {:fixed-column-count 1}
     :empty-message "There are no analyses to display."
     :columns
     [{:id "view" :initial-width 50
       :resizable? false :sortable? false :filterable? false :hidden? true
       :column-data :submissionId
       :as-text (constantly "View analysis details")
       :render #(links/create-internal {:data-test-id (str "submission-" %)
                                        :href (nav/get-link :workspace-submission workspace-id %)}
                  "View")}
      {:header "Status"
       :sort-by (fn [submission]
                  (moncommon/sort-order-submission (:status submission) (:workflowStatuses submission)))
       :as-text (fn [submission] (.stringify js/JSON (clj->js (:workflowStatuses submission))))
       :render (fn [submission]
                 [:div {:style {:height table-style/table-icon-size}}
                  (moncommon/icon-for-submission (:status submission) (:workflowStatuses submission))
                  (:status submission)])}
      {:header "Method Configuration" :initial-width 300
       :column-data (juxt :methodConfigurationNamespace :methodConfigurationName)
       :as-text (partial clojure.string/join "/")}
      {:header "Date" :initial-width 200 :as-text render-date
       :sort-by :submissionDate :sort-initial :desc}
      {:header "Data Entity" :initial-width 220
       :column-data (comp (juxt :entityName :entityType) :submissionEntity)
       :as-text (fn [[entity-name entity-type]]
                  (if entity-type
                    (str entity-name " (" entity-type ")")
                    "N/A"))}
      {:header "Submitted By" :initial-width 220 :column-data :submitter}
      {:header "Submission ID" :initial-width 235 :column-data :submissionId
       :render (fn [submission-id]
                 (links/create-external {:href (str moncommon/google-storage-context
                                                    bucketName "/" submission-id "/")}
                   submission-id))}]}
    :toolbar
    {:style {:alignItems "flex-end"}
     :get-items (constantly [flex/spring [QueueStatus]])}}])


(react/defc- SubmissionsList
  {:reload
   (fn [{:keys [state this]}]
     (swap! state dissoc :server-response)
     (this :-load-submissions))
   :render
   (fn [{:keys [props state]}]
     (let [server-response (:server-response @state)
           {:keys [submissions error-message]} server-response]
       (cond
         (nil? server-response)
         [:div {:style {:textAlign "center"}} (spinner "Loading analyses...")]
         error-message (style/create-server-error-message error-message)
         :else
         (render-submissions-table (:workspace-id props) submissions (:bucketName props)))))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :-load-submissions))
   :-load-submissions
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/list-submissions (:workspace-id props))
       :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                  (swap! state assoc :server-response (if success?
                                                        {:submissions (get-parsed-response)}
                                                        {:error-message status-text})))}))})


(react/defc Page
  {:refresh
   (fn [{:keys [props refs]}]
     (when-not (:submission-id props)
       ((@refs "submissions-list") :reload)))
   :render
   (fn [{:keys [props]}]
     (let [{:keys [submission-id workspace-id]} props
           bucketName (get-in (:workspace props) [:workspace :bucketName])]
       [:div {:style {:padding "1rem 1.5rem"}}
        (if submission-id
          [submission-details/Page {:key submission-id
                                    :workspace-id workspace-id
                                    :bucketName bucketName
                                    :submission-id submission-id
                                    :workflow-id (:workflow-id props)}]
          [SubmissionsList {:ref "submissions-list"
                            :workspace-id workspace-id
                            :bucketName bucketName}])]))})
