(ns broadfcui.page.workspace.method-configs.launch-analysis
  (:require
   [clojure.string :as string]
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.entity-table :refer [EntityTable]]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.checkbox :refer [Checkbox]]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.queue-status :refer [QueueStatus]]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.workspace.data.utils :as data-utils]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   ))


(defn- entity->id [entity]
  {:type (:entityType entity) :name (:name entity)})

(defn- render-form [state props]
  [:div {:style {:width "80vw"}}
   (when (:launching? @state)
     (blocker "Launching analysis..."))
   (style/create-form-label "Select Entity")
   [:div {:style {:backgroundColor "#fff" :border style/standard-line
                  :padding "1em" :marginBottom "0.5em"}}
    [:div {:style {:display "flex" :alignItems "flex-end"}}
     [:div {:style {:marginBottom "1em" :fontSize "140%"}
            :data-test-id "selected-entity"}
      "Selected: "
      (if-let [e (:selected-entity @state)]
        (str (:name e) " (" (:type e) ")")
        "None")]
     flex/spring
     [QueueStatus]]
    (let [set-entity (fn [entity]
                       (swap! state assoc
                              :selected-entity (entity->id entity)
                              :workflow-count (common/count-workflows
                                               entity (:root-entity-type props))))]
      [EntityTable
       {:workspace-id (:workspace-id props)
        :initial-entity-type (:root-entity-type props)
        :column-defaults
        (data-utils/get-column-defaults (:column-defaults props))
        :entity-name-renderer (fn [{:keys [name entityName] :as entity}]
                                (let [entity-name (or name entityName)]
                                  (links/create-internal
                                    {:data-test-id (str entity-name "-link")
                                     :onClick #(set-entity entity)}
                                    entity-name)))
        :style {:body-row (fn [{:keys [index row]}]
                            {:backgroundColor
                             (cond (= (entity->id row) (:selected-entity @state)) (:tag-background style/colors)
                                   (even? index) (:background-light style/colors)
                                   :else "#fff")
                             :cursor "pointer"})}
        :on-row-click (fn [_ entity] (set-entity entity))}])]
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
    [Checkbox
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
         (icons/render-icon {:style {:color (:state-exception style/colors) :marginRight 5 :verticalAlign "middle"}}
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
     [modals/OKCancelForm
      {:data-test-id "launch-analysis-modal"
       :header "Launch Analysis"
       :content (react/create-element (render-form state props))
       :ok-button {:text "Launch" :disabled? (:disabled? props) :onClick #(this :launch) :data-test-id "launch-button"}
       :dismiss (:dismiss props)}])
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/get-cromwell-version
      (fn [{:keys [success? get-parsed-response]}]
        (when success?
          (swap! state assoc :cromwell-version (parse-cromwell-ver (get-parsed-response)))))))
   :launch
   (fn [{:keys [props state refs]}]
     (if-let [entity (:selected-entity @state)]
       (let [config-id (:config-id props)
             expression (:expression @state)
             payload (merge {:methodConfigurationNamespace (:namespace config-id)
                             :methodConfigurationName (:name config-id)
                             :entityType (:type entity)
                             :entityName (:name entity)
                             :useCallCache ((@refs "callCache-check") :checked?)}
                            (when-not (string/blank? expression) {:expression expression}))]
         (utils/multi-swap! state (assoc :launching? true) (dissoc :launch-server-error))
         (endpoints/call-ajax-orch
          {:endpoint (endpoints/create-submission (:workspace-id props))
           :payload payload
           :headers ajax/content-type=json
           :on-done (fn [{:keys [success? get-parsed-response]}]
                      (swap! state dissoc :launching?)
                      (if success?
                        ((:on-success props) (:submissionId (get-parsed-response)))
                        (swap! state assoc :launch-server-error (get-parsed-response false))))}))
       (swap! state assoc :validation-errors ["Please select an entity"])))})


(react/defc LaunchAnalysisButton
  {:render
   (fn [{:keys [props state]}]
     [:div {}
      (when (:show-modal? @state)
        [Form (assoc (select-keys props [:config-id :workspace-id :column-defaults
                                         :root-entity-type :on-success :cromwell-version])
                :dismiss #(swap! state dissoc :show-modal?))])
      [buttons/Button
       {:data-test-id "open-launch-analysis-modal-button"
        :text "Launch Analysis..."
        :disabled? (:disabled? props)
        :onClick #(swap! state assoc :show-modal? true)}]])})
