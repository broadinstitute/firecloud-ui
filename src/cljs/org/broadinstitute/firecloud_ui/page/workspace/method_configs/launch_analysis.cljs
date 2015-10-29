(ns org.broadinstitute.firecloud-ui.page.workspace.method-configs.launch-analysis
  (:require
    [clojure.set :refer [union]]
    clojure.string
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    ))


(defn- entity->id [entity]
  {:type (entity "entityType") :name (entity "name")})


(react/defc LaunchButton
  {:render
   (fn [{:keys [this state]}]
     [:div {:style {:display "inline-block"}}
      [comps/Button {:text "Launch" :onClick #(react/call :handle-click this)}]
      (when (:launching? @state)
        [comps/Blocker {:banner "Launching analysis..."}])])
   :handle-click
   (fn [{:keys [props state]}]
     (if-let [entity-id (:entity-id props)]
       (let [config-id (:config-id props)
             expression (:expression props)
             payload (merge {:methodConfigurationNamespace (:namespace config-id)
                             :methodConfigurationName (:name config-id)
                             :entityType (:type entity-id)
                             :entityName (:name entity-id)}
                            (when-not (clojure.string/blank? expression) {:expression expression}))
             on-success (:on-success props)]
         (swap! state assoc :launching? true)
         (endpoints/call-ajax-orch
           {:endpoint (endpoints/create-submission (:workspace-id props))
            :payload payload
            :headers {"Content-Type" "application/json"}
            :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                       (swap! state dissoc :launching?)
                       (if success?
                         (on-success ((get-parsed-response) "submissionId"))
                         (js/alert (str "Launch failed: " status-text))))}))
       (js/alert "Please select an entity.")))})


(react/defc LaunchForm
  {:get-initial-state
   (fn [{:keys [props]}]
     (let [types (:entity-types props)
           root (:root-entity-type props)]
       (if (contains? (set types) root)
         (let [entity (first (filter #(= root (% "entityType")) (:entities props)))]
           ((:entity-selected props) (entity->id entity))
           {:selected-entity (first (filter #(= root (% "entityType")) (:entities props)))
            :selected-entity-type root})
         {:selected-entity-type (first types)})))
   :render
   (fn [{:keys [props state]}]
     [:div {}
      (style/create-form-label "Select Entity")
      [:div {:style {:backgroundColor "#fff" :border (str "1px solid " (:line-gray style/colors))
                     :padding "1em" :marginBottom "0.5em"}}
       (let [attribute-keys (apply
                             union
                             (map #(set (keys (% "attributes")))
                                  (filter #(= (% "entityType") (:selected-entity-type @state))
                                          (:entities props))))
             types (:entity-types props)
             root (:root-entity-type props)
             ordered-types (if (contains? (set types) root)
                             (cons root (remove #(= % root) types))
                             types)]
         [table/Table
          {:key (:selected-entity-type @state)
           :empty-message "No entities available."
           :columns (concat
                     [{:header "" :starting-width 40 :resizable? false :reorderable? false
                       :sort-by :none :filter-by :none
                       :content-renderer
                       (fn [data]
                         [:input {:type "radio"
                                  :checked (identical? data (:selected-entity @state))
                                  :onChange #(do (swap! state assoc :selected-entity data)
                                                 ((:entity-selected props) (entity->id data)))}])}
                      {:header "Entity Type" :starting-width 100}
                      {:header "Entity Name" :starting-width 100}]
                     (map (fn [k] {:header k :starting-width 100}) attribute-keys))
           :filters (mapv (fn [key] {:text key :pred #(= key (% "entityType"))}) ordered-types)
           :selected-filter-index (.indexOf (to-array ordered-types)
                                            (:selected-entity-type @state))
           :on-filter-change #(swap! state assoc
                                     :selected-entity-type (nth ordered-types %))
           :data (:entities props)
           :->row (fn [m]
                    (concat
                     [m
                      (m "entityType")
                      (m "name")]
                     (map (fn [k] (get-in m ["attributes" k])) attribute-keys)))}])]
      (style/create-form-label "Define Expression")
      (let [disabled (= (:root-entity-type props) (:selected-entity-type @state))]
        (style/create-text-field {:placeholder "leave blank for default"
                                  :style {:width "100%"
                                          :backgroundColor (when disabled
                                                             (:background-gray style/colors))}
                                  :disabled disabled
                                  :value (if disabled
                                           "Disabled - selected entity is of root entity type"
                                           (:expression @state))
                                  :onChange #(let [text (-> % .-target .-value clojure.string/trim)]
                                              (swap! state assoc :expression text)
                                              ((:expression-selected props) text))}))])})


(react/defc Page
  {:render
   (fn [{:keys [props state]}]
     [dialog/OKCancelForm
      {:header "Launch Analysis"
       :dismiss-self (:on-cancel props)
       :content
       (let [{:keys [server-response]} @state
             {:keys [entities entity-types error-message]} server-response]
         (cond
           (nil? server-response)
           [:div {:style {:textAlign "center" :background "#fff" :padding "1em"}}
            [comps/Spinner {:text "Loading data..."}]]
           error-message (style/create-server-error-message error-message)
           (zero? (count @state))
           (style/create-message-well "No data found.")
           :else
           [LaunchForm {:on-success (:on-success props)
                        :config-id (:config-id props)
                        :workspace-id (:workspace-id props)
                        :entities entities
                        :entity-types entity-types
                        :root-entity-type (:root-entity-type props)
                        :entity-selected #(swap! state assoc :selected-entity-id %)
                        :expression-selected #(swap! state assoc :selected-expression %)}]))
       :ok-button
       [LaunchButton (merge props
                       {:entity-id (:selected-entity-id @state)
                        :expression (when-not (= (:root-entity-type props)
                                                 (:type (:selected-entity-id @state)))
                                      (:selected-expression @state))})]}])
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-entities-by-type (:workspace-id props))
        :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                   (swap! state assoc
                     :server-response (if success?
                                        (let [entities (get-parsed-response)]
                                          {:entities entities
                                           :entity-types (distinct (map #(% "entityType") entities))})
                                        {:error-message status-text})))}))})


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
