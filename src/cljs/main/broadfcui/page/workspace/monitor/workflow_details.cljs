(ns broadfcui.page.workspace.monitor.workflow-details
  (:require
   [dmohs.react :as react]
   [clojure.walk :as walk]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.codemirror :refer [CodeMirror]]
   [broadfcui.common.components :as comps]
   [broadfcui.common.gcs-file-preview :refer [FilePreviewLink]]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.script-loader :refer [ScriptLoader]]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.workspace.monitor.common :as moncommon]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   ))

(defn- create-field [label & contents]
  [:div {:style {:paddingBottom "0.25em"}}
   [:div {:style {:display "inline-block" :width 130}} (str label ":")]
   contents])

(defn- display-value
  ([workspace-namespace gcs-uri] (display-value workspace-namespace gcs-uri nil))
  ([workspace-namespace gcs-uri link-label]
   (if-let [parsed (common/dos-or-gcs-uri? gcs-uri)]
     [FilePreviewLink (assoc parsed :attributes {:style {:display "inline"}}
                                    :workspace-namespace workspace-namespace
                                    :link-label link-label)]
     (str gcs-uri))))

(defonce default-metadata-includes ["backendLogs" "backendStatus" "end" "executionStatus" "callCaching:hit" "failures"
                                    "id" "jobId" "start" "status" "stderr" "stdout" "submission" "subworkflowId"
                                    "workflowLog" "workflowName" "workflowRoot"])

(defonce metadata-timing-includes ["attempt" "description" "end" "endTime" "executionEvents" "executionStatus"
                                   "shardIndex" "start" "startTime" "workflowName"])

(defonce metadata-inputs-includes ["submittedFiles:inputs" "inputs"])

;; requesting executionStatus for outputs makes the response payload somewhat larger, but it forces Cromwell to return
;; data about every call in a task scatter. Otherwise, if we request just includeKey=outputs and a scatter contains both
;; successes and failures, Cromwell will return only those calls that have outputs, i.e. the successes. Since our CLJS code
;; identifies calls by their index in an array, this causes off-by-one (or off-by-many) functional errors.
(defonce metadata-outputs-includes ["executionStatus" "outputs"])

(defn getTimingDiagramHeight [chartContainer]
  (let [e (-> chartContainer (.-childNodes)
              (aget 0) (.-childNodes)
              (aget 0) (.-childNodes)
              (aget 0) (.-childNodes))]
    (-> (if (> (alength e) 1)
          (let [el (-> e (aget 1) (.-childNodes) (aget 0) (.-childNodes) (aget 1) (.-childNodes))]
            (aget el (dec (alength el))))
          (let [el (-> e (aget 0) (.-childNodes) (aget 1) (.-childNodes))]
            (aget el (dec (alength el)))))
        (aget "height") (aget "animVal") (aget "value"))))

(defn- workflow-name [callName]
  (first (string/split callName ".")))

(defn- call-name [callName]
  (string/join "." (rest (string/split callName "."))))

(react/defc- IODetail
  {:get-initial-state
   (fn []
     {:expanded false})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [data-fn data-path data workspace-id]} props]
       [:div {}
        (create-field
         (:label props)
           ;; when the user clicks to expand, trigger the request to get data
           (links/create-internal {:onClick #(when (:expanded (swap! state update :expanded not)) (data-fn))}
             (if (:expanded @state) "Hide" "Show")))
        (when (:expanded @state)
          (cond
            (nil? data)
              (spinner (str "Loading " (:label props) "...")) ;; show a spinner while data from the onClick loads
            (not (:success? data))
              (style/create-inline-error-message (:response data))
            :else
              ;; some portions of the metadata response from Cromwell are escaped json strings (e.g. submittedFiles/inputs),
              ;; instead of valid json. Handle that transparently here: if we detect the target data is a string, attempt to parse it.
              (let [raw-data (get-in data (cons :response data-path))
                    usable-data (if (string? raw-data) (utils/parse-json-string raw-data) raw-data)]
                (if (empty? usable-data)
                  [:div {:style {:padding "0.25em 0 0.5em 1em" :font-style "italic"}} (str "No " (:label props) ".")]
                  [:div {:style {:padding "0.25em 0 0.25em 1em"}}
                    (let [namespace (get-in props [:workspace-id :namespace])
                          columns [{:header "Label"
                                    :column-data #(last (string/split (key %) #"\."))}
                                   {:header "Value"
                                    :initial-width :auto
                                    :sortable? false
                                    :column-data #(display-value namespace (second %))}]
                          task-column {:header "Task"
                                       :column-data #(second (rseq (string/split (key %) #"\.")))}]
                      [Table
                       {:data usable-data
                        :body {:style table-style/table-heavy
                               :behavior {:reorderable-columns? false
                                          :filterable? false}
                               :columns (if (:call-detail? props) columns (cons task-column columns))}}])]))))]))})

(react/defc- WorkflowTimingDiagram
  {:render
    (fn [{:keys [props state refs]}]
      (let [{:keys [error? expanded? metadata-response]} @state
            {:keys [workspace-id submission-id workflow]} props
            workflow-name (workflow "workflowName")
            workflow-id (workflow "id")]
        (cond
          (nil? metadata-response)
            (spinner "Loading timing diagram...")
          (not (:success? metadata-response))
            (style/create-inline-error-message (str "Error loading timing data: " (:response metadata-response)))
          :else
            (let [data (:raw-response metadata-response)]
              [:div {}
               (if error?
                (style/create-inline-error-message "Error loading charts.")
                [ScriptLoader
                  {:on-error #(swap! state assoc :error? true)
                  :on-load (fn []
                              (js/google.charts.load "current" (clj->js {"packages" ["timeline"]}))
                              (js/google.charts.setOnLoadCallback
                              (fn []
                                (let [chart-container (@refs "chart-container")]
                                  (.timingDiagram js/window chart-container data workflow-name 100)
                                  (let [height (getTimingDiagramHeight chart-container)]
                                    (.timingDiagram js/window chart-container data workflow-name (+ 75 height)))))))
                  :path "https://www.gstatic.com/charts/loader.js"}])
              [:div {:ref "chart-container" :style {:paddingTop "0.5rem"}}]]))))
   :component-did-mount
    (fn [{:keys [props state]}]
      (let [{:keys [workspace-id submission-id workflow]} props
            workflow-name (workflow "name")
            workflow-id (workflow "id")]
        (endpoints/call-ajax-orch
          {:endpoint
           (endpoints/get-workflow-details workspace-id submission-id workflow-id metadata-timing-includes)
           :on-done (fn [{:keys [success? get-parsed-response status-text raw-response]}]
                      (swap! state assoc :metadata-response
                             {:success? success?
                              :response (if success? (get-parsed-response false) status-text)
                              :raw-response raw-response}))})))})

(react/defc- WorkflowTimingContainer
  {:render
   (fn [{:keys [props state refs]}]
     (let [{:keys [expanded?]} @state
           {:keys [workspace-id submission-id workflow label]} props]
       [:div {}
        (create-field label
          (links/create-internal {:onClick #(swap! state update :expanded? not)}
            (if expanded? "Hide" "Show")))
        ;; use expanded? to show/hide the WorkflowTimingDiagram component via the DOM
        [:div {:style {:display (if expanded? "block" "none")}}
          (when (some? expanded?)
            ;; as long as the user has clicked to show the WorkflowTimingDiagram at least once,
            ;; render it. After the first render, show/hide it via the DOM so we don't trigger
            ;; its ajax call more than  once.
            [WorkflowTimingDiagram (utils/restructure workspace-id submission-id workflow)])]]))})

(defn- backend-logs [data workspace-id]
  (when-let [log-map (data "backendLogs")]
    ;; Right now we only ever have a single log, so we should only ever hit the "true" case.
    ;; But in case something changes, I'll leave the general case so the UI doesn't totally bomb.
    (if (= 1 (count log-map))
      (let [log-name (last (string/split (first (vals log-map)) #"/"))]
        (create-field "JES log" (display-value (:namespace workspace-id) (first (vals log-map)) log-name)))
      [:div {:style {:paddingBottom "0.25em"}} "Backend logs:"
       (map
        (fn [[name value]]
          (create-field name (display-value (:namespace workspace-id) value)))
        log-map)])))

(react/defc- Failures
  {:get-initial-state
   (fn []
     {:expanded false})
   :render
   (fn [{:keys [props state]}]
     [:div {}
      (create-field
       "Failures"
       (links/create-internal {:onClick #(swap! state update :expanded not)}
         (if (:expanded @state) "Hide" "Show")))
      (when (:expanded @state)
        [comps/Tree {:start-collapsed? false
                     :highlight-ends false
                     :data (walk/prewalk
                            (fn [elem]
                              (cond
                                ;; reorder map elements to message then causedBy
                                (map? elem) (select-keys elem ["message" "causedBy"])
                                ;; remove empty causedBy[] arrays - prewalk means this executes after the above
                                (= (second elem) []) nil
                                :else elem))
                            (:data props))}])])})

(react/defc- OperationDialog
  {:render
   (fn [{:keys [props state]}]
     [modals/OKCancelForm
      {:header "Operation Details"
       :content
       (let [server-response (:server-response @state)]
         (cond
           (nil? server-response)
           (spinner "Loading operation details...")
           (not (:success? server-response))
           (style/create-server-error-message (:response server-response))
           :else
           [CodeMirror {:text (:raw-response server-response)}]))
       :show-cancel? false
       :dismiss (:dismiss props)
       :ok-button {:text "Done" :onClick #((:dismiss props))}}])

   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint
       (endpoints/get-workflow-genomic-operations
        (:workflow-id props) (:job-id props))
       :on-done (fn [{:keys [success? get-parsed-response status-text raw-response]}]
                  (swap! state assoc :server-response
                         {:success? success?
                          :response (if success? (get-parsed-response false) status-text)
                          :raw-response raw-response}))}))})

(defn- render-gcs-path [components]
  (str moncommon/google-storage-context (string/join "/" components) "/"))

;; recursive partner of render-workflow-detail
(declare CallDetail)
(declare WorkflowDetails)

(react/defc- CallDetail
  {:get-initial-state
   (fn []
     {:expanded false
      :subworkflow-expanded {}})
   :render
   (fn [{:keys [props state]}]
     (let [call-path-components (conj (:gcs-path-prefix props) (str "call-" (call-name (:label props))))
           all-call-statuses (set (map #(get % "executionStatus") (:data props)))
           workspace-namespace (get-in props [:workspace-id :namespace])
           {:keys [label inputs-fn inputs-data outputs-fn outputs-data]} props]
       [:div {:style {:marginTop "1em"}}
        (when (:show-operation-dialog? @state)
          [OperationDialog (:operation-dialog-props @state)])
        [:div {:style {:display "inline-block" :marginRight "1em"}}
         (moncommon/icons-for-call-statuses all-call-statuses)
         (links/create-external {:href (render-gcs-path call-path-components)} (:label props))]
        (links/create-internal {:onClick #(swap! state update :expanded not)}
                               (if (:expanded @state) "Hide" "Show"))
        (when (:expanded @state)
          (map-indexed
            (fn [index data]
              [:div {:style {:padding "0.5em 0 0 0.5em"}}
               [:div {:style {:paddingBottom "0.25em"}}
                (if-let [subWorkflowId (data "subWorkflowId")]
                  [:span {}
                   (str "Call #" (inc index) " (Subworkflow ID " subWorkflowId "): ")
                   (if (contains? (:subworkflow-expanded @state) subWorkflowId)
                     [:span {}
                      ;; click to hide this subworkflow's WorkflowDetails and remove from the :subworkflow-expanded map in @state
                      (links/create-internal {:onClick #(swap! state update :subworkflow-expanded
                                                               (fn [expanded-map]
                                                                 (dissoc expanded-map subWorkflowId)))}
                                             "Hide")
                      ;; shardIndex -1 means un-scattered.  Omit the shard path element.
                      (let [subworkflow-path-prefix (if (>= (data "shardIndex") 0)
                                                      (conj call-path-components (str "shard-" index))
                                                      call-path-components)]
                        [WorkflowDetails (merge (select-keys props [:workspace-id :submission-id :use-call-cache :workflow-name
                                                                       :inputs-fn :inputs-data :outputs-fn :outputs-data])
                                                   {:subworkflow? true
                                                    :workflow-id subWorkflowId
                                                    :gcs-path-prefix subworkflow-path-prefix})])]
                     ;; click to show subworkflow details and add it to the :subworkflow-expanded map in @state
                     (links/create-internal {:onClick #(swap! state assoc-in
                                                              [:subworkflow-expanded subWorkflowId] true)}
                                            "Show"))]
                  (str "Call #" (inc index) ":"))]
               [:div {:style {:paddingLeft "0.5em"}}
                (create-field "Operation"
                              ;; Note: using [:a ...] instead of style/create-link to be consistent with FilePreviewLink
                              [:a {:href    "javascript:;"
                                   :onClick (fn [_]
                                              (swap! state assoc
                                                     :show-operation-dialog? true
                                                     :operation-dialog-props {:workflow-id (:workflowId props)
                                                                              :job-id       (data "jobId")
                                                                              :dismiss      #(swap! state dissoc :show-operation-dialog?)}))}
                               (data "jobId")])
                (let [status (data "executionStatus")]
                  (create-field "Status" (moncommon/icon-for-call-status status) status))
                (when (and (:use-call-cache props) (some? (get (data "callCaching") "hit")))
                  (create-field "Cache Result" (moncommon/format-call-cache (get (data "callCaching") "hit"))))
                (create-field "Started" (moncommon/render-date (data "start")))
                (create-field "Ended" (moncommon/render-date (data "end")))
                [IODetail {:label "Inputs" :data-path ["calls" label index "inputs"] :data-fn inputs-fn :data inputs-data :call-detail? (not (data "subWorkflowId")) :workspace-id (:workspace-id props)}]
                [IODetail {:label "Outputs" :data-path ["calls" label index "outputs"] :data-fn outputs-fn :data outputs-data :call-detail? (not (data "subWorkflowId")) :workspace-id (:workspace-id props)}]
                (when-let [stdout (data "stdout")]
                  (create-field "stdout" (display-value workspace-namespace stdout (last (string/split stdout #"/")))))
                (when-let [stderr (data "stderr")]
                  (create-field "stderr" (display-value workspace-namespace stderr (last (string/split stderr #"/")))))
                (backend-logs data (:workspace-id props))
                (when-let [failures (data "failures")]
                  [Failures {:data failures}])]])
            (:data props)))]))})

(react/defc- WorkflowCost
  {:render
   (fn [{:keys [state props]}]
     (let [cost-response (:cost-response @state)
           workflow-cost (:response cost-response)
           rendered-cost (if cost-response
                           (moncommon/render-cost workflow-cost)
                           (spinner {:style {:fontSize "1rem" :margin 0}} "Loading..."))]
         (create-field "Total Run Cost" rendered-cost)))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint
       (endpoints/get-workflow-cost
        (:workspace-id props) (:submission-id props) (:workflow-id props))
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (swap! state assoc :cost-response
                         {:success? success?
                          :response (if success? (:cost (get-parsed-response)) (str "Error: " (or (:message (get-parsed-response)) status-text)))}))}))})

(defn- render-workflow-detail [workflow raw-data workflow-name submission-id use-call-cache workspace-id gcs-path-prefix inputs-fn inputs-data outputs-fn outputs-data subworkflow?]
  (let [inputs (ffirst (workflow "calls"))
        input-names (string/split inputs ".")
        workflow-name-for-path (first input-names)
        workflow-path-components (conj gcs-path-prefix workflow-name-for-path (workflow "id"))
        unit-name (if subworkflow? "Subworkflow" "Workflow")]
    [:div {:style {:padding "1em" :border style/standard-line :borderRadius 4
                   :backgroundColor (:background-light style/colors)}}
     (create-field (str unit-name  " ID") (links/create-external {:href (render-gcs-path workflow-path-components)} (workflow "id")))
     ;; Subworkflows contain a lot of information that is redundant to what can be seen from the Calls
     ;; Only show new information
     (when-not subworkflow? [:div {}
      (let [status (workflow "status")]
        (create-field "Status" (moncommon/icon-for-wf-status status) status))
      [WorkflowCost {:workspace-id workspace-id
                      :submission-id submission-id
                      :workflow-id (workflow "id")}]
      (when-let [submission (workflow "submission")]
        (create-field "Submitted" (moncommon/render-date submission)))
      (when-let [start (workflow "start")]
        (create-field "Started" (moncommon/render-date start)))
      (when-let [end (workflow "end")]
        (create-field "Ended" (moncommon/render-date end)))
      [IODetail {:label "Inputs" :data-path ["submittedFiles" "inputs"] :data-fn inputs-fn :data inputs-data :workspace-id workspace-id}]
      [IODetail {:label "Outputs" :data-path ["outputs"] :data-fn outputs-fn :data outputs-data :workspace-id workspace-id}]
      (when-let [workflowLog (workflow "workflowLog")]
        (create-field "Workflow Log" (display-value (:namespace workspace-id) workflowLog (str "workflow." (workflow "id") ".log"))))])
     (when (seq (workflow "calls"))
       [WorkflowTimingContainer (merge {:label "Workflow Timing"} (utils/restructure workspace-id submission-id workflow))])
     (when-let [failures (workflow "failures")]
       [Failures {:data failures}])
     (when (seq (workflow "calls"))
       [:div {:style {:marginTop "1em" :fontWeight 500}} "Calls:"])
     (for [[call data] (workflow "calls")]
        [CallDetail (conj (utils/restructure data submission-id use-call-cache workspace-id
                                             inputs-fn inputs-data outputs-fn outputs-data)
                          {:label call
                           :workflowId (workflow "id")
                           :gcs-path-prefix workflow-path-components})])]))

(react/defc- WorkflowDetails
  {:get-default-props
   (fn [] {:subworkflow? false})
   :get-initial-state
   (fn [] {:metadata-inputs-lock? false
           :metadata-outputs-lock? false})
   :render
   (fn [{:keys [state props this]}]
     (let [metadata-response (:metadata-response @state)
           unit-name (if (:subworkflow? props) "subworkflow" "workflow")]
       (cond
         (nil? metadata-response)
         [:div {} (spinner (str "Loading " unit-name " details..."))]
         (not (:success? metadata-response))
         (style/create-server-error-message (:response metadata-response))
         :else
         ;; generate this workflow's GCS path prefix
         ;; subworkflows receive them as props from parent workflows
         ;; top-level workflows use workspace-bucket-name/submission-id
         (let [workflow-path-prefix [(:bucketName props) (:submission-id props)]]
           (render-workflow-detail (:response metadata-response) (:raw-response metadata-response)
                                   (:workflow-name props) (:submission-id props) (:use-call-cache props)
                                   (:workspace-id props) workflow-path-prefix
                                   #(this :get-inputs) (:metadata-inputs @state)
                                   #(this :get-outputs) (:metadata-outputs @state)
                                   (:subworkflow? props))))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint
       (endpoints/get-workflow-details
        (:workspace-id props) (:submission-id props) (:workflow-id props) default-metadata-includes)
       :on-done (fn [{:keys [success? get-parsed-response status-text raw-response]}]
                  (swap! state assoc :metadata-response
                         {:success? success?
                          :response (if success? (get-parsed-response false) status-text)
                          :raw-response raw-response}))}))
  :get-outputs
  (fn [{:keys [this]}]
    (this :-get-io :metadata-outputs :metadata-outputs-lock? metadata-outputs-includes))
  :get-inputs
  (fn [{:keys [this]}]
    (this :-get-io :metadata-inputs :metadata-inputs-lock? metadata-inputs-includes))
  :-get-io
  (fn [{:keys [props state]} data-key lock-key includes]
    (when-not (or (lock-key @state) (data-key @state))
      (do
        (swap! state assoc lock-key true)
        (endpoints/call-ajax-orch
          {:endpoint
          (endpoints/get-workflow-details
            (:workspace-id props) (:submission-id props) (:workflow-id props) includes)
          :on-done (fn [{:keys [success? get-parsed-response status-text status-code raw-response]}]
                      (swap! state assoc data-key
                            {:success? success?
                              :response (if success? (get-parsed-response false)
                                                     (ajax/extract-error-message get-parsed-response status-text status-code))
                              :raw-response raw-response})
                      (swap! state assoc lock-key false))}))))})

(defn render [props]
  (assert (every? #(contains? props %) #{:workspace-id :submission-id :use-call-cache :workflow-id}))
  [WorkflowDetails props])
