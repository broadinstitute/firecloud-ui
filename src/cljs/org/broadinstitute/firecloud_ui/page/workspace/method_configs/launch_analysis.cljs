(ns org.broadinstitute.firecloud-ui.page.workspace.method-configs.launch-analysis
  (:require
    [clojure.set :refer [union]]
    clojure.string
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.config :as config]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- entity->id [entity]
  {:type (entity "entityType") :name (entity "name")})

;; GAWB-662
;; show Queued first, then Launching, and collapse all remaining states into Active
(defn queue-status-remap [queue-status]
  [["Queued" (get queue-status "Queued" 0)]
   ["Launching" (get queue-status "Launching" 0)]
   ["Active" (apply + (vals (dissoc queue-status "Queued" "Launching")))]])

(defn queue-status-table-row [[status count]]
  [:div {}
   [:div {:style {:width 100 :float "right"}} status]
   [:div {:style {:marginRight 10 :float "right"}} count]
   (common/clear-both)])

(defn queue-status-table [state]
  (let [error-msg (:submission-queue-error-message @state)
        queue-status (:submission-queue-status @state)]
    [:div {:style {:marginBottom "1em" :float "right"}}
     (cond
       error-msg (style/create-server-error-message error-msg)
       (not queue-status) [comps/Spinner {:text "Loading submission queue status..."}]
       :else
       [:div {}
        "Queue Status: "
        (map queue-status-table-row queue-status)])]))

(defn- render-form [state props]
  [:div {}
   (when (:launching? @state)
     [comps/Blocker {:banner "Launching analysis..."}])
   (style/create-form-label "Select Entity")
   [:div {:style {:backgroundColor "#fff" :border style/standard-line
                  :padding "1em" :marginBottom "0.5em"}}
    [:div {:style {:marginBottom "1em" :fontSize "140%" :float "left"}}
     (str "Selected: "
       (if-let [e (:selected-entity @state)]
         (str (:name e) " (" (:type e) ")")
         "None"))]
    (queue-status-table state)
    (common/clear-both)
    [table/EntityTable
     {:workspace-id (:workspace-id props)
      :initial-entity-type (:root-entity-type props)
      :row-style (fn [row-index row-data]
                   {:backgroundColor
                    (cond (= (entity->id (first row-data)) (:selected-entity @state)) "yellow"
                      (even? row-index) (:background-gray style/colors)
                      :else "#fff")})
      :entity-name-renderer (fn [e]
                              (let [entity-id (entity->id e)]
                                (style/create-link {:text (:name entity-id)
                                                    :onClick #(swap! state assoc
                                                                     :selected-entity entity-id
                                                                     :workflow-count (common/count-workflows e))})))}]]
   (style/create-form-label "Define Expression")
   (let [disabled (= (:root-entity-type props) (get-in @state [:selected-entity :type]))]
     (style/create-text-field {:placeholder "leave blank for default"
                               :style {:width "100%"
                                       :backgroundColor (when disabled (:background-gray style/colors))}
                               :disabled disabled
                               :value (if disabled
                                        "Disabled - selected entity is of root entity type"
                                        (:expression @state))
                               :onChange #(let [text (-> % .-target .-value clojure.string/trim)]
                                            (swap! state assoc :expression text))}))
   (when-let [wf-count (:workflow-count @state)]
     (when (> wf-count (config/workflow-count-warning-threshold))
       [:div {:style {:textAlign "center"}}
        [:div {:style {:display "inline-block" :margin "1em 0 -1em 0" :padding "0.5em"
                       :backgroundColor "white" :border style/standard-line :borderRadius 3}}
         (icons/font-icon {:style {:color (:exception-red style/colors) :marginRight 5 :verticalAlign "middle"}}
           :status-warning-triangle)
         "Warning: This will launch " wf-count " workflows"]]))
   (style/create-validation-error-message (:validation-errors @state))
   [comps/ErrorViewer {:error (:launch-server-error @state)}]])

(react/defc Form
  {:render
   (fn [{:keys [props state this]}]
     [dialog/OKCancelForm
      {:header "Launch Analysis"
       :dismiss-self (:dismiss-self props)
       :content (render-form state props)
       :ok-button
       [comps/Button {:text "Launch" :disabled? (:disabled? props)
                      :onClick #(react/call :launch this)}]}])
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/submissions-queue-status)
        :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                   (if success?
                     (swap! state assoc :submission-queue-status (queue-status-remap (get-parsed-response)))
                     (swap! state assoc :submission-queue-error-message status-text)))}))
   :launch
   (fn [{:keys [props state]}]
     (if-let [entity (:selected-entity @state)]
       (let [config-id (:config-id props)
             expression (:expression @state)
             payload (merge {:methodConfigurationNamespace (:namespace config-id)
                             :methodConfigurationName (:name config-id)
                             :entityType (:type entity)
                             :entityName (:name entity)}
                       (when-not (clojure.string/blank? expression) {:expression expression}))]
         (swap! state assoc :launching? true :launch-server-error nil)
         (endpoints/call-ajax-orch
           {:endpoint (endpoints/create-submission (:workspace-id props))
            :payload payload
            :headers {"Content-Type" "application/json"}
            :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                       (swap! state dissoc :launching?)
                       (if success?
                         ((:on-success props) ((get-parsed-response) "submissionId"))
                         (swap! state assoc :launch-server-error (get-parsed-response))))}))
       (swap! state assoc :validation-errors ["Please select an entity"])))})


(react/defc LaunchAnalysisButton
  {:render
   (fn [{:keys [props state]}]
     [:span {}
      (when (:display-modal? @state)
        [dialog/Dialog {:width "80%"
                        :dismiss-self #(swap! state dissoc :display-modal?)
                        :content (react/create-element
                                   [Form
                                    (merge
                                      (select-keys props [:config-id :workspace-id :root-entity-type :on-success])
                                      {:dismiss-self #(swap! state dissoc :display-modal?)})])}])
      [comps/Button {:text "Launch Analysis..."
                     :disabled? (when (:disabled? props) "The workspace is locked")
                     :onClick #(do (common/scroll-to-top 100)
                                   (swap! state assoc :display-modal? true))}]])})


(defn render-button [props]
  (react/create-element LaunchAnalysisButton props))
