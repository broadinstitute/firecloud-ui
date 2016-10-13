(ns org.broadinstitute.firecloud-ui.page.workspace.monitor.tab
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.nav :as nav]
    [org.broadinstitute.firecloud-ui.page.workspace.monitor.common :as moncommon]
    [org.broadinstitute.firecloud-ui.page.workspace.monitor.submission-details :as submission-details]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

(defn- render-date [submission]
  (common/format-date (submission "submissionDate")))

(defn- render-submissions-table [submissions nav-context bucketName]
  [table/Table
   {:empty-message "There are no analyses to display."
    :columns
    [{:header "Date" :starting-width 200 :as-text render-date
      :sort-by #(% "submissionDate")
      :sort-initial :desc
      :content-renderer (fn [submission]
                          (style/create-link {:text (render-date submission)
                                              :href (nav/create-href nav-context (submission "submissionId"))}))}
     {:header "Status" :as-text #(% "status") :sort-by :text
      :content-renderer (fn [submission]
                          [:div {}
                            (when (= "Done" (submission "status"))
                              (moncommon/icon-for-sub-status (get-in submission ["workflowStatuses"])))
                            (submission "status")])}
     {:header "Method Configuration" :starting-width 300
      :content-renderer (fn [[namespace name]]
                          [:div {} namespace "/" name])}
     {:header "Data Entity" :starting-width 220}
     {:header "Submitted By" :starting-width 220}
     {:header "Submission ID" :starting-width 235 
     :content-renderer (fn [submissionId]
                          (style/create-link {:text submissionId
                                              :href (str moncommon/google-cloud-context bucketName "/" submissionId "/")}))}]
    :data submissions
    :->row (fn [x]
             [x
              x
              [(x "methodConfigurationNamespace") (x "methodConfigurationName")]
              (str (get-in x ["submissionEntity" "entityName"])
                   " (" (get-in x ["submissionEntity" "entityType"]) ")")
              (x "submitter")
              (x "submissionId")])}])


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
         (render-submissions-table submissions (:nav-context props)(:bucketName props)))))
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
           workspace (:workspace props)
           selected-submission-id (not-empty (:segment nav-context))]
       (if selected-submission-id
         (nav/back nav-context)
         (react/call :reload (@refs "submissions-list")))))
   :render
   (fn [{:keys [props]}]
     (let [workspace-id (:workspace-id props)
           workspace (:workspace props)
           nav-context (nav/parse-segment (:nav-context props))
           bucketName (get-in workspace [:workspace :bucketName])
           selected-submission-id (not-empty (:segment nav-context))]
       [:div {:style {:padding "1em"}}
        (if selected-submission-id
          [submission-details/Page {:key selected-submission-id
                                    :workspace-id workspace-id
                                    :bucketName bucketName
                                    :submission-id selected-submission-id}]
          [SubmissionsList {:ref "submissions-list"
                            :workspace-id workspace-id
                            :bucketName bucketName
                            :nav-context nav-context}])]))})
