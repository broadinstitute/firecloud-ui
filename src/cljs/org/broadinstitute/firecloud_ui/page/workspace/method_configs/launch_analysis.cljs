(ns org.broadinstitute.firecloud-ui.page.workspace.method-configs.launch-analysis
  (:require
    [clojure.set :refer [union]]
    clojure.string
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    ))


(defn- entity->id [entity]
  {:type (entity "entityType") :name (entity "name")})


(defn- render-form [state props]
  [:div {}
   (when (:launching? @state)
     [comps/Blocker {:banner "Launching analysis..."}])
   (style/create-form-label "Select Entity")
   [:div {:style {:backgroundColor "#fff" :border (str "1px solid " (:line-gray style/colors))
                  :padding "1em" :marginBottom "0.5em"}}
    [:div {:style {:marginBottom "1em" :fontSize "140%"}}
     (str "Selected: "
       (if-let [e (:selected-entity @state)]
         (str (:name e) " (" (:type e) ")")
         "None"))]
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
                                                    :onClick #(swap! state assoc :selected-entity entity-id)})))}]]
   (style/create-form-label "Define Expression")
   (let [disabled (= (:root-entity-type props) (get-in @state [:selected-entity :type]))]
     (style/create-text-field {:placeholder "leave blank for default"
                               :style {:width "100%"
                                       :backgroundColor (when disabled
                                                          (:background-gray style/colors))}
                               :disabled disabled
                               :value (if disabled
                                        "Disabled - selected entity is of root entity type"
                                        (:expression @state))
                               :onChange #(let [text (-> % .-target .-value clojure.string/trim)]
                                            (swap! state assoc :expression text))}))
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
