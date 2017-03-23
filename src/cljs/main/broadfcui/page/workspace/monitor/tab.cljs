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
    [broadfcui.utils :as utils]
    ))

(defn- render-date [submission]
  (common/format-date (:submissionDate submission)))

(defn- render-submissions-table [submissions nav-context bucketName]
  [Table
   {:table
    {:state-key "monitor"
     :style table-style/table-heavy
     :data-source (table-utils/local submissions)
     :empty-message "There are no analyses to display."
     :columns
     [{:header "Date" :initial-width 200 :as-text render-date :reorderable? false
       :sort-by :submissionDate :sort-initial :desc
       :render (fn [submission]
                 (style/create-link {:text (render-date submission)
                                     :href (nav/create-href nav-context (:submissionId submission))}))}
      {:header "Status" :as-text :status
       :render (fn [submission]
                 [:div {:style {:height table-style/table-icon-size}}
                  (when (= "Done" (:status submission))
                    (moncommon/icon-for-sub-status (:workflowStatuses submission)))
                  (:status submission)])}
      {:header "Method Configuration" :initial-width 300
       :column-data (juxt :methodConfigurationNamespace :methodConfigurationName)
       :as-text (fn [[namespace name]] (str namespace "/" name))}
      {:header "Data Entity" :initial-width 220
       :column-data #(str (get-in % [:submissionEntity :entityName])
                          " (" (get-in % [:submissionEntity :entityType]) ")")}
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
         (render-submissions-table submissions (:nav-context props) (:bucketName props)))))
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
     (let [nav-context (nav/parse-segment (:nav-context props))
           selected-submission-id (not-empty (:segment nav-context))]
       (if selected-submission-id
         (nav/back nav-context)
         (react/call :reload (@refs "submissions-list")))))
   :render
   (fn [{:keys [props]}]
     (let [workspace-id (:workspace-id props)
           nav-context (nav/parse-segment (:nav-context props))
           bucketName (get-in (:workspace props) [:workspace :bucketName])
           selected-submission-id (not-empty (:segment nav-context))]
       [:div {:style {:padding "1rem 1.5rem"}}
        (if selected-submission-id
          [submission-details/Page {:key selected-submission-id
                                    :workspace-id workspace-id
                                    :bucketName bucketName
                                    :submission-id selected-submission-id
                                    :nav-context nav-context}]
          [SubmissionsList {:ref "submissions-list"
                            :workspace-id workspace-id
                            :bucketName bucketName
                            :nav-context nav-context}])]))})
