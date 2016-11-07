(ns org.broadinstitute.firecloud-ui.page.workspace.monitor.workflow-details
  (:require
    [dmohs.react :as react]
    [clojure.browser.dom :as gdom]
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

(def timingDiagramDrawn (atom false))

(defn- create-field [label & contents]
  [:div {:style {:paddingBottom "0.25em"}}
   [:div {:style {:display "inline-block" :width 130}} (str label ":")]
   contents])

(defn- display-value
  ([gcs-uri] (display-value gcs-uri nil))
  ([gcs-uri link-label]
   (if-let [parsed (common/parse-gcs-uri gcs-uri)]
     [GCSFilePreviewLink (assoc parsed :attributes {:style {:display "inline"}}
                                       :link-label link-label)]
     (str gcs-uri))))

(defn- workflow-name [callName]
  (first (string/split callName ".")))

(defn- call-name [callName]
  (string/join "." (rest (string/split callName "."))))

(defn draw-chart [data workflow-name]
  (.timingDiagram js/window data workflow-name)
  (reset! timingDiagramDrawn true))

(defn clear-chart []
  (when (true? @timingDiagramDrawn) 
    (try (gdom/remove-children "chart_div") 
      (catch js/Object e (.log js/console "timing diagram"))))
  (reset! timingDiagramDrawn false))

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


(react/defc WorkflowTiming
  {:get-initial-state
   (fn []
     {:expanded false})
   :render
   (fn [{:keys [props state]}]
     [:div {}
      (create-field
        (:label props)
        (if (empty? (:data props))
          "Not Available"
          (style/create-link {:text (if (:expanded @state) "Hide" "Show")
                              :onClick #(swap! state assoc :expanded (not (:expanded @state)))})))
          [:div {:style {:padding "0.25em 0 0 0"} :id "chart_div"}]
        (if (:expanded @state)
          (draw-chart (:data props) (:workflow-name props))
          (clear-chart))
      ])})

(defn- backend-logs [data]
  (when-let [log-map (data "backendLogs")]
    ;; Right now we only ever have a single log, so we should only ever hit the "true" case.
    ;; But in case something changes, I'll leave the general case so the UI doesn't totally bomb.
    (if (= 1 (count log-map))
      (let [log-name (last (string/split (first (vals log-map)) #"/" ))]
      (create-field "JES log" (display-value (first (vals log-map)) log-name)))
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
       (let [workflow-name (workflow-name (:label props))
             call-name (call-name (:label props))]
        (style/create-link {:text (:label props)
                            :target "_blank"
                            :style {:color "-webkit-link" :textDecoration "underline"}
          :href (str moncommon/google-cloud-context (:bucketName props) "/" (:submission-id props)
                     "/" workflow-name "/" (:workflowId props) "/" call-name "/")}))]
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
              (let [stdout-name (last (string/split (data "stdout") #"/"))
                    stderr-name (last (string/split (data "stderr") #"/"))]
              (create-field "stdout" (display-value (data "stdout") stdout-name))
              (create-field "stderr" (display-value (data "stderr") stderr-name)))
              (backend-logs data)]])
          (:data props)))])})



(defn- render-workflow-detail [workflow raw-data workflow-name submission-id bucketName]
  [:div {:style {:padding "1em" :border style/standard-line :borderRadius 4
                 :backgroundColor (:background-light style/colors)}}
   [:div {}
    (let [calls (workflow "calls")
          inputs (first (first (workflow "calls")))
          input-names (string/split inputs ".")
          workflow-name (first input-names)]
    (create-field "Workflow ID" 
                  (style/create-link {:text (workflow "id")
                                      :target "_blank"
                                      :style {:color "-webkit-link" :textDecoration "underline"}
                                      :href (str moncommon/google-cloud-context
                                                 bucketName "/" submission-id  "/"
                                                 workflow-name "/" (workflow "id") "/")})))
    (let [status (workflow "status")]
      (create-field "Status" (moncommon/icon-for-wf-status status) status))
    (when (workflow "submission")
      (create-field "Submitted" (moncommon/render-date (workflow "submission"))))
    (when (workflow "start")
      (create-field "Started" (moncommon/render-date (workflow "start"))))
    (when (workflow "end")
      (create-field "Ended" (moncommon/render-date (workflow "end"))))
    [IODetail {:label "Inputs" :data (workflow "inputs")}]
    [IODetail {:label "Outputs" :data (workflow "outputs")}]
    [:div {:style {:whiteSpace "nowrap" :marginRight "0.5em"}}
     (let [wlogurl (str "gs://" bucketName "/" submission-id "/workflow.logs/workflow."
                   (workflow "id") ".log")]
      (create-field "Workflow Log" (display-value wlogurl (str "workflow." (workflow "id") ".log"))))]
    [WorkflowTiming {:label "Workflow Timing" :data raw-data :workflow-name workflow-name}]]

   [:div {:style {:marginTop "1em" :fontWeight 500}} "Calls:"]
   (for [[call data] (workflow "calls")]
     [CallDetail {:label call :data data :submission-id submission-id :bucketName bucketName :workflowId (workflow "id")}])])


(react/defc WorkflowDetails
  {:render

   (fn [{:keys [state props proxy-mappings]}]
     (let [server-response (:server-response @state)
           submission-id (props "submission-id")
           bucketName (props "bucketName")]
       (cond
         (nil? server-response)
         [:div {} [comps/Spinner {:text "Loading workflow details..."}]]
         (not (:success? server-response))
         (style/create-server-error-message (:response server-response))
         :else
         (render-workflow-detail (:response server-response) (:raw-response server-response) 
                                 (:workflow-name props) (:submission-id props) (:bucketName props)))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint
       (endpoints/get-workflow-details
        (:workspace-id props) (:submission-id props) (:workflow-id props))
       :on-done (fn [{:keys [success? get-parsed-response status-text raw-response]}]
                  (swap! state assoc :server-response
                         {:success? success?
                          :response (if success? (get-parsed-response) status-text)
                          :raw-response raw-response}))}))})


(defn render [props]
  (assert (every? #(contains? props %) #{:workspace-id :submission-id :workflow-id}))
  (.load js/google "visualization" "1.0" {"packages" ["Timeline"]})
  [WorkflowDetails props])
