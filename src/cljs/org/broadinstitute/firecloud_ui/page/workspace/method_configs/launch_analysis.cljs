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
     {:entities (:entities props)
      :entity-types (:entity-types props)
      :row-style (fn [row-index row-data]
                   {:backgroundColor
                    (cond (= (entity->id (first row-data)) (:selected-entity @state)) "yellow"
                      (even? row-index) (:background-gray style/colors)
                      :else "#fff")})
      :entity-name-renderer (fn [e]
                              (let [entity-id (entity->id e)]
                                (style/create-link
                                  #(swap! state assoc :selected-entity entity-id)
                                  (:name entity-id))))}]]
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
  {:get-initial-state
   (fn [{:keys [props]}]
     (let [types (:entity-types props)
           root (:root-entity-type props)]
       (if (contains? (set types) root)
         {:selected-entity (entity->id (first (filter #(= root (% "entityType")) (:entities props))))}
         {})))
   :render
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


(react/defc Page
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [server-response]} @state
           {:keys [entities entity-types error-response]} server-response]
       (cond
         entities [Form {:config-id (:config-id props)
                         :workspace-id (:workspace-id props)
                         :entities entities
                         :entity-types entity-types
                         :root-entity-type (:root-entity-type props)
                         :dismiss-self (:on-cancel props)
                         :on-success (:on-success props)}]
         error-response [comps/ErrorViewer {:error error-response}]
         :else [:div {:style {:textAlign "center" :padding "1em"}}
                [comps/Spinner {:text "Loading data..."}]])))
   :component-did-mount
   (fn [{:keys [props state]}]
     (common/scroll-to-top 100)
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-entities-by-type (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response]}]
                   (swap! state assoc
                     :server-response (if success?
                                        (let [entities (get-parsed-response)]
                                          {:entities entities
                                           :entity-types (distinct (map #(% "entityType") entities))})
                                        {:error-response (get-parsed-response)})))}))})


(react/defc ShowLaunchModalButton
  {:render
   (fn [{:keys [props state]}]
     [:span {}
      (when (:display-modal? @state)
        [dialog/Dialog {:width "80%"
                        :dismiss-self #(swap! state dissoc :display-modal?)
                        :content (react/create-element
                                   Page
                                   (merge props
                                     {:on-cancel #(swap! state dissoc :display-modal?)}))}])
      [comps/Button {:text "Launch Analysis..."
                     :disabled? (when (:disabled? props) "The workspace is locked")
                     :onClick #(swap! state assoc :display-modal? true)}]])})


(defn render-button [props]
  (react/create-element ShowLaunchModalButton props))
