(ns org.broadinstitute.firecloud-ui.page.workspace.monitor.workflow-details
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
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


(defn- display-gcs-link [gcs-uri]
  (if-let [parsed (common/parse-gcs-uri gcs-uri)]
    [dialog/GCSFilePreviewLink (assoc parsed :gcs-uri gcs-uri :style-props {:style {:display "inline"}})]
    gcs-uri))


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
          (style/create-link
            #(swap! state assoc :expanded (not (:expanded @state)))
            (if (:expanded @state) "Hide" "Show"))))
      (when (:expanded @state)
        [:div {:style {:padding "0.25em 0 0.25em 1em"}}
         (for [[k v] (:data props)]
           [:div {} k [:span {:style {:margin "0 1em"}} "→"] (display-gcs-link v)])])])})


(react/defc CallDetail
  {:get-initial-state
   (fn []
     {:expanded false})
   :render
   (fn [{:keys [props state]}]
     [:div {:style {:marginTop "1em"}}
      [:div {:style {:display "inline-block" :marginRight "1em"}} (:label props)]
      (style/create-link
        #(swap! state assoc :expanded (not (:expanded @state)))
        (if (:expanded @state) "Hide" "Show"))
      (when (:expanded @state)
        (map-indexed
          (fn [index data]
            (org.broadinstitute.firecloud-ui.utils/jslog data)
            [:div {:style {:padding "0.5em 0 0 0.5em"}}
             [:div {:style {:paddingBottom "0.25em"}} (str "Call #" (inc index) ":")]
             [:div {:style {:paddingLeft "0.5em"}}
              (create-field "ID" (data "jobId"))
              (let [status (data "executionStatus")]
                (create-field "Status" (moncommon/icon-for-wf-status status) status))
              (create-field "Started" (moncommon/render-date (data "start")))
              (create-field "Ended" (moncommon/render-date (data "end")))
              (create-field "stdout" (display-gcs-link (data "stdout")))
              (create-field "stderr" (display-gcs-link (data "stderr")))
              [IODetail {:label "Inputs" :data (data "inputs")}]
              [IODetail {:label "Outputs" :data (data "outputs")}]]])
          (:data props)))])})


(defn- render-workflow-detail [workflow]
  [:div {:style {:padding "1em" :border (str "1px solid " (:line-gray style/colors)) :borderRadius 4
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
   [:div {:style {:marginTop "1em" :fontWeight 500}} "Calls:"]
   (for [[call data] (workflow "calls")]
     [CallDetail {:label call :data data}])])


(react/defc WorkflowDetails
  {:render
   (fn [{:keys [props state]}]
     (let [server-response (:server-response @state)]
       (cond
         (nil? server-response)
         [:div {} [comps/Spinner {:text "Loading workflow details..."}]]
         (not (:success? server-response))
         (style/create-server-error-message (:response server-response))
         :else
         (render-workflow-detail (:response server-response)))))
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
