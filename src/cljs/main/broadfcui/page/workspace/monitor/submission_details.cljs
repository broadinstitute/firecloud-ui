(ns broadfcui.page.workspace.monitor.submission-details
    (:require
      [dmohs.react :as react]
      [clojure.string :as string]
      [broadfcui.common :as common]
      [broadfcui.common.components :as comps]
      [broadfcui.common.duration :as duration]
      [broadfcui.common.icons :as icons]
      [broadfcui.common.modal :as modal]
      [broadfcui.common.style :as style]
      [broadfcui.common.table :refer [Table]]
      [broadfcui.nav :as nav]
      [broadfcui.page.workspace.monitor.common :as moncommon]
      [broadfcui.page.workspace.monitor.workflow-details :as workflow-details]
      [broadfcui.endpoints :as endpoints]
      [broadfcui.utils :as utils]))


(defn- color-for-submission [submission]
  (cond (contains? moncommon/sub-running-statuses (submission "status")) (:running-state style/colors)
        (moncommon/all-success? submission) (:success-state style/colors)
        :else (:exception-state style/colors)))

(defn- icon-for-submission [submission]
  (cond (contains? moncommon/sub-running-statuses (submission "status")) [icons/RunningIcon {:size 36}]
        (moncommon/all-success? submission) [icons/CompleteIcon {:size 36}]
        :else [icons/ExceptionIcon {:size 36}]))


(react/defc WorkflowsTable
  {:get-initial-state
   (fn []
     {:active-filter :all})
   :render
   (fn [{:keys [this props]}]
     (let [{:keys [workflow-id]} props]
       (if workflow-id
         (this :render-workflow-details workflow-id)
         (this :render-table))))
   :render-table
   (fn [{:keys [props]}]
     [Table
      {:empty-message "No Workflows"
       :columns [{:starting-width 50
                  :as-text (constantly "View workflow details")
                  :content-renderer
                  (fn [id]
                    (when id
                      (style/create-link
                       {:text "View"
                        :href (nav/get-link :workspace-workflow
                                            (:workspace-id props)
                                            (:submission-id props)
                                            id)})))}
                 {:header "Data Entity" :starting-width 200
                  :as-text
                  (fn [entity]
                    (str (:entityName entity) " (" (:entityType entity) ")"))
                  :sort-by :text}
                  {:header "Last Changed" :starting-width 280 :as-text moncommon/render-date}
                  {:header "Status" :starting-width 120
                   :content-renderer (fn [status]
                                       [:div {}
                                        (moncommon/icon-for-wf-status status)
                                        status])}
                  {:header "Messages" :starting-width 300
                   :content-renderer (fn [message-list]
                                       [:div {}
                                        (map (fn [message]
                                               [:div {} message])
                                             message-list)])}
                  {:header "Workflow ID" :starting-width 300
                   :as-text
                   (fn [workflow] (:workflowId workflow))
                   :sort-by :text
                   :content-renderer
                   (fn [workflow]
                     (let [{:keys [submission-id bucketName]} props
                           inputs (second (second (first (:inputResolutions workflow))))
                           input-names (string/split inputs ".")
                           workflow-name (first input-names)
                           workflowId (:workflowId workflow)]
                       (style/create-link {:text workflowId
                                           :target "_blank"
                                           :style {:color "-webkit-link" :textDecoration "underline"}
                                           :href (str moncommon/google-cloud-context bucketName "/" submission-id "/"
                                                      workflow-name "/" workflowId "/")})))}]
       :filter-groups
       (vec (cons {:text "All" :pred (constantly true)}
                  (map (fn [status] {:text status :pred #(= status (:status %))})
                       moncommon/wf-all-statuses)))
       :data (:workflows props)
       :->row (fn [row]
                [(:id row)
                 (:workflowEntity row)
                 (:statusLastChangedDate row)
                 (:status row)
                 (:messages row)
                 row])}])
   :render-workflow-details
   (fn [{:keys [props]} workflowId]
     (let [workflows (:workflows props)
           workflowName (->> workflows (filterv #(= (:workflowId %) workflowId))
                             first :workflowEntity :entityName)]
       [:div {}
        [:div {:style {:marginBottom "1rem" :fontSize "1.1rem"}}
         [comps/Breadcrumbs {:crumbs
                             [{:text "Workflows"
                               :href (nav/get-link :workspace-submission
                                                   (:workspace-id props)
                                                   (:submission-id props))}
                              {:text workflowName}]}]]
        (workflow-details/render
         (merge (select-keys props [:workspace-id :submission-id :bucketName])
                {:workflow-id workflowId
                 :submission (:submission props)
                 :workflow-name workflowName}))]))})


(react/defc AbortButton
  {:render (fn [{:keys [state this]}]
             (when (:aborting-submission? @state)
               [comps/Blocker {:banner "Aborting submission..."}])
             [comps/SidebarButton {:color :exception-state :style :light :margin :top
                                   :text "Abort" :icon :warning
                                   :onClick (fn [_]
                                              (comps/push-confirm
                                               {:text "Are you sure you want to abort this submission?"
                                                :on-confirm #(this :abort-submission)}))}])
   :abort-submission (fn [{:keys [props state]}]
                       (modal/pop-modal)
                       (swap! state assoc :aborting-submission? true)
                       (endpoints/call-ajax-orch
                        {:endpoint (endpoints/abort-submission (:workspace-id props) (:submission-id props))
                         :headers utils/content-type=json
                         :on-done (fn [{:keys [success? status-text]}]
                                    (swap! state dissoc :aborting-submission?)
                                    (if success?
                                      ((:on-abort props))
                                      (comps/push-error
                                       (str "Error in aborting the job : " status-text))))}))})


(react/defc Page
  {:render
   (fn [{:keys [state props this]}]
     (let [server-response (:server-response @state)
           {:keys [submission error-message]} server-response]
       (cond
         (nil? server-response)
         [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading analysis details..."}]]
         error-message (style/create-server-error-message error-message)
         :else
         [:div {}
          [:div {:style {:float "left" :width 290 :marginRight 40}}
           [comps/StatusLabel {:text (:status submission)
                               :color (color-for-submission submission)
                               :icon (icon-for-submission submission)}]
           (when (contains? moncommon/sub-running-statuses (:status submission))
             [AbortButton
              {:on-abort (fn []
                           (swap! state assoc :server-response nil)
                           (react/call :load-details this))
               :workspace-id (:workspace-id props)
               :submission-id (:submissionId submission)}])]
          [:div {:style {:float "left"}}
           (style/create-section-header "Method Configuration")
           (style/create-paragraph
            [:div {}
             [:div {:style {:fontWeight 200 :display "inline-block" :width 90}} "Namespace:"]
             [:span {:style {:fontWeight 500}} (:methodConfigurationNamespace submission)]]
            [:div {}
             [:div {:style {:fontWeight 200 :display "inline-block" :width 90}} "Name:"]
             [:span {:style {:fontWeight 500}} (:methodConfigurationName submission)]])
           (style/create-section-header "Submission Entity")
           (style/create-paragraph
            [:div {}
             [:div {:style {:fontWeight 200 :display "inline-block" :width 90}} "Type:"]
             [:span {:style {:fontWeight 500}} (get-in submission [:submissionEntity :entityType])]]
            [:div {}
             [:div {:style {:fontWeight 200 :display "inline-block" :width 90}} "Name:"]
             [:span {:style {:fontWeight 500}} (get-in submission [:submissionEntity :entityName])]])]
          [:div {:style {:float "right"}}
           (style/create-section-header "Submitted by")
           (style/create-paragraph
            [:div {} (:submitter submission)]
            [:div {} (common/format-date (:submissionDate submission)) " ("
             (duration/fuzzy-time-from-now-ms (.parse js/Date (:submissionDate submission)) true) ")"])
           (style/create-section-header "Submission ID")
           (style/create-link {:text (style/create-paragraph (:submissionId submission))
                               :target "_blank"
                               :style {:color "-webkit-link" :textDecoration "underline"}
                               :href (str moncommon/google-cloud-context
                                          (:bucketName props) "/" (:submissionId submission) "/")})]
          (common/clear-both)
          [:h2 {} "Workflows:"]
          [WorkflowsTable {:workflows (:workflows submission)
                           :workspace-id (:workspace-id props)
                           :submission submission
                           :bucketName (:bucketName props)
                           :submission-id (:submissionId submission)
                           :workflow-id (:workflow-id props)}]])))
   :load-details
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-submission (:workspace-id props) (:submission-id props))
       :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                  (swap! state assoc :server-response (if success?
                                                        {:submission (get-parsed-response)}
                                                        {:error-message status-text})))}))
   :component-did-mount (fn [{:keys [this]}] (react/call :load-details this))})
