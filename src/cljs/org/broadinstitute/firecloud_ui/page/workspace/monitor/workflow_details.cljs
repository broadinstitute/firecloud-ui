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

(def execution-calls-count (atom 0))

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

(defn chart-options [executionCallsCount]
  (clj->js {:colors (list "#E7E7E7", "#04E9E7", "#009DF4", "#0201F4", "#01FC01", "#00C400", "#008C00", "#CCAD51", "#2dd801", "#F99200", "#9854C6", "#F800FD", "#BC0000", "#FD0000")
            :height (+ (* executionCallsCount 18) 85)
            :timeline {
                :avoidOverlappingGridLines false
                :showBarLabels false
                :rowLabelStyle {:fontName "Roboto" :fontSize 12 :color "#333"}
                ;; Although bar labels are unshown, they still affect the height of each row. So make it small.
                :barLabelStyle {:fontName "Roboto" :fontSize 8 :color "#333" }}}))

(defn add-dataTable-row [data-table entry start end]
  (when (< start end) (.addRow data-table entry)))

(defn add-to-chart [calldata workflow-name data-table workflow-start workflow-end]
  (let [
        execution-status (get calldata "executionStatus")
        shard-index (get calldata "shardIndex" -1)
        attempt (get calldata "attempt")
        events (get calldata "executionEvents")
        call-start (when (get calldata "start") (js/moment (get calldata "start")))
        call-end (when (get calldata "end") (js/moment (get calldata "end")))
        call-name (if (= shard-index -1) (str "0") (str shard-index))
        call-version (if (and (= execution-status "Done") (= attempt 1))
                        (str call-name)
                        (if (some? attempt) (str "retry-" attempt) (str call-name)))
        firstEventStart (atom nil)
        finalEventEnd (atom nil)
       ]
    (let [mark (js/moment workflow-start)
          markend (.add (js/moment workflow-start) 1 "ms")
          ix (add-dataTable-row data-table (array call-version "begin" (.toDate mark) (.toDate markend)) mark markend)])
    (cond
      (= execution-status "Running")
        (let [count (swap! execution-calls-count inc)
              ix (if (nil? call-end)
                      (add-dataTable-row data-table
                               (array call-version "Running" (js/Date. (get calldata "start")) (js/Date. (.now js/Date)))
                               call-start (js/moment (.now js/Date)))
                      (add-dataTable-row data-table
                               (array call-version "Still running when workflow ended" (js/Date. (get calldata "start")) (js/Date. get calldata "end"))
                               call-start call-end))])
      (= execution-status "Starting")
        (let [count  (swap! execution-calls-count inc)
              ix (add-dataTable-row data-table (array call-version "Starting" (js/Date. (get calldata "start")) (js/Date. (.now js/Date)))
                          call-start (js/moment (.now js/Date)))])
      (or (= execution-status "Done") (= execution-status "Failed") (= execution-status "Preempted"))
        (do (dorun (map #(% (let [evstart (js/moment (get % "startTime"))
                                 evend (js/moment (get % "endTime"))
                                 date1 (when (or (nil? @firstEventStart) (< evstart @firstEventStart)) (reset! firstEventStart evstart))
                                 date2 (when (or (nil? @finalEventEnd) (> evend @finalEventEnd)) (reset! finalEventEnd evend))
                                 ix3 (add-dataTable-row data-table
                                      (array call-version (get % "description") (js/Date. (get % "startTime")) (js/Date. (get % "endTime")))
                                      evstart evend)])) events))
          (swap! execution-calls-count inc)))

      (if (or (nil? @firstEventStart) (nil? @finalEventEnd))
        (let [ix (add-dataTable-row data-table (array call-version execution-status (js/Date. (get calldata "start")) (js/Date. (get calldata "end")))
                          call-start call-end)])
        (let [startovehead (when (< call-start @firstEventStart)
              (add-dataTable-row data-table (array call-version "cromwell starting overhead" (js/Date. (get calldata "start")) (.toDate @firstEventStart))
                        call-start @firstEventStart))
              endoverhead (when (> call-end @finalEventEnd)
              (add-dataTable-row data-table (array call-version "cromwell final overhead" (.toDate @finalEventEnd) (js/Date. (get calldata "end")))
                        @finalEventEnd call-end))]))
          (let [mark (js/moment workflow-end)
          markend (.add (js/moment workflow-end) 1 "ms")
          ix (add-dataTable-row data-table (array call-version "end" (.toDate mark) (.toDate markend)) mark markend)])))

(defn generate-chart [data workflow-name container-id workflow-start workflow-end]
  (let [container (.getElementById js/document container-id)
        chart (js/google.visualization.Timeline. container)
        data-table (js/google.visualization.DataTable. chart)
        prevCt (reset! execution-calls-count 0)]

    (doto data-table
      (.addColumn #js {:type "string", :id "Position"})
      (.addColumn #js {:type "string", :id "Name"})
      (.addColumn #js {:type "date", :id "Start"})
      (.addColumn #js {:type "date", :id "End"}))
    (dorun (map #(% (let [reps (add-to-chart % workflow-name data-table workflow-start workflow-end)])) data))
    (when (not= @execution-calls-count 0) (.draw chart data-table (chart-options @execution-calls-count)))))

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
    (let [workflow-name (workflow-name (:label props))
     call-name (call-name (:label props))
     chart-name (str "chart_div_" workflow-name "_" call-name)]
     [:div {:style {:margin "0"}}
      [:div {:style {:display "inline-block"}}
      [:div {:style {:display "inline-block" :width "25vw"}}
        (style/create-link {:text (:label props)
                            :target "_blank"
                            :style {:color "-webkit-link" :textDecoration "underline"}
          :href (str moncommon/google-cloud-context (:bucketName props) "/" (:submission-id props)
                     "/" workflow-name "/" (:workflowId props) "/" call-name "/")})
      (style/create-link {:text (if (:expanded @state) "Hide" "Show")
                          :style {:marginLeft ".5em"}
                          :onClick #(swap! state assoc :expanded (not (:expanded @state)))})]
      [:div {:style {:display "inline-block" :float "right" :minWidth "60vw" :margin "0" :padding "0"} :id chart-name } "chart"]]

      (when (:expanded @state)
        (map-indexed
          (fn [index data]
            [:div {:style {:padding "0.5em 0 0 0.5em" :marginBottom "1em"}}
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
          (:data props)))]))

    :componentDidMount(fn [{:keys [props state]}]
           (let [workflow-name (workflow-name (:label props))
             call-name (call-name (:label props))
             chart-id (str "chart_div_" workflow-name "_" call-name)]
             (generate-chart (:data props) workflow-name chart-id (:beginTime props) (:endTime props))
             ))})


(defn- render-workflow-detail [workflow workflow-name submission-id bucketName]
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
      (create-field "Workflow Log" (display-value wlogurl (str "workflow." (workflow "id") ".log"))))]]

   [:div {:style {:marginTop ".5em"  :marginBottom ".5em" :fontWeight 500}} "Calls:"]

   (for [[call data] (sort-by first (workflow "calls"))]
     [CallDetail {:label call :data data :submission-id submission-id :bucketName bucketName :workflowId (workflow "id") :beginTime (workflow "start") :endTime (workflow "end")}])])


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
         (render-workflow-detail (:response server-response)
                                 (:workflow-name props) (:submission-id props) (:bucketName props)))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint
       (endpoints/get-workflow-details
        (:workspace-id props) (:submission-id props) (:workflow-id props))
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (swap! state assoc :server-response
                         {:success? success?
                          :response (if success? (get-parsed-response false) status-text)}))}))})


(defn render [props]
  (assert (every? #(contains? props %) #{:workspace-id :submission-id :workflow-id}))
  [WorkflowDetails props])
