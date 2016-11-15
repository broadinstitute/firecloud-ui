(ns org.broadinstitute.firecloud-ui.page.workspace.monitor.submission-details
    (:require
      [dmohs.react :as react]
      [clojure.string :as string]
      [org.broadinstitute.firecloud-ui.common :as common]
      [org.broadinstitute.firecloud-ui.common.components :as comps]
      [org.broadinstitute.firecloud-ui.common.icons :as icons]
      [org.broadinstitute.firecloud-ui.common.modal :as modal]
      [org.broadinstitute.firecloud-ui.common.style :as style]
      [org.broadinstitute.firecloud-ui.common.table :as table]
      [org.broadinstitute.firecloud-ui.page.workspace.monitor.common :as moncommon]
      [org.broadinstitute.firecloud-ui.page.workspace.monitor.workflow-details :as workflow-details]
      [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
      [org.broadinstitute.firecloud-ui.utils :as utils]))


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
   (fn [{:keys [this state]}]
     (if (:selected-workflow @state)
       (react/call :render-workflow-details this)
       (react/call :render-table this)))
   :render-table
   (fn [{:keys [props state]}]
     [table/Table
      {:empty-message "No Workflows"
       :columns [{:header "Data Entity" :starting-width 200
                  :as-text
                  (fn [workflow] (get-in workflow ["workflowEntity" "entityName"]))
                  :sort-by :text
                  :content-renderer
                  (fn [workflow]
                    (let [entity (workflow "workflowEntity")
                          id (workflow "workflowId")
                          name (str (entity "entityName") " (" (entity "entityType") ")")]
                      (if-not id
                        name
                        (style/create-link {:text name
                                            :onClick #(swap! state assoc :selected-workflow {:id id :name name})}))))}
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
                    (fn [workflow] (workflow "workflowId"))
                  :sort-by :text
                  :content-renderer
                  (fn [workflow]
                   (let [{:keys [submission-id bucketName]} props
                         inputs (second (second (first (workflow "inputResolutions"))))
                         input-names (string/split inputs ".")
                         workflow-name (first input-names)
                         workflowId (workflow "workflowId")]
                   (style/create-link {:text workflowId
                                       :target "_blank"
                                       :style {:color "-webkit-link" :textDecoration "underline"}
                     :href (str moncommon/google-cloud-context bucketName "/" submission-id  "/"
                                workflow-name "/" workflowId "/")})))}]
       :filter-groups
       (vec (cons {:text "All" :pred (constantly true)}
              (map (fn [status] {:text status :pred #(= status (% "status"))})
                moncommon/wf-all-statuses)))
       :data (:workflows props)
       :->row (fn [row]
                [row
                 (row "statusLastChangedDate")
                 (row "status")
                 (row "messages")
                 row])}])
   :render-workflow-details
   (fn [{:keys [state props]}]
     (let [workflows (:workflows props)
           workflowName (get-in workflows [0 "workflowEntity" "entityName"])]
     [:div {}
      [:div {}
       (style/create-link {:text "Workflows"
                           :onClick #(swap! state dissoc :selected-workflow)})
       (icons/icon {:style {:verticalAlign "middle" :margin "0 1ex 0 1ex"}} :angle-right)
       [:b {} (:name (:selected-workflow @state))]]
      [:div {:style {:marginTop "1em"}}
       (workflow-details/render
        (merge (select-keys props [:workspace-id :submission-id :bucketName])
               {:workflow-id (get-in @state [:selected-workflow :id])
                :submission (:submission props)
                :workflow-name workflowName}))]]))})


(react/defc AbortButton
  {:render (fn [{:keys [state this]}]
             (when (:aborting-submission? @state)
               [comps/Blocker {:banner "Aborting submission ..."}])
             [comps/SidebarButton {:color :button-primary :style :light :margin :top
                                   :text "Abort" :icon :status-warning-triangle
                                   :onClick (fn [_]
                                              (modal/push-confirm
                                               {:text "Are you sure you want to abort this submission?"
                                                :on-confirm #(react/call :abort-submission this)}))}])
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
                                      (modal/push-error-text (str "Error in aborting the job : " status-text))))}))})


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
           [comps/StatusLabel {:text (submission "status")
                               :color (color-for-submission submission)
                               :icon (icon-for-submission submission)}]
           (when (contains? moncommon/sub-running-statuses (submission "status"))
             [AbortButton
              {:on-abort (fn []
                          (swap! state assoc :server-response nil)
                          (react/call :load-details this))
               :workspace-id (:workspace-id props)
               :submission-id (submission "submissionId")}])]
          [:div {:style {:float "left"}}
           (style/create-section-header "Method Configuration")
           (style/create-paragraph
             [:div {}
              [:div {:style {:fontWeight 200 :display "inline-block" :width 90}} "Namespace:"]
              [:span {:style {:fontWeight 500}} (submission "methodConfigurationNamespace")]]
             [:div {}
              [:div {:style {:fontWeight 200 :display "inline-block" :width 90}} "Name:"]
              [:span {:style {:fontWeight 500}} (submission "methodConfigurationName")]])
           (style/create-section-header "Submission Entity")
           (style/create-paragraph
             [:div {}
              [:div {:style {:fontWeight 200 :display "inline-block" :width 90}} "Type:"]
              [:span {:style {:fontWeight 500}} (get-in submission ["submissionEntity" "entityType"])]]
             [:div {}
              [:div {:style {:fontWeight 200 :display "inline-block" :width 90}} "Name:"]
              [:span {:style {:fontWeight 500}} (get-in submission ["submissionEntity" "entityName"])]])]
          [:div {:style {:float "right"}}
           (style/create-section-header "Submitted by")
           (style/create-paragraph
             [:div {} (submission "submitter")]
             (let [m (js/moment (submission "submissionDate"))]
               [:div {} (.format m "LLL") " (" (.fromNow m) ")"]))
                (style/create-section-header "Submission ID")
                (style/create-link {:text (style/create-paragraph (submission "submissionId"))
                               :target "_blank"
                               :style {:color "-webkit-link" :textDecoration "underline"}
                               :href (str moncommon/google-cloud-context
                                  (:bucketName props) "/" (submission "submissionId") "/")})]
          (common/clear-both)
          [:h2 {:style {:paddingBottom "0.5em"}} "Workflows:"]
          [WorkflowsTable {:workflows (submission "workflows")
                           :workspace-id (:workspace-id props)
                           :submission submission
                           :bucketName (:bucketName props)
                           :submission-id (submission "submissionId")}]])))
   :load-details
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-submission (:workspace-id props) (:submission-id props))
        :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                   (swap! state assoc :server-response (if success?
                                                         {:submission (get-parsed-response false)}
                                                         {:error-message status-text})))}))
   :component-did-mount (fn [{:keys [this]}] (react/call :load-details this))})
