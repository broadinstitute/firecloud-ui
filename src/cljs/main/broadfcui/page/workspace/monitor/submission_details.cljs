(ns broadfcui.page.workspace.monitor.submission-details
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.duration :as duration]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.page.workspace.monitor.common :as moncommon]
   [broadfcui.page.workspace.monitor.workflow-details :as workflow-details]
   [broadfcui.utils :as utils]
   ))


(defn- color-for-submission [submission]
  (cond (contains? moncommon/sub-running-statuses (:status submission)) (:running-state style/colors)
        (moncommon/all-success? submission) (:success-state style/colors)
        :else (:exception-state style/colors)))

(defn- icon-for-submission [submission]
  (cond (contains? moncommon/sub-running-statuses (:status submission)) [icons/RunningIcon {:size 36}]
        (moncommon/all-success? submission) [icons/CompleteIcon {:size 36}]
        :else [icons/ExceptionIcon {:size 36}]))

(react/defc- WorkflowsTable
  {:render
   (fn [{:keys [this props]}]
     (let [{:keys [workflow-id]} props]
       (if workflow-id
         (this :render-workflow-details workflow-id)
         (this :render-table))))
   :render-table
   (fn [{:keys [props]}]
     [Table
      {:ref "table"
       :data (:workflows props)
       :tabs {:items (->> moncommon/wf-all-statuses
                          (map (fn [status] {:label status :predicate #(= status (:status %))}))
                          (cons {:label "All"})
                          vec)}
       :body
       {:empty-message "No Workflows"
        :style table-style/table-heavy
        :behavior {:fixed-column-count 1}
        :columns [{:id "view" :initial-width 50
                   :hidden? true :resizable? false :sortable? false :filterable? false
                   :column-data :workflowId
                   :as-text (constantly "View workflow details")
                   :render (fn [id]
                             (when id
                               (links/create-internal
                                 {:href (nav/get-link :workspace-workflow
                                                      (:workspace-id props)
                                                      (:submission-id props)
                                                      id)}
                                 "View")))}
                  {:header "Data Entity" :initial-width 200
                   :column-data :workflowEntity
                   :as-text (fn [{:keys [entityName entityType]}]
                              (str entityName " (" entityType ")"))
                   :sort-by :text}
                  {:header "Last Changed" :initial-width 280
                   :column-data :statusLastChangedDate
                   :sort-initial :desc
                   :as-text moncommon/render-date}
                  {:header "Status" :initial-width 120
                   :column-data :status
                   :render (fn [status]
                             [:div {:data-test-id "workflow-status"}
                              (moncommon/icon-for-wf-status status)
                              status])}
                  {:header "Messages" :initial-width 300
                   :column-data :messages
                   :as-text (partial string/join "\n")
                   :render (fn [message-list]
                             [:div {} (map (fn [message] [:div {} message]) message-list)])}
                  {:header "Workflow ID" :initial-width 300
                   :as-text :workflowId :sort-by :text
                   :render
                   (fn [{:keys [workflowId inputResolutions]}]
                     (when workflowId
                       (let [{:keys [submission-id bucketName]} props
                             inputs (second (second (first inputResolutions)))
                             input-names (string/split inputs ".")
                             workflow-name (first input-names)]
                         (links/create-external
                          {:href (str moncommon/google-cloud-context bucketName "/" submission-id "/"
                                      workflow-name "/" workflowId "/")}
                          workflowId))))}]}}])
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


(react/defc- AbortButton
  {:render (fn [{:keys [state this]}]
             (when (:aborting-submission? @state)
               [comps/Blocker {:banner "Aborting submission..."}])
             [buttons/SidebarButton
              {:data-test-id "submission-abort-button"
               :color :exception-state :style :light :margin :top
               :text "Abort" :icon :warning
               :onClick (fn [_]
                          (comps/push-confirm
                           {:text "Are you sure you want to abort this submission?"
                            :on-confirm
                            [buttons/Button {:data-test-id "submission-abort-modal-confirm-button"
                                             :text "Abort Submission"
                                             :onClick #(this :abort-submission)}]}))}])
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
           (links/create-external {:data-test-id "submission-id"
                                   :href (str moncommon/google-cloud-context
                                              (:bucketName props) "/" (:submissionId submission) "/")}
                                  (style/create-paragraph (:submissionId submission)))]
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
