(ns org.broadinstitute.firecloud-ui.page.workspace.method-configs.launch-analysis
  (:require
    [clojure.set :refer [union]]
    clojure.string
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.sign-in :as sign-in]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.entity-table :refer [EntityTable]]
    [org.broadinstitute.firecloud-ui.config :as config]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- entity->id [entity]
  {:type (:entityType entity) :name (:name entity)})


(defn- row [label content]
  [:div {}
   [:div {:style {:display "inline-block" :width 200 :textAlign "right" :marginRight "1ex"}} label]
   [:div {:style {:display "inline-block" :width 240}} content]])

(defn queue-status-table [state]
  (let [{:keys [queue-status queue-error]} @state
        {:keys [queue-time queue-position queued active]} queue-status]
    [:div {:style {:marginBottom "1em" :float "right"}}
     (cond
       queue-error (style/create-server-error-message queue-error)
       (not queue-status) [comps/Spinner {:text "Loading submission queue status..."}]
       :else
       [:div {}
        (row "Estimated wait time:" (.humanize (js/moment.duration queue-time)))
        (row "Workflows ahead of yours:" queue-position)
        (row "Queue status:" (str queued " Queued; " active " Active"))])]))

(defn- render-form [state props]
  [:div {:style {:width 1000}}
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
    [EntityTable
     {:workspace-id (:workspace-id props)
      :initial-entity-type (:root-entity-type props)
      :row-style (fn [row-index row-data]
                   {:backgroundColor
                    (cond (= (entity->id (first row-data)) (:selected-entity @state)) "yellow"
                      (even? row-index) (:background-light style/colors)
                      :else "#fff")})
      :entity-name-renderer (fn [e]
                              (let [entity-id (entity->id e)]
                                (style/create-link
                                  {:text (:name entity-id)
                                   :onClick #(swap! state assoc
                                                    :selected-entity entity-id
                                                    :workflow-count (common/count-workflows
                                                                      e (:root-entity-type props)))})))}]]
   (style/create-form-label "Define Expression")
   (let [disabled (= (:root-entity-type props) (get-in @state [:selected-entity :type]))]
     (style/create-text-field {:placeholder "leave blank for default"
                               :style {:width "100%"
                                       :backgroundColor (when disabled (:background-light style/colors))}
                               :disabled disabled
                               :value (if disabled
                                        "Disabled - selected entity is of root entity type"
                                        (:expression @state))
                               :onChange #(let [text (-> % .-target .-value clojure.string/trim)]
                                            (swap! state assoc :expression text))}))
   (when-let [wf-count (:workflow-count @state)]
     (when (> wf-count (config/workflow-count-warning-threshold))
       [:div {:style {:textAlign "center"}}
        [:div {:style {:display "inline-flex" :alignItems "center" :margin "1em 0 -1em 0" :padding "0.5em"
                       :backgroundColor "white" :border style/standard-line :borderRadius 3}}
         (icons/icon {:style {:color (:exception-state style/colors) :marginRight 5 :verticalAlign "middle"}}
                     :warning-triangle)
         (str "Warning: This will launch " wf-count " workflows")]]))
   [:div {:style {:textAlign "right" :fontSize "80%"}}
    (style/create-link {:text  (str "Cromwell Version: " (:cromwell-version props))
                        :target "_blank"
                        :href (str "https://github.com/broadinstitute/cromwell/releases/tag/"
                                   (:cromwell-version props))})]
   (style/create-validation-error-message (:validation-errors @state))
   [comps/ErrorViewer {:error (:launch-server-error @state)}]])

(react/defc Form
  {:render
   (fn [{:keys [props state this]}]
     [modal/OKCancelForm
      {:header "Launch Analysis"
       :content (render-form state props)
       :ok-button {:text "Launch" :disabled? (:disabled? props) :onClick #(react/call :launch this)}}])
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/submissions-queue-status)
        :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                   (if success?
                     (swap! state assoc :queue-status (common/queue-status-counts (get-parsed-response false)))
                     (swap! state assoc :queue-error status-text)))}))
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
            :headers utils/content-type=json
            :on-done (fn [{:keys [success? get-parsed-response]}]
                       (swap! state dissoc :launching?)
                       (if success?
                         (do (modal/pop-modal) ((:on-success props) ((get-parsed-response false) "submissionId")))
                         (swap! state assoc :launch-server-error (get-parsed-response false))))}))
       (swap! state assoc :validation-errors ["Please select an entity"])))})


(react/defc LaunchAnalysisButton
  {:render
   (fn [{:keys [props]}]
     [comps/Button {:text "Launch Analysis..."
                    :disabled? (:disabled? props)
                    :onClick #(if (:force-login? props)
                               (sign-in/show-sign-in-dialog :refresh-token (:after-login props))
                               (modal/push-modal [Form (select-keys props [:config-id :workspace-id :root-entity-type :on-success :cromwell-version])]))}])})


(defn render-button [props]
  (react/create-element LaunchAnalysisButton props))
