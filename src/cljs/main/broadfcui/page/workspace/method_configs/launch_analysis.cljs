(ns broadfcui.page.workspace.method-configs.launch-analysis
  (:require
   [clojure.string :as string]
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.duration :as duration]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.common.entity-table :refer [EntityTable]]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
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
        (row "Estimated wait time:" (duration/fuzzy-time-from-now-ms (+ (.now js/Date) queue-time) false))
        (row "Workflows ahead of yours:" queue-position)
        (row "Queue status:" (str queued " Queued; " active " Active"))])]))

(defn- render-form [state props]
  [:div {}
   (when (:launching? @state)
     [comps/Blocker {:banner "Launching analysis..."}])
   (style/create-form-label "Select Entity")
   [:div {:style {:backgroundColor "#fff" :border style/standard-line
                  :padding "1em" :marginBottom "0.5em"}}
    [:div {:style {:marginBottom "1em" :fontSize "140%" :float "left"}
           :data-test-id "selected-entity"}
     (str "Selected: "
          (if-let [e (:selected-entity @state)]
            (str (:name e) " (" (:type e) ")")
            "None"))]
    (queue-status-table state)
    (common/clear-both)
    [EntityTable
     {:workspace-id (:workspace-id props)
      :initial-entity-type (:root-entity-type props)
      :style {:body-row (fn [{:keys [index row]}]
                          {:backgroundColor
                           (cond (= (entity->id row) (:selected-entity @state)) "yellow"
                                 (even? index) (:background-light style/colors)
                                 :else "#fff")
                           :cursor "pointer"})}
      :on-row-click (fn [_ entity]
                      (let [entity-id (entity->id entity)]
                        (swap! state assoc
                               :selected-entity entity-id
                               :workflow-count (common/count-workflows
                                                entity (:root-entity-type props)))))}]]
   (style/create-form-label "Define Expression")
   (let [disabled (= (:root-entity-type props) (get-in @state [:selected-entity :type]))]
     (style/create-text-field {:placeholder "leave blank for default"
                               :style {:width "100%"
                                       :backgroundColor (when disabled (:background-light style/colors))}
                               :disabled disabled
                               :value (if disabled
                                        "Disabled - selected entity is of root entity type"
                                        (:expression @state))
                               :data-test-id "define-expression-input"
                               :onChange #(let [text (-> % .-target .-value string/trim)]
                                            (swap! state assoc :expression text))}))
   [:div {:style {:marginTop "1em"}}
    [comps/Checkbox
     {:ref "callCache-check"
      :label [:span {:data-test-id "call-cache-text" :style {:marginBottom "0.8em"}} "Use Call Caching "
              (links/create-external {:href (config/call-caching-guide-url)} "Learn about call caching")]
      :data-test-id "call-cache-checkbox"
      :initial-checked? true
      :disabled-text (case (:protected-option @state)
                       :not-loaded "Call Caching status has not finished loading."
                       :not-available "This option is not available for your account."
                       nil)}]]
   (when-let [wf-count (:workflow-count @state)]
     (when (> wf-count (config/workflow-count-warning-threshold))
       [:div {:style {:textAlign "center"}}
        [:div {:style {:display "inline-flex" :alignItems "center" :margin "1em 0 -1em 0" :padding "0.5em"
                       :backgroundColor "white" :border style/standard-line :borderRadius 3}
               :data-test-id "number-of-workflows-warning"}
         (icons/icon {:style {:color (:exception-state style/colors) :marginRight 5 :verticalAlign "middle"}}
                     :warning)
         (str "Warning: This will launch " wf-count " workflows")]]))
   [:div {:style {:textAlign "right" :fontSize "80%"}}
    (links/create-external {:href (str "https://github.com/broadinstitute/cromwell/releases/tag/"
                                       (:cromwell-version @state))}
                           (str "Cromwell Version: " (:cromwell-version @state)))]
   (style/create-validation-error-message (:validation-errors @state))
   [comps/ErrorViewer {:error (:launch-server-error @state)}]])

(defn- parse-cromwell-ver [cromwell-ver-response]
  (first (string/split (:cromwell cromwell-ver-response) #"-")))

(react/defc- Form
  {:render
   (fn [{:keys [props state this]}]
     [comps/OKCancelForm
      {:header "Launch Analysis"
       :content (react/create-element (render-form state props))
       :ok-button {:text "Launch" :disabled? (:disabled? props) :onClick #(react/call :launch this) :data-test-id "launch-button"}
       :data-test-id "launch-analysis-modal"}])
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/submissions-queue-status)
       :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                  (if success?
                    (swap! state assoc :queue-status (common/queue-status-counts (get-parsed-response false)))
                    (swap! state assoc :queue-error status-text)))})
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/cromwell-version)
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (when success?
                    (swap! state assoc :cromwell-version (parse-cromwell-ver (get-parsed-response)))))}))
   :launch
   (fn [{:keys [props state refs]}]
     (if-let [entity (:selected-entity @state)]
       (let [config-id (:config-id props)
             expression (:expression @state)
             payload (merge {:methodConfigurationNamespace (:namespace config-id)
                             :methodConfigurationName (:name config-id)
                             :entityType (:type entity)
                             :entityName (:name entity)
                             :useCallCache (react/call :checked? (@refs "callCache-check"))}
                            (when-not (string/blank? expression) {:expression expression}))]
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


(react/defc- LaunchAnalysisButton
  {:render
   (fn [{:keys [props]}]
     [buttons/Button
      {:data-test-id "open-launch-analysis-modal-button"
       :text "Launch Analysis..."
       :disabled? (:disabled? props)
       :onClick #(modal/push-modal
                  [Form (select-keys props [:config-id :workspace-id
                                            :root-entity-type :on-success :cromwell-version])])}])})


(defn render-button [props]
  (react/create-element LaunchAnalysisButton props))
