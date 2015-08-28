(ns org.broadinstitute.firecloud-ui.page.workspace.monitor-tab
  (:require
    [dmohs.react :as react]
    cljsjs.moment
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.page.workspace.submission-details :as submission-details]
    [org.broadinstitute.firecloud-ui.paths :as paths]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- create-mock-submissions-list [workspace-id]
  (map
    (fn [i]
      {:workspaceName workspace-id
       :methodConfigurationNamespace "my_test_configs"
       :submissionDate (utils/rand-recent-time)
       :submissionId "46bfd579-b1d7-4f92-aab0-e44dd092b52a"
       :notstarted []
       :workflows [{:messages []
                    :workspaceName workspace-id
                    :statusLastChangedDate (utils/rand-recent-time)
                    :workflowEntity {:entityType "sample"
                                     :entityName "sample_01"}
                    :status "Succeeded"
                    :workflowId "97adf170-ee40-40a5-9539-76b72802e124"}]
       :methodConfigurationName (str "test_config" (inc i))
       :status (rand-nth ["Submitted" "Done"])
       :submissionEntity {:entityType "sample"
                          :entityName (str "sample_" (inc i))}
       :submitter "abaumann@broadinstitute.org"})
    (range (rand-int 50))))


(defn- render-submissions-table [submissions on-submission-clicked]
  [table/Table
   {:columns
    [{:header "Date" :starting-width 200
      :sort-by #(% "submissionDate") :filter-by #(% "submissionDate")
      :content-renderer (fn [row-index submission]
                          [:a {:href "javascript:;"
                               :style {:color (:button-blue style/colors) :textDecoration "none"}
                               :onClick #(on-submission-clicked (submission "submissionId"))}
                           (.format (js/moment (submission "submissionDate")) "LLL")])}
     {:header "Status" :sort-by :value}
     {:header "Method Configuration" :starting-width 220 :sort-by :value}
     {:header "Data Entity" :starting-width 220 :sort-by :value}]
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
         (zero? (count submissions))
         (style/create-message-well "There are no analyses to display.")
         :else
         [:div {:style {:margin "0 -2em"}}
          (render-submissions-table submissions (:on-submission-clicked props))])))
   :component-did-mount
    (fn [{:keys [this]}]
      (react/call :load-submissions this))
   :load-submissions
   (fn [{:keys [props state]}]
     (let [url (paths/submissions-list (:workspace-id props))
           on-done (fn [{:keys [success? status-text get-parsed-response]}]
                     (swap! state assoc :server-response (if success?
                                                           {:submissions (get-parsed-response)}
                                                           {:error-message status-text})))
           canned-response {:responseText (utils/->json-string
                                           (create-mock-submissions-list (:workspace-id props)))
                            :status 200
                            :delay-ms (rand-int 2000)}]
       (utils/ajax-orch url {:on-done on-done :canned-response canned-response})))})


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
