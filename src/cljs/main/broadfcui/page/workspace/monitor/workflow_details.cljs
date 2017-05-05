(ns broadfcui.page.workspace.monitor.workflow-details
  (:require
    [dmohs.react :as react]
    [clojure.string :as string]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.gcs-file-preview :refer [GCSFilePreviewLink]]
    [broadfcui.common.style :as style]
    [broadfcui.common.table.table :refer [Table]]
    [broadfcui.common.table.style :as table-style]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.page.workspace.monitor.common :as moncommon]
    [broadfcui.utils :as utils]
    [goog.dom :as gdom]
    ))

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

(defn getTimingDiagramHeight [chartContainer]
  (let [e (-> chartContainer (.-childNodes)
              (aget 0) (.-childNodes)
              (aget 0) (.-childNodes)
              (aget 0) (.-childNodes))]
    (-> (if (> (alength e) 1)
          (let [el (-> e (aget 1) (.-childNodes) (aget 0) (.-childNodes) (aget 1) (.-childNodes))]
            (aget el (- (alength el) 1)))
          (let [el (-> e (aget 0) (.-childNodes) (aget 1) (.-childNodes))]
            (aget el (- (alength el) 1))))
        (aget "height") (aget "animVal") (aget "value"))))

(defn- workflow-name [callName]
  (first (string/split callName ".")))

(defn- call-name [callName]
  (string/join "." (rest (string/split callName "."))))

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
         [Table
          {:data (:data props)
           :body {:style table-style/table-heavy
                  :behavior {:reorderable-columns? false
                             :sortable-columns? false
                             :filterable? false}
                  :columns [{:header "Task"
                             :column-data #(second (string/split (key %) #"\."))}
                            {:header "Label"
                             :column-data #(last (string/split (key %) #"\."))}
                            {:header "Value"
                             :initial-width :auto
                             :column-data #(->> % second display-value)}]}}]])])})


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
                              :onClick #(swap! state update :expanded not)})))
      [:div {:ref "chart-container" :style {:padding "0.25em 0 0 0"}}]])
   :component-did-mount #((:this %) :-update-element)
   :component-did-update #((:this %) :-update-element)
   :-update-element
   (fn [{:keys [props state refs]}]
     (if (:expanded @state)
       (do
         (.timingDiagram js/window (get @refs "chart-container") (:data props) (:workflow-name props) 100)
         (let [height (getTimingDiagramHeight (get @refs "chart-container"))]
           (gdom/removeChildren "chart-container")
           (.timingDiagram js/window (get @refs "chart-container") (:data props) (:workflow-name props) (+ 75 height))))
       (gdom/removeChildren (get @refs "chart-container"))))})

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
                                        "/" workflow-name "/" (:workflowId props) "/call-" call-name "/")}))]
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
              (when (= (-> (data "callCaching") (get "effectiveCallCachingMode")) "ReadAndWriteCache")
                (create-field "Cache Result" (moncommon/format-call-cache (-> (data "callCaching") (get "hit")))))
              (create-field "Started" (moncommon/render-date (data "start")))
              ;(utils/cljslog data)
              (create-field "Ended" (moncommon/render-date (data "end")))
              [IODetail {:label "Inputs" :data (data "inputs")}]
              [IODetail {:label "Outputs" :data (data "outputs")}]
              (create-field "stdout" (display-value (data "stdout") (last (string/split (data "stdout") #"/"))))
              (create-field "stderr" (display-value (data "stderr") (last (string/split (data "stderr") #"/"))))
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
                                                   bucketName "/" submission-id "/"
                                                   workflow-name "/" (workflow "id") "/")})))
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
                           :response (if success? (get-parsed-response false) status-text)
                           :raw-response raw-response}))}))})


(defn render [props]
  (assert (every? #(contains? props %) #{:workspace-id :submission-id :workflow-id}))
  [WorkflowDetails props])
