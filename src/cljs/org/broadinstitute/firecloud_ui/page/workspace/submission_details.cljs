(ns org.broadinstitute.firecloud-ui.page.workspace.submission-details
  (:require
    [dmohs.react :as react]
    cljsjs.moment
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.paths :as paths]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

(defn- create-mock-submission-details [submission-id]
  {"submissionId" submission-id
   "submissionDate" (utils/rand-recent-time)
   "submitter" "test@test.gov"
   "methodConfigurationNamespace" "broad-dsde-dev"
   "methodConfigurationName" "some method conf"
   "submissionEntity" {"entityType" (rand-nth ["sample" "participant"])
                       "entityName" "foo"}
   "workflows" (map (fn [i]
                      {"messages" []
                       "workspaceName" "foo"
                       "statusLastChangedDate" (utils/rand-recent-time)
                       "workflowEntity" {"entityType" "sample"
                                         "entityName" (str "sample_" i)}
                       "status" (rand-nth ["Succeeded" "Submitted" "Running" "Failed" "Aborted" "Unknown"])
                       "workflowId" "97adf170-ee40-40a5-9539-76b72802e124"})
                 (range (rand-int 10)))
   "notstarted" (map (fn [i]
                       {"entityType" (rand-nth ["Sample" "Participant"])
                        "entityName" (str "entity " i)
                        "errors" (utils/rand-subset ["Prerequisites not met" "Server error"
                                                     "I didn't feel like it" "Syntax error"])})
                  (range (rand-int 5)))
   "status" (rand-nth ["Submitted" "Done"])})

(defn- all-success? [submission]
  (every? #(= "Succeeded" (% "status")) (submission "workflows")))

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


(react/defc WorkflowsTable
  {:render
   (fn [{:keys [props]}]
     [table/Table
      {:empty-message "No Workflows"
       :columns [{:header "Data Entity" :starting-width 200 :sort-by :value
                  :filter-by (fn [entity]
                               (str (entity "entityType") " " (entity "entityName")))}
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
               (:workflows props))}])})


(react/defc WorkflowFailuresTable
  {:render
   (fn [{:keys [props]}]
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
                                              [:div {} error])
                                         error-list)])}]
       :data (map (fn [row]
                    [{:type (row "entityType") :name (row "entityName")}
                     (row "errors")])
               (:workflows props))}])})


(react/defc Page
  {:render
   (fn [{:keys [state]}]
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
             [:div {:style {:padding "0.7em 0" :marginTop "0.5em" :color "#fff" :cursor "pointer"
                            :backgroundColor (:exception-red style/colors) :borderRadius 5}
                    :onClick #(js/console.log "Insert abort logic here")}
              (icons/font-icon {:style {:fontSize "160%" :marginRight 14 :verticalAlign "middle"}}
                :status-warning-triangle)
              [:span {:style {:fontSize "125%" :verticalAlign "middle"}} "Abort"]])]
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
   :component-did-mount
   (fn [{:keys [props state]}]
     (let [url (paths/submission-details (:workspace-id props) (:submission-id props))
           on-done (fn [{:keys [success? status-text get-parsed-response]}]
                     (swap! state assoc :server-response (if success?
                                                           {:submission (get-parsed-response)}
                                                           {:error-message status-text})))
           canned-response {:responseText (utils/->json-string
                                            (create-mock-submission-details (:submission-id props)))
                            :status 200
                            :delay-ms (rand-int 2000)}]
       (utils/ajax-orch url {:on-done on-done :canned-response canned-response})))})
