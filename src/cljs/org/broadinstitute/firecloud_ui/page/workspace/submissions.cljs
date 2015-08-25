(ns org.broadinstitute.firecloud-ui.page.workspace.submissions
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.paths :as paths]
    [org.broadinstitute.firecloud-ui.utils :as utils]))


(defn- create-mock-submissions-list [workspace]
  (map
    (fn [i]
      {:workspaceName {:namespace (:namespace workspace)
                       :name (:name workspace)}
       :methodConfigurationNamespace "my_test_configs"
       :submissionDate (str "2015-" (rand-nth (range 1 12)) "-"
                         (rand-nth (range 1 30)) "T19:08:53.027Z")
       :submissionId "46bfd579-b1d7-4f92-aab0-e44dd092b52a"
       :notstarted []
       :workflows [{:messages []
                    :workspaceName {:namespace (:namespace workspace)
                                    :name (:name workspace)}
                    :statusLastChangedDate (str "2015-" (rand-nth (range 1 12))
                                             "-" (rand-nth (range 1 30))
                                             "T19:08:53.027Z")
                    :workflowEntity {:entityType "sample"
                                     :entityName "sample_01"}
                    :status "Succeeded"
                    :workflowId "97adf170-ee40-40a5-9539-76b72802e124"}]
       :methodConfigurationName (str "test_config" (inc i))
       :status (rand-nth ["Running" "Failed" "Completed" "Queued"])
       :submissionEntity {:entityType "sample"
                          :entityName (str "sample_" (inc i))}
       :submitter "abaumann@broadinstitute.org"})
    (range (rand-int 50))))


(defn- render-running-loaded-single-submission-summary [submission]
  [:div {:style {:border "1px solid black" :marginTop "1em" :position "relative"}}
   [:div {:style {:float "left" :marginLeft "1em" :padding "0.25em 0"}}
    [:div {:style {:fontWeight 500 :fontSize "120%" :paddingBottom "0.25em"}}
     (submission "methodConfigurationName ")]
    [:div {} (str "Status: " (submission "status")
                  " | Started At: " (submission "submissionDate"))
                  " | By " (submission "submitter")]
    (let [submissionEntity (submission "submissionEntity")
          entityName (submissionEntity "entityName")
          entityType (submissionEntity "entityType")]
      [:div {} (str "Entities: " entityType "/" entityName)])]
   [:div {:style {:float "right"}}
    [comps/Button
     {:color (style/colors :border-gray)
      :text "Details..."
      :onClick #(utils/rlog "TODO : take me to the details please")}]
    [comps/Button
     {:color "Red"
      :text "Abort"
      :onClick #(utils/rlog "TODO : abort the submission here!")}]
    ]
   [:div {:style {:clear "both"}}]])


(defn- render-loaded-submissions [submissions]
  (let [count-total (count submissions)]
    [:div {:style {:padding "1em"}}
     [:div {} (str "Monitor Submissions")]
     (if (= count-total 0)
       [:div {:style {:paddingTop "1em"}}
        [:em {} (str "There are no submissions.")]]
       (map render-running-loaded-single-submission-summary submissions))]))


(react/defc SubmissionsList
  {:load-submissions
   (fn [{:keys [props state]}]
     (let [canned-response
             {:responseText
              (utils/->json-string
                (create-mock-submissions-list (:workspace props)))
              :status 200
              :delay-ms (rand-int 2000)}
           url (paths/list-submissions-path (:workspace props))
           on-done (fn [{:keys [success? xhr]}]
                     (if success?
                       (swap! state assoc :submissions
                         (utils/parse-json-string (.-responseText xhr) ))
                       (swap! state assoc :error (.-responseText xhr))))]
       (utils/ajax-orch url
         {:canned-response canned-response
          :on-done on-done})))
   :component-did-mount
    (fn [{:keys [this]}]
      (react/call :load-submissions this))
   :render
   (fn [{:keys [state]}]
     [:div {}
      (cond
        (:error @state) (str "Firecloud returned error : " (:error @state))
        (:submissions @state) (render-loaded-submissions (:submissions @state))
        :else [comps/Spinner {:text "Loading submissions ..."}])])})


(defn render-submissions [ws]
  [SubmissionsList {:workspace ws}])
