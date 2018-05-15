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
   ))

(defn- create-field [label & contents]
  [:div {:style {:paddingBottom "0.25em"}}
   [:div {:style {:display "inline-block" :width 130}} (str label ":")]
   contents])

(defn- display-value
  ([gcs-uri] (display-value gcs-uri nil))
  ([gcs-uri link-label]
   (if-let [parsed (common/dos-or-gcs-uri? gcs-uri)]
     [FilePreviewLink (assoc parsed :attributes {:style {:display "inline"}}
                                    :link-label link-label)]
     (str gcs-uri))))

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
     [:div {}
      (create-field
       (:label props)
       (if (empty? (:data props))
         "None"
         (links/create-internal {:onClick #(swap! state update :expanded not)}
           (if (:expanded @state) "Hide" "Show"))))
      (when (:expanded @state)
        [:div {:style {:padding "0.25em 0 0.25em 1em"}}
         (let [columns [{:header "Label"
                         :column-data #(last (string/split (key %) #"\."))}
                        {:header "Value"
                         :initial-width :auto
                         :sortable? false
                         :column-data #(->> % second display-value)}]
               task-column {:header "Task"
                            :column-data #(second (string/split (key %) #"\."))}]
           [Table
            {:data (:data props)
             :body {:style table-style/table-heavy
                    :behavior {:reorderable-columns? false
                               :filterable? false}
                    :columns (if (:call-detail? props) columns (cons task-column columns))}}])])])})

(react/defc- WorkflowTiming
  {:render
   (fn [{:keys [props state refs]}]
     (let [{:keys [error? expanded?]} @state
           {:keys [label data]} props]
       [:div {}
        (create-field
         label
         (if (empty? data)
           "Not Available"
           (links/create-internal {:onClick #(swap! state update :expanded? not)}
             (if expanded? "Hide" "Show"))))
        (when expanded?
          [:div {}
           (if error?
             (style/create-server-error-message "Error loading charts.")
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
           [:div {:ref "chart-container" :style {:paddingTop "0.5rem"}}]])]))})

(defn- backend-logs [data]
  (when-let [log-map (data "backendLogs")]
    ;; Right now we only ever have a single log, so we should only ever hit the "true" case.
    ;; But in case something changes, I'll leave the general case so the UI doesn't totally bomb.
    (if (= 1 (count log-map))
      (let [log-name (last (string/split (first (vals log-map)) #"/"))]
        (create-field "JES log" (display-value (first (vals log-map)) log-name)))
      [:div {:style {:paddingBottom "0.25em"}} "Backend logs:"
       (map
        (fn [[name value]]
          (create-field name (display-value value)))
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
       (endpoints/get-workspace-genomic-operations
        ;; strip out operations/ from the job id
        (:workspace-id props) (last (string/split (:job-id props) #"/")))
       :on-done (fn [{:keys [success? get-parsed-response status-text raw-response]}]
                  (swap! state assoc :server-response
                         {:success? success?
                          :response (if success? (get-parsed-response false) status-text)
                          :raw-response raw-response}))}))})

(react/defc- CallDetail
  {:get-initial-state
   (fn []
     {:expanded false})
   :render
   (fn [{:keys [props state]}]
     [:div {:style {:marginTop "1em"}}
      (when (:show-operation-dialog? @state)
        [OperationDialog (:operation-dialog-props @state)])
      [:div {:style {:display "inline-block" :marginRight "1em"}}
       (let [workflow-name (workflow-name (:label props))
             call-name (call-name (:label props))]
         (links/create-external {:href (str moncommon/google-storage-context (:bucketName props) "/" (:submission-id props)
                                            "/" workflow-name "/" (:workflowId props) "/call-" call-name "/")}
           (:label props)))]
      (links/create-internal {:onClick #(swap! state update :expanded not)}
        (if (:expanded @state) "Hide" "Show"))
      (when (:expanded @state)
        (map-indexed
         (fn [index data]
           [:div {:style {:padding "0.5em 0 0 0.5em"}}
            [:div {:style {:paddingBottom "0.25em"}} (str "Call #" (inc index) ":")]
            [:div {:style {:paddingLeft "0.5em"}}
             (create-field "Operation"
                           ;; Note: using [:a ...] instead of style/create-link to be consistent with GCSFilePreviewLink
                           [:a {:href "javascript:;"
                                :onClick (fn [_]
                                           (swap! state assoc
                                                  :show-operation-dialog? true
                                                  :operation-dialog-props {:workspace-id (:workspace-id props)
                                                                           :job-id (data "jobId")
                                                                           :dismiss #(swap! state dissoc :show-operation-dialog?)}))}
                            (data "jobId")])
             (let [status (data "executionStatus")]
               (create-field "Status" (moncommon/icon-for-call-status status) status))
             (when (= (get-in data ["callCaching" "effectiveCallCachingMode"]) "ReadAndWriteCache")
               (create-field "Cache Result" (moncommon/format-call-cache (get (data "callCaching") "hit"))))
             (create-field "Started" (moncommon/render-date (data "start")))
             ;(utils/cljslog data)
             (create-field "Ended" (moncommon/render-date (data "end")))
             [IODetail {:label "Inputs" :data (data "inputs") :call-detail? true}]
             [IODetail {:label "Outputs" :data (data "outputs") :call-detail? true}]
             (create-field "stdout" (display-value (data "stdout") (last (string/split (data "stdout") #"/"))))
             (create-field "stderr" (display-value (data "stderr") (last (string/split (data "stderr") #"/"))))
             (backend-logs data)
             (when-let [failures (data "failures")]
               [Failures {:data failures}])]])
         (:data props)))])})


(defn- render-workflow-detail [workflow raw-data workflow-name submission-id bucketName workspace-id]
  [:div {:style {:padding "1em" :border style/standard-line :borderRadius 4
                 :backgroundColor (:background-light style/colors)}}
   [:div {}
    (let [inputs (ffirst (workflow "calls"))
          input-names (string/split inputs ".")
          workflow-name (first input-names)]
      (create-field "Workflow ID"
                    (links/create-external {:href (str moncommon/google-storage-context
                                                       bucketName "/" submission-id "/"
                                                       workflow-name "/" (workflow "id") "/")}
                      (workflow "id"))))
    (let [status (workflow "status")]
      (create-field "Status" (moncommon/icon-for-wf-status status)))
    (let [call-cache-status (-> (workflow "calls") vals first first (get "callCaching") (get "effectiveCallCachingMode"))]
      (create-field "Call Caching" (moncommon/call-cache-result call-cache-status)))
    (when (workflow "submission")
      (create-field "Submitted" (moncommon/render-date (workflow "submission"))))
    (when (workflow "start")
      (create-field "Started" (moncommon/render-date (workflow "start"))))
    (when (workflow "end")
      (create-field "Ended" (moncommon/render-date (workflow "end"))))
    [IODetail {:label "Inputs" :data (utils/parse-json-string (get-in workflow ["submittedFiles", "inputs"]))}]
    [IODetail {:label "Outputs" :data (workflow "outputs")}]
    [:div {:style {:whiteSpace "nowrap" :marginRight "0.5em"}}
     (let [wlogurl (str "gs://" bucketName "/" submission-id "/workflow.logs/workflow."
                        (workflow "id") ".log")]
       (create-field "Workflow Log" (display-value wlogurl (str "workflow." (workflow "id") ".log"))))]
    (when (seq (workflow "calls"))
      [WorkflowTiming {:label "Workflow Timing" :data raw-data :workflow-name workflow-name}])
    (when-let [failures (workflow "failures")]
      [Failures {:data failures}])]
   (when (seq (workflow "calls"))
     [:div {:style {:marginTop "1em" :fontWeight 500}} "Calls:"])
   (for [[call data] (workflow "calls")]
     [CallDetail {:label call :data data :submission-id submission-id :bucketName bucketName :workflowId (workflow "id")
                  :workspace-id workspace-id}])])


(react/defc- WorkflowDetails
  {:render
   (fn [{:keys [state props]}]
     (let [server-response (:server-response @state)]
       (cond
         (nil? server-response)
         (spinner "Loading workflow details...")
         (not (:success? server-response))
         (style/create-server-error-message (:response server-response))
         :else
         (render-workflow-detail (:response server-response) (:raw-response server-response)
                                 (:workflow-name props) (:submission-id props) (:bucketName props)
                                 (:workspace-id props)))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint
       (endpoints/get-workflow-details
        (:workspace-id props) (:submission-id props) (:workflow-id props))
       :on-done (fn [{:keys [success? get-parsed-response status-text raw-response]}]
                  (swap! state assoc :server-response
                         {:success? success?
                          :response (if success? (get-parsed-response false) status-text)
                          :raw-response raw-response}))}))})


(defn render [props]
  (assert (every? #(contains? props %) #{:workspace-id :submission-id :workflow-id}))
  [WorkflowDetails props])
