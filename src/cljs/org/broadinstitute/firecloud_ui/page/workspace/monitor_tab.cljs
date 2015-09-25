(ns org.broadinstitute.firecloud-ui.page.workspace.monitor-tab
  (:require
    [dmohs.react :as react]
    cljsjs.moment
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.workspace.submission-details :as submission-details]
    ))


(defn- render-submissions-table [submissions on-submission-clicked]
  [table/Table
   {:empty-message "There are no analyses to display."
    :columns
    [{:header "Date" :starting-width 200
      :sort-by #(% "submissionDate") :filter-by #(% "submissionDate")
      :content-renderer (fn [submission]
                          (style/create-link
                            #(on-submission-clicked (submission "submissionId"))
                            (.format (js/moment (submission "submissionDate")) "LLL")))}
     {:header "Status"}
     {:header "Method Configuration" :starting-width 220}
     {:header "Data Entity" :starting-width 220}]
    :data (map (fn [x]
                 [x
                  (x "status")
                  (str (x "methodConfigurationNamespace") ":" (x "methodConfigurationName"))
                  (str (get-in x ["submissionEntity" "entityName"])
                       " (" (get-in x ["submissionEntity" "entityType"]) ")")])
               submissions)}])


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
