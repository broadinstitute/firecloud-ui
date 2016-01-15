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

(defn- render-submissions-table [submissions on-submission-clicked]
  [table/Table
   {:empty-message "There are no analyses to display."
    :columns
    [{:header "Date" :starting-width 200 :as-text render-date
      :sort-by #(% "submissionDate")
      :sort-initial :desc
      :content-renderer (fn [submission]
                          (style/create-link {:text (render-date submission)
                                              :onClick #(on-submission-clicked (submission "submissionId"))}))}
     {:header "Status" :as-text #(% "status") :sort-by :text
      :content-renderer (fn [submission]
                          [:div {}
                           (when (and (= "Done" (submission "status"))
                                      (not (moncommon/all-success? submission)))
                             (icons/font-icon {:style {:color (:exception-red style/colors)
                                                       :marginRight 8}} :status-warning))
                           (submission "status")])}
     {:header "Method Configuration" :starting-width 300
      :content-renderer (fn [[namespace name]]
                          [:div {} namespace "/" name])}
     {:header "Data Entity" :starting-width 220}
     {:header "Submitted By" :starting-width 220}]
    :data submissions
    :->row (fn [x]
             [x
              x
              [(x "methodConfigurationNamespace") (x "methodConfigurationName")]
              (str (get-in x ["submissionEntity" "entityName"])
                   " (" (get-in x ["submissionEntity" "entityType"]) ")")
              (x "submitter")])}])


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
         (render-submissions-table submissions (:on-submission-clicked props)))))
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
           selected-submission-id (not-empty (:segment nav-context))]
       [:div {:style {:padding "1em"}}
        (if selected-submission-id
          [submission-details/Page {:key selected-submission-id
                                    :workspace-id workspace-id
                                    :submission-id selected-submission-id}]
          [SubmissionsList {:ref "submissions-list"
                            :workspace-id workspace-id
                            :on-submission-clicked #(nav/navigate nav-context %)}])]))})
