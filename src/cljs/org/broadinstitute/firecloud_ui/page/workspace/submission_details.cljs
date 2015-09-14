(ns org.broadinstitute.firecloud-ui.page.workspace.submission-details
  (:require
    [dmohs.react :as react]
    cljsjs.moment
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
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

(defn- icon-for-wf-status [status]
  (cond (= "Succeeded" status)
        (icons/font-icon {:style {:color (:success-green style/colors)
                                  :fontSize 12 :marginRight 4}}
          :status-done)
        (or (= "Running" status) (= "Submitted" status))
        [:span {:style {:backgroundColor (:running-blue style/colors) :position "relative"
                        :width 16 :height 16 :display "inline-block" :borderRadius 3
                        :verticalAlign "middle" :marginTop -4 :marginRight 4}}
         (style/center {} [comps/RunningIcon {:size 12}])]
        :else
        [:span {:style {:backgroundColor (:exception-red style/colors) :position "relative"
                        :width 16 :height 16 :display "inline-block" :borderRadius 3
                        :verticalAlign "middle" :marginTop -4 :marginRight 4}}
         (style/center {} [comps/ExceptionIcon {:size 12}])]))

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
        :columns [{:header "Data Entity" :starting-width 200 :sort-by :value}
                  {:header "Last Changed" :starting-width 280 :sort-by :value
                   :content-renderer (fn [row-index date]
                                       (let [m (js/moment date)]
                                         (str (.format m "L [at] LTS") " ("
                                           (.fromNow m) ")")))}
                  {:header "Status" :starting-width 120 :sort-by :value
                   :content-renderer (fn [row-index status]
                                       [:div {}
                                        (icon-for-wf-status status)
                                        status])}
                  {:header "Messages" :starting-width 300 :sort-by count
                   :content-renderer (fn [row-index message-list]
                                       [:div {}
                                        (map (fn [message]
                                               [:div {} message])
                                          message-list)])}
                  {:header "Workflow ID" :starting-width 300 :sort-by :value}]
        :data (map (fn [row]
                     [(str (get-in row ["workflowEntity" "entityName"]) " ("
                        (get-in row ["workflowEntity" "entityType"]) ")")
                      (row "statusLastChangedDate")
                      (row "status")
                      (row "messages")
                      (row "workflowId")])
                (filter-workflows (:active-filter @state) (:workflows props)))}]])})


(react/defc WorkflowFailuresTable
  {:render
   (fn [{:keys [props state]}]
     [:div {}
     (when (:tempx @state)
       [comps/Blocker
        {
         :banner "TESTING"
         }
        ])
     [table/Table
      {:empty-message "No Workflows"
       :columns [{:header "Data Entity" :starting-width 200 :sort-by :value
                  :filter-by (fn [entity]
                               (str (:type entity) " " (:name entity)))
                  :content-renderer (fn [i entity]
                                      (str (:name entity) " (" (:type entity) ")"))}
                 {:header "Errors" :starting-width 500 :sort-by count
                  :content-renderer (fn [i error-list]
                                      [:div {}
                                       (map (fn [error]
                                              [:div {}
                                               [:a {:href "javascript:;"
                                                    :onClick (fn []
                                                               (utils/rlog "in onclick for href...")
                                                               (swap! state assoc :tempx true)
                                                               )
                                                    }
                                               error]
                                               ])
                                         error-list)])}]
       :data (map (fn [row]
                    [{:type (row "entityType") :name (row "entityName")}
                     (row "errors")])
               (:workflows props))}]]

     )})


(react/defc AbortButton
  {:render (fn [{:keys [state this]}]
             (if (:aborting-submission? @state)
               [comps/Blocker {:banner "Aborting submission ..."}]
               [:div {:style {:padding "0.7em 0" :marginTop "0.5em" :color "#fff" :cursor "pointer"
                              :backgroundColor (:exception-red style/colors) :borderRadius 5}
                      :onClick #(when (js/confirm "Are you sure?")
                                 (react/call :abort-submission this))}
                (icons/font-icon {:style {:fontSize "160%" :marginRight 14 :verticalAlign "middle"}}
                  :status-warning-triangle)
                [:span {:style {:fontSize "125%" :verticalAlign "middle"}} "Abort"]]))
   :abort-submission (fn [{:keys [props state]}]
                       (swap! state assoc :aborting-submission? true)
                       (endpoints/call-ajax-orch
                         {:endpoint (endpoints/abort-submission (:workspace-id props) (:submission-id props))
                          :headers {"Content-Type" "application/json"}
                          :on-done (fn [{:keys [success? status-text]}]
                                     (swap! state assoc :aborting-submission? false)
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
          [:div {:style {:float "left" :width 290 :marginRight 40 :textAlign "center"}}
           [:div {:style {:background (color-for-submission submission) :color "#fff"
                          :padding 20 :borderRadius 5}}
            (icon-for-submission submission)
            [:span {:style {:marginLeft "1.5ex" :fontSize "125%" :fontWeight 400
                            :verticalAlign "middle"}}
             (submission "status")]]
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
          [:div {:style {:clear "both" :paddingBottom "0.5em"}} "Workflows:"]
          [WorkflowsTable {:workflows (submission "workflows")}]
          [:div {:style {:padding "3em 0 0.5em 0"}} "Failed to Start:"]
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
