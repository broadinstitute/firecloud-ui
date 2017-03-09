(ns broadfcui.page.workspace.monitor.workflow-details
  (:require
    [dmohs.react :as react]
    [clojure.string :as string]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.gcs-file-preview :refer [GCSFilePreviewLink]]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.style :as style]
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
                      (style/create-link {:text    (if (:expanded @state) "Hide" "Show")
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
                                            (style/create-link {:text    (if (:expanded @state) "Hide" "Show")
                                                                :onClick #(swap! state update :expanded not)})))
                                        [:div {:ref "chart-container" :style {:padding "0.25em 0 0 0"}}]])
             :component-did-mount  #((:this %) :-update-element)
             :component-did-update #((:this %) :-update-element)
             :-update-element
                                   (fn [{:keys [props state refs]}]
                                       (if (:expanded @state)
                                         (do
                                           (.timingDiagram js/window (get @refs "chart-container") (:data props) (:workflow-name props) 100)
                                           (let [height (-> (get @refs "chart-container")
                                                            .-childNodes (aget 0)
                                                            .-childNodes (aget 0)
                                                            .-childNodes (aget 0)
                                                            .-childNodes (aget 1)
                                                            .-childNodes (aget 0)
                                                            (aget "height") (aget "animVal") (aget "value"))]
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
                        (style/create-link {:text   (:label props)
                                            :target "_blank"
                                            :style  {:color "-webkit-link" :textDecoration "underline"}
                                            :href   (str moncommon/google-cloud-context (:bucketName props) "/" (:submission-id props)
                                                         "/" workflow-name "/" (:workflowId props) "/call-" call-name "/")}))]
                  (style/create-link {:text    (if (:expanded @state) "Hide" "Show")
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
                                (create-field "stdout" (display-value (data "stdout") (last (string/split (data "stdout") #"/"))))
                                (create-field "stderr" (display-value (data "stderr") (last (string/split (data "stderr") #"/"))))
                                (backend-logs data)]])
                          (:data props)))])})




(react/defc WorkflowTimingNEW
            {:get-initial-state
             (fn []
                 {:expanded false})
             :render
             (fn [{:keys [props state]}]
                 [:div {:style {:marginTop "1em"}}

                  ;; Flex with row direction
                  [:div {:style {:marginTop 15}}
                   ; X axis
                   
                   [:div {:style {:display "flex" :flexDirection "row" :height 20 :fontSize 12}}
                    [:div {:style {:flex "0 1 auto" :borderLeft "1px solid rgb(244, 244, 244)" :borderRight style/standard-line :width "25%" :textAlign "right" :alignSelf "flex-end"}} "0"]
                    [:div {:style {:flex "0 1 auto" :borderRight style/standard-line :width "25%" :textAlign "right" :alignSelf "flex-end"}} "10"]
                    [:div {:style {:flex "0 1 auto" :borderRight style/standard-line :width "25%" :textAlign "right" :alignSelf "flex-end"}} "20"]
                    [:div {:style {:flex "0 1 auto" :borderRight style/standard-line :width "25%" :textAlign "right" :alignSelf "flex-end"}} "30"]]]

                  [:div {:style {:border "1px solid gray"}}
                   ;Row 1
                   [:div {:style {:display "flex" :flexDirection "row" :backgroundColor "white" :borderBottom style/standard-line :height 20}}
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderRight style/standard-line :width "25%" :fontSize 14 :alignSelf "center"}} "Call.One  "
                     (style/create-link {:text    (if (:expanded1 @state) "Hide" "Show")
                                         :onClick #(swap! state assoc :expanded1 (not (:expanded1 @state)))})]
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderRight style/standard-line :width "25%"}}
                     [:div {:style {:display "flex" :height "100%" :alignItems "center"}}
                      [:div {:style {:flex "0 1 auto" :position "relative" :width "60%" :height "75%" :backgroundColor "white"}}]
                      [:div {:style {:flex "0 1 auto" :position "relative" :width "20%" :height "75%" :backgroundColor "blue"}}]
                      [:div {:style {:flex "0 1 auto" :width "40%" :height "75%" :backgroundColor "red"}}]]]
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderRight style/standard-line :width "25%"}}]
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :width "25%"}}]]

                   (when (:expanded1 @state) [:div {:style {:x "100" :y "20" :width "100%" :height 90 :backgroundColor "rgb(244, 244, 244)" :borderBottom "1px solid gray"}}
                                              "\nCall Details go here!"])
                   ;Row2
                   [:div {:style {:display "flex" :flexDirection "row" :backgroundColor "white" :borderBottom style/standard-line :height 20}}
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderRight style/standard-line :width "25%" :fontSize 14 :alignSelf "center"}} "Call.Two  "
                     (style/create-link {:text (if (:expanded2 @state) "Hide" "Show")
                                         :onClick #(swap! state assoc :expanded2 (not (:expanded2 @state)))})]
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderRight style/standard-line :width "25%"}}]
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderRight style/standard-line :width "25%"}}]
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :width "25%"}}]]

                   (when (:expanded2 @state) [:div {:style {:x "100" :y "20" :width "100%" :height 90 :backgroundColor "rgb(244, 244, 244)" :borderBottom "1px solid gray" :fontSize 12}}
                                              "\nCall Details go here!"])

                   ;Row 3
                   [:div {:style {:display "flex" :flexDirection "row" :backgroundColor "white" :borderBottom style/standard-line :height 20}}
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderRight style/standard-line :width "25%" :fontSize 14 :alignSelf "center"}} "Call.Three  "
                     (style/create-link {:text    (if (:expanded3 @state) "Hide" "Show")
                                         :onClick #(swap! state assoc :expanded3 (not (:expanded3 @state)))})]
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderRight style/standard-line :width "25%"}}]
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderRight style/standard-line :width "25%"}}]
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :width "25%"}}]]

                   (when (:expanded3 @state) [:div {:style {:x "100" :y "20" :width "100%" :height 90 :backgroundColor "rgb(244, 244, 244)" :borderBottom "1px solid gray"}}
                                              "\nCall Details go here!"])
                   ]




                  ;; Flex with column direction
                  [:div {:style {:display "flex" :flexDirection "row" :borderRight "1px solid gray" :borderBottom "1px solid gray" :marginTop 15}}
                   [:div {:style {:display "flex" :flexDirection "column" :backgroundColor "white" :width "25%" :borderRight "1px solid gray"}}
                    [:div {:style {:flex "0 1 auto" :backgroundColor "rgb(244, 244, 244)" :borderLeft "1px solid gray" :borderBottom style/standard-line :borderRight style/standard-line :height 22
                                   :textAlign "right"}} "0"]
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderBottom style/standard-line
                                   :height 22 :fontFamily "Roboto"}} "Call.1"
                     (style/create-link {:text    (if (:expandedV1 @state) "Hide" "Show")
                                         :onClick #(swap! state assoc :expandedV1 (not (:expandedV1 @state)))})]
                    (when (:expandedV1 @state)[:div {:style {:x "100" :y "20" :position "relative" :width "400%" :height 90 :borderLeft "1px solid gray" :backgroundColor "rgb(244, 244, 244)"}}])
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderLeft "1px solid gray" :borderBottom style/standard-line :height 22}} "Call.2"
                     (style/create-link {:text    (if (:expandedV2 @state) "Hide" "Show")
                                         :onClick #(swap! state assoc :expandedV2 (not (:expandedV2 @state)))})]
                    (when (:expandedV2 @state)[:div {:style {:x "100" :y "20" :position "relative" :width "400%" :height 90 :backgroundColor "rgb(244, 244, 244)"}}])
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderBottom style/standard-line :height 22}}  "Call.3"
                     (style/create-link {:text    (if (:expandedV3 @state) "Hide" "Show")
                                         :onClick #(swap! state assoc :expandedV3 (not (:expandedV3 @state)))})]
                    (when (:expandedV3 @state)[:div {:style {:x "100" :y "20" :position "relative" :width "400%" :height 90 :backgroundColor "rgb(244, 244, 244)"}}])
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderBottom style/standard-line :height 22}} "Call.4"
                     (style/create-link {:text    (if (:expandedV4 @state) "Hide" "Show")
                                         :onClick #(swap! state assoc :expandedV4 (not (:expandedV4 @state)))})]
                    (when (:expandedV4 @state)[:div {:style {:x "100" :y "20" :position "relative" :width "400%" :height 90 :backgroundColor "rgb(244, 244, 244)"}}])]
                   [:div {:style {:display "flex" :flexDirection "column" :backgroundColor "white" :width "25%" :borderRight "1px solid lightgray"}}
                    [:div {:style {:flex "0 1 auto" :backgroundColor "rgb(244, 244, 244)" :borderBottom style/standard-line :height 22 :textAlign "right"}} "10"]
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderBottom style/standard-line :height 22}}]
                    (when (:expandedV1 @state) [:div {:style {:x "100" :y "20" :height 90 :backgroundColor "blue"}}])
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderBottom style/standard-line :height 22}}]
                    (when (:expandedV2 @state) [:div {:style {:x "100" :y "20" :height 90 :backgroundColor "blue"}}])
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderBottom style/standard-line :height 22}}]
                    (when (:expandedV3 @state) [:div {:style {:x "100" :y "20" :height 90 :backgroundColor "blue"}}])
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderBottom style/standard-line :height 22}}]
                    (when (:expandedV4 @state) [:div {:style {:x "100" :y "20" :height 90 :backgroundColor "blue"}}])]
                   [:div {:style {:display "flex" :flexDirection "column" :backgroundColor "white" :width "25%" :borderRight "1px solid lightgray"}}
                    [:div {:style {:flex "0 1 auto" :backgroundColor "rgb(244, 244, 244)" :borderBottom style/standard-line :height 22 :textAlign "right"}} "20"]
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderBottom style/standard-line :height 22}}]
                    (when (:expandedV1 @state) [:div {:style {:x "100" :y "20" :height 90 :backgroundColor "blue"}}])
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderBottom style/standard-line :height 22}}]
                    (when (:expandedV2 @state) [:div {:style {:x "100" :y "20" :height 90 :backgroundColor "blue"}}])
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderBottom style/standard-line :height 22}}]
                    (when (:expandedV3 @state) [:div {:style {:x "100" :y "20" :height 90 :backgroundColor "blue"}}])
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderBottom style/standard-line :height 22}}]
                    (when (:expandedV4 @state) [:div {:style {:x "100" :y "20" :height 90 :backgroundColor "blue"}}])  ]
                   [:div {:style {:display "flex" :flexDirection "column" :backgroundColor "white" :width "25%" :borderRight "1px solid lightgray"}}
                    [:div {:style {:flex "0 1 auto" :backgroundColor "rgb(244, 244, 244)" :borderBottom style/standard-line :height 22 :textAlign "right"}} "30"]
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderBottom style/standard-line :height 22}}]
                    (when (:expandedV1 @state)[:div {:style {:x "100" :y "20" :height 90 :backgroundColor "blue"}}])
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderBottom style/standard-line :height 22}}]
                    (when (:expandedV2 @state) [:div {:style {:x "100" :y "20" :height 90 :backgroundColor "blue"}}])
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderBottom style/standard-line :height 22}}]
                    (when (:expandedV3 @state) [:div {:style {:x "100" :y "20" :height 90 :backgroundColor "blue"}}])
                    [:div {:style {:flex "0 1 auto" :backgroundColor "white" :borderBottom style/standard-line :height 22}}]
                    (when (:expandedV4 @state) [:div {:style {:x "100" :y "20" :height 90 :backgroundColor "blue"}}])]]

                  ])})



(defn- render-workflow-detail [workflow raw-data workflow-name submission-id bucketName]
       [:div {}
        [:div {:style {:padding         "1em" :border style/standard-line :borderRadius 4
                       :backgroundColor (:background-light style/colors)}}
         [:div {}
          (let [calls (workflow "calls")
                inputs (first (first (workflow "calls")))
                input-names (string/split inputs ".")
                workflow-name (first input-names)]
               (create-field "Workflow ID"
                             (style/create-link {:text   (workflow "id")
                                                 :target "_blank"
                                                 :style  {:color "-webkit-link" :textDecoration "underline"}
                                                 :href   (str moncommon/google-cloud-context
                                                              bucketName "/" submission-id "/"
                                                              workflow-name "/" (workflow "id") "/")})))
          (let [status (workflow "status")]
               (create-field "Status" (moncommon/icon-for-wf-status status) status))
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
              [CallDetail {:label call :data data :submission-id submission-id :bucketName bucketName :workflowId (workflow "id")}])]

        [:div {:style {:padding         "1em" :border style/standard-line :borderRadius 4 :marginTop 10
                       :backgroundColor (:background-light style/colors)}}
         [:div {} "Timing Diagram"]
         [WorkflowTimingNEW {}]
         ]])


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
                                        {:success?     success?
                                         :response     (if success? (get-parsed-response false) status-text)
                                         :raw-response raw-response}))}))})


(defn render [props]
      (assert (every? #(contains? props %) #{:workspace-id :submission-id :workflow-id}))
      [WorkflowDetails props])





