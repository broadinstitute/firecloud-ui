(ns org.broadinstitute.firecloud-ui.page.workspace.monitor.workflow-details
  (:require
    [dmohs.react :as react]
    [clojure.string :as string]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.gcs-file-preview :refer [GCSFilePreviewLink]]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.workspace.monitor.common :as moncommon]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- create-field [label & contents]
  [:div {:style {:paddingBottom "0.25em"}}
   [:div {:style {:display "inline-block" :width 100}} (str label ":")]
   contents])


(defn- display-value [gcs-uri]
  (if-let [parsed (common/parse-gcs-uri gcs-uri)]
    [GCSFilePreviewLink (assoc parsed :attributes {:style {:display "inline"}})]
    (str gcs-uri)))

; extract workflow name from dotted string; in anticipation of complexity
(defn- workflow-name [callName]
  (let [names (string/split callName ".")]
    js/return (first names);
    ))

; extract call name from dotted string; in anticipation of complexity
(defn- call-name [callName]
  (let [names (string/split callName ".")]
    js/return (rest names);
    ))       

(react/defc IODetail
  {:get-initial-state
   (fn []
     {:expanded false})
   :render
   (fn [{:keys [props state]}]
     [:div {}
      (create-field
        (:label props)
        (if (empty? (:data props))
          "None"
          (style/create-link {:text (if (:expanded @state) "Hide" "Show")
                              :onClick #(swap! state assoc :expanded (not (:expanded @state)))})))
      (when (:expanded @state)
        [:div {:style {:padding "0.25em 0 0.25em 1em"}}
         (for [[k v] (:data props)]
           [:div {} k [:span {:style {:margin "0 1em"}} "→"] (display-value v)])])])})


(defn- backend-logs [data]
  (when-let [log-map (data "backendLogs")]
    ;; Right now we only ever have a single log, so we should only ever hit the "true" case.
    ;; But in case something changes, I'll leave the general case so the UI doesn't totally bomb.
    (if (= 1 (count log-map))
      (create-field "JES log" (display-value (first (vals log-map))))
      [:div {:style {:paddingBottom "0.25em"}} "Backend logs:"
       (map
         (fn [[name value]]
           (create-field name (display-value value)))
         log-map)])))

(react/defc CallDetail
  {:get-initial-state
   (fn []
     {:expanded false})
   :render
   (fn [{:keys [props state]}]
     [:div {:style {:marginTop "1em"}}
      [:div {:style {:display "inline-block" :marginRight "1em"}} 
       (let [workflowName (workflow-name (:label props)) callName (call-name (:label props))]
        (style/create-link {:text (:label props)
                     :href (str moncommon/google-cloud-context (:bucketName props) "/" (:submission-id props)  "/" workflowName "/" (:workflowId props) "/" callName "/" )
                    })
        )]
      (style/create-link {:text (if (:expanded @state) "Hide" "Show")
                          :onClick #(swap! state assoc :expanded (not (:expanded @state)))})
      (when (:expanded @state)
        (map-indexed
          (fn [index data]
            [:div {:style {:padding "0.5em 0 0 0.5em"}}
             [:div {:style {:paddingBottom "0.25em"}} (str "Call #" (inc index) ":")]
             [:div {:style {:paddingLeft "0.5em"}}
              (create-field "ID" (data "jobId"))
              (let [status (data "executionStatus")]
                (create-field "Status" (moncommon/icon-for-call-status status) status))
              (create-field "Started" (moncommon/render-date (data "start")))
              (create-field "Ended" (moncommon/render-date (data "end")))
              [IODetail {:label "Inputs" :data (data "inputs")}]
              [IODetail {:label "Outputs" :data (data "outputs")}]
              (create-field "stdout" (display-value (data "stdout")))
              (create-field "stderr" (display-value (data "stderr")))
              (backend-logs data)]])
          (:data props)))])})


(defn- render-workflow-detail [workflow submission-id bucketName]
  [:div {:style {:padding "1em" :border style/standard-line :borderRadius 4
                 :backgroundColor (:background-gray style/colors)}}
   [:div {}
    (create-field "Workflow ID" (workflow "id"))
    (let [status (workflow "status")]
      (create-field "Status" (moncommon/icon-for-wf-status status) status))
    (when (workflow "submission")
      (create-field "Submitted" (moncommon/render-date (workflow "submission"))))
    (when (workflow "start")
      (create-field "Started" (moncommon/render-date (workflow "start"))))
    (when (workflow "end")
      (create-field "Ended" (moncommon/render-date (workflow "end"))))
    [IODetail {:label "Inputs" :data (workflow "inputs")}]
    [IODetail {:label "Outputs" :data (workflow "outputs")}]]
      (create-field "Workflow Log" (style/create-link {:text (str moncommon/google-cloud-context bucketName "/" submission-id "/workflow.logs/workflow." (workflow "id") ".log" )
                                              :href (str moncommon/google-cloud-context bucketName "/" submission-id "/workflow.logs/workflow." (workflow "workflowId") ".log" )}))

   [:div {:style {:marginTop "1em" :fontWeight 500}} "Calls:"]
   (for [[call data] (workflow "calls")]
     [CallDetail {:label call :data data :submission-id submission-id :bucketName bucketName :workflowId (workflow "id")}])])


(react/defc WorkflowDetails
  {:render
   (fn [{:keys [state props]}] 
     (let [server-response (:server-response @state) submission-id (props "submission-id") bucketName (props "bucketName")]
       (cond
         (nil? server-response)
         [:div {} [comps/Spinner {:text "Loading workflow details..."}]]
         (not (:success? server-response))
         (style/create-server-error-message (:response server-response))
         :else 
         (render-workflow-detail (:response server-response) (:submission-id props) (:bucketName props) ))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint
       (endpoints/get-workflow-details
        (:workspace-id props) (:submission-id props) (:workflow-id props))
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (swap! state assoc :server-response
                         {:success? success?
                          :response (if success? (get-parsed-response) status-text)}))}))})


(defn render [props]
  (assert (every? #(contains? props %) #{:workspace-id :submission-id :workflow-id}))

  [WorkflowDetails props])
