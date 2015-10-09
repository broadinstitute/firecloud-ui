(ns org.broadinstitute.firecloud-ui.page.workspace.monitor.submission-details
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.page.workspace.monitor.common :as moncommon]
    [org.broadinstitute.firecloud-ui.page.workspace.monitor.workflow-details :as workflow-details]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    ))


(defn- all-success? [submission]
  (and (every? #(= "Succeeded" (% "status")) (submission "workflows"))
       (zero? (count (submission "notstarted")))))

(defn- color-for-submission [submission]
  (cond (= "Submitted" (submission "status")) (:running-blue style/colors)
        (all-success? submission) (:success-green style/colors)
        :else (:exception-red style/colors)))

(defn- icon-for-submission [submission]
  (cond (= "Submitted" (submission "status")) [comps/RunningIcon {:size 36}]
        (all-success? submission) [comps/CompleteIcon {:size 36}]
        :else [comps/ExceptionIcon {:size 36}]))


(defn- filter-workflows [f wfs]
  (filter (fn [wf]
            (case f
              :all true
              :succeeded (= "Succeeded" (wf "status"))
              :running (contains? #{"Running" "Submitted"} (wf "status"))
              (contains? #{"Failed" "Aborted" "Unknown"} (wf "status"))))
    wfs))


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
     [:div {}
      (let [make-button (fn [name f]
                          {:text (str name " (" (count (filter-workflows f (:workflows props))) ")")
                           :active? (= f (:active-filter @state))
                           :onClick #(swap! state assoc :active-filter f)})]
        [:div {:style {:marginBottom "1em" :textAlign "center"}}
         [comps/FilterButtons {:buttons [(make-button "All" :all)
                                         (make-button "Succeeded" :succeeded)
                                         (make-button "Running" :running)
                                         (make-button "Failed" :failed)]}]])
      [table/Table
       {:key (:active-filter @state)
        :empty-message "No Workflows"
        :columns [{:header "Data Entity" :starting-width 200
                   :content-renderer
                   (fn [x]
                     (let [e (x "workflowEntity")
                           n (str (e "entityName") " (" (e "entityType") ")")]
                       (style/create-link
                        #(swap! state assoc :selected-workflow {:id (x "workflowId") :name n})
                        n)))}
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
                  {:header "Workflow ID" :starting-width 300}]
        :data (map (fn [row]
                     [row
                      (row "statusLastChangedDate")
                      (row "status")
                      (row "messages")
                      (row "workflowId")])
                   (filter-workflows (:active-filter @state) (:workflows props)))}]])
   :render-workflow-details
   (fn [{:keys [state]}]
     [:div {}
      [:div {}
       (style/create-link
        #(swap! state dissoc :selected-workflow)
        "Workflows")
       (icons/font-icon {:style {:verticalAlign "middle" :margin "0 1ex 0 1ex"}} :angle-right)
       [:b {} (:name (:selected-workflow @state))]]
      [:div {:style {:marginTop "1em"}}
       (workflow-details/render
        (merge (select-keys props [:workspace-id :submission-id])
               {:workflow-id (get-in @state [:selected-workflow :id])}))]])})


(react/defc WorkflowFailuresTable
  {:render
   (fn [{:keys [props]}]
     [table/Table
      {:empty-message "No Workflows"
       :columns [{:header "Data Entity" :starting-width 200 :sort-by (juxt :type :name)
                  :filter-by (fn [entity]
                               (str (:type entity) " " (:name entity)))
                  :content-renderer (fn [entity]
                                      (str (:name entity) " (" (:type entity) ")"))}
                 {:header "Errors" :starting-width 500 :sort-by count
                  :content-renderer (fn [error-list]
                                      [:div {}
                                       (map (fn [error]
                                              [:div {} error])
                                         error-list)])}]
       :data (map (fn [row]
                    [{:type (row "entityType") :name (row "entityName")}
                     (row "errors")])
               (:workflows props))}])})


(react/defc AbortButton
  {:render (fn [{:keys [state this]}]
             (when (:aborting-submission? @state)
               [comps/Blocker {:banner "Aborting submission ..."}])
             [comps/SidebarButton {:color :exception-red :margin :top
                                   :text "Abort" :icon :status-warning-triangle
                                   :onClick #(when (js/confirm "Are you sure?")
                                              (react/call :abort-submission this))}])
   :abort-submission (fn [{:keys [props state]}]
                       (swap! state assoc :aborting-submission? true)
                       (endpoints/call-ajax-orch
                         {:endpoint (endpoints/abort-submission (:workspace-id props) (:submission-id props))
                          :headers {"Content-Type" "application/json"}
                          :on-done (fn [{:keys [success? status-text]}]
                                     (swap! state dissoc :aborting-submission?)
                                     (if success?
                                       ((:on-abort props))
                                       (js/alert (str "Error in aborting the job : " status-text))))}))})


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
           (when (= "Submitted" (submission "status"))
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
           (style/create-paragraph (submission "submissionId"))]
          (common/clear-both)
          [:h2 {:style {:paddingBottom "0.5em"}} "Workflows:"]
          [WorkflowsTable {:workflows (submission "workflows")
                           :workspace-id (:workspace-id props)
                           :submission-id (submission "submissionId")}]
          [:h2 {:style {:padding "3em 0 0.5em 0"}} "Failed to Start:"]
          [WorkflowFailuresTable {:workflows (submission "notstarted")}]])))
   :load-details
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-submission (:workspace-id props) (:submission-id props))
        :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                   (swap! state assoc :server-response (if success?
                                                         {:submission (get-parsed-response)}
                                                         {:error-message status-text})))}))
   :component-did-mount (fn [{:keys [this]}] (react/call :load-details this))})
