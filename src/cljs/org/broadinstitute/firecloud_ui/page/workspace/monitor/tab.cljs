(ns org.broadinstitute.firecloud-ui.page.workspace.monitor.tab
  (:require
    [dmohs.react :as react]
    cljsjs.moment
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.workspace.monitor.common :as moncommon]
    [org.broadinstitute.firecloud-ui.page.workspace.monitor.submission-details
     :as submission-details]
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
                          (style/create-link
                            #(on-submission-clicked (submission "submissionId"))
                            (render-date submission)))}
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
  {:render
   (fn [{:keys [props state]}]
     (let [server-response (:server-response @state)
           {:keys [submissions error-message]} server-response]
       (cond
         (nil? server-response)
         [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading analyses..."}]]
         error-message (style/create-server-error-message error-message)
         :else
         [:div {:style {:margin "0 -2em"}}
          (render-submissions-table submissions (:on-submission-clicked props))])))
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
  {:get-initial-state
   (fn [{:keys [props]}]
     {:selected-submission-id (:initial-submission-id props)})
   :render
   (fn [{:keys [props state]}]
     [:div {:style {:margin "2em"}}
      (if-let [sid (:selected-submission-id @state)]
        [submission-details/Page {:workspace-id (:workspace-id props) :submission-id sid}]
        [SubmissionsList {:workspace-id (:workspace-id props)
                          :on-submission-clicked #(swap! state assoc :selected-submission-id %)}])])
   :component-will-receive-props
   (fn [{:keys [state]}]
     (swap! state dissoc :selected-submission-id))})


(defn render [workspace-id & [submission-id]]
  [Page {:workspace-id workspace-id :initial-submission-id submission-id}])
