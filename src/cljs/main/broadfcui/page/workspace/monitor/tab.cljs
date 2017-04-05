(ns broadfcui.page.workspace.monitor.tab
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.style :as style]
    [broadfcui.common.table.table :refer [Table]]
    [broadfcui.common.table.style :as table-style]
    [broadfcui.common.table.utils :as table-utils]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.nav :as nav]
    [broadfcui.page.workspace.monitor.common :as moncommon]
    [broadfcui.page.workspace.monitor.submission-details :as submission-details]
    [broadfcui.utils :as u]
    ))

(defn- render-date [submission]
  (common/format-date (:submissionDate submission)))

(defn- render-submissions-table [workspace-id submissions bucketName]
  [Table
   {:persistence-key "monitor" :v 1
    :body
    {:style table-style/table-heavy
     :data-source (table-utils/local submissions)
     :empty-message "There are no analyses to display."
     :columns
     [{:header "Date" :initial-width 200 :as-text render-date
       :sort-by :submissionDate :sort-initial :desc
       :render (fn [submission]
                 (style/create-link {:text (render-date submission)
                                     :href (nav/get-link
                                            :broadfcui.page.workspace.details/submission
                                            workspace-id
                                            (:submissionId submission))}))}
      {:header "Status" :as-text :status :sort-by :text
       :render (fn [submission]
                 [:div {:style {:height table-style/table-icon-size}}
                  (when (= "Done" (:status submission))
                    (moncommon/icon-for-sub-status (:workflowStatuses submission)))
                  (:status submission)])}
      {:header "Method Configuration" :initial-width 300
       :column-data (juxt :methodConfigurationNamespace :methodConfigurationName)
       :as-text (fn [[namespace name]] (str namespace "/" name))}
      {:header "Data Entity" :initial-width 220
       :column-data (comp (juxt :entityName :entityType) :submissionEntity)
       :as-text (fn [[entity-name entity-type]]
                  (str entity-name " (" entity-type ")"))}
      {:header "Submitted By" :initial-width 220 :column-data :submitter}
      {:header "Submission ID" :initial-width 235 :column-data :submissionId
       :render (fn [submission-id]
                 (style/create-link {:text submission-id
                                     :target "_blank"
                                     :style {:color "-webkit-link" :textDecoration "underline"}
                                     :href (str moncommon/google-cloud-context
                                                bucketName "/" submission-id "/")}))}]}}])


(react/defc SubmissionsList
  {:reload
   (fn [{:keys [state this]}]
     (swap! state dissoc :server-response)
     (react/call :load-submissions this))
   :render
   (fn [{:keys [props state]}]
     (let [server-response (:server-response @state)
           {:keys [submissions error-message]} server-response]
       (cond
         (nil? server-response)
         [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading analyses..."}]]
         error-message (style/create-server-error-message error-message)
         :else
         (render-submissions-table (:workspace-id props) submissions (:bucketName props)))))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-submissions this))
   :load-submissions
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
       (react/call :reload (@refs "submissions-list"))))
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
