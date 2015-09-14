(ns org.broadinstitute.firecloud-ui.page.workspace.launch-analysis
  (:require
    [clojure.set :refer [union]]
    clojure.string
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    ))


(defn- entity->id [entity]
  {:type (entity "entityType") :name (entity "name")})


(react/defc LaunchButton
  {:render
   (fn [{:keys [this state]}]
     [:div {:style {:fontSize "106%" :lineHeight 1 :textAlign "center"}}
      [:div {:style {:padding "0.7em 0" :cursor "pointer"
                     :backgroundColor (:button-blue style/colors)
                     :color "#fff" :borderRadius 4
                     :border (str "1px solid " (:line-gray style/colors))}
             :onClick #(react/call :handle-click this)}
       "Launch"]
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
                         (on-success (get-in (get-parsed-response) [0 "submissionId"]))
                         (js/alert (str "Launch failed: " status-text))))}))
       (js/alert "Please select an entity.")))})


(defn- render-table [entities filter selected-entity on-entity-selected]
  (let [attribute-keys (apply union (map (fn [e] (set (keys (e "attributes")))) entities))]
    [table/Table
     {:key filter
      :empty-message "No entities available."
      :columns (concat
                [{:header "" :starting-width 40 :resizable? false :reorderable? false
                  :content-renderer (fn [i data]
                                      [:input {:type "radio"
                                               :checked (identical? data selected-entity)
                                               :onChange #(on-entity-selected data)}])}
                 {:header "Entity Type" :starting-width 100 :sort-by :value}
                 {:header "Entity Name" :starting-width 100 :sort-by :value}]
                (map (fn [k] {:header k :starting-width 100 :sort-by :value}) attribute-keys))
      :data (map (fn [m]
                   (concat
                    [m
                     (m "entityType")
                     (m "name")]
                    (map (fn [k] (get-in m ["attributes" k])) attribute-keys)))
                 entities)}]))


(defn render-form [entities state refs props]
  (let [entity-map (group-by #(% "entityType") entities)
        filter (or (:filter @state) "Sample")
        filtered-entities (entity-map filter)
        selected-entity (or (:selected-entity @state) (first filtered-entities))]
    [:div {:style {:padding "22px 48px 40px" :backgroundColor (:background-gray style/colors)}}
     (if (:launch-result @state)
       [:div {}
        (if (:launch-exception @state)
          (icons/font-icon {:style {:fontSize "200%" :color (:exception-red style/colors)}}
                           :status-warning-triangle)
          (icons/font-icon {:style {:fontSize "200%" :color (:success-green style/colors)}}
                           :status-done))
        [:pre {} (.stringify js/JSON (.parse js/JSON (:launch-result @state)) nil 2)]]
       [:div {}
        (style/create-form-label "Select Entity Type")
        (style/create-select
         {:style {:width "50%" :minWidth 50 :maxWidth 200} :ref "filter"
          :defaultValue (:root-entity-type props)
          :onChange #(let [value (-> (@refs "filter") .getDOMNode .-value)]
                       (swap! state assoc :filter value))}
         (keys entity-map))
        (style/create-form-label "Select Entity")
        [:div {:style {:backgroundColor "#fff" :border (str "1px solid " (:line-gray style/colors))
                       :padding "1em" :marginBottom "0.5em"}}
         (render-table filtered-entities filter selected-entity
                       #(swap! state assoc :selected-entity %))]
        (style/create-form-label "Define Expression")
        (style/create-text-field {:placeholder "leave blank for default"
                                  :value (:expression @state)
                                  :onChange #(swap! state assoc :expression (-> % .-target .-value))})
        [LaunchButton (merge props {:entity-id (when selected-entity (entity->id selected-entity))
                                    :expression (:expression @state)})]])]))


(react/defc Page
  {:render
   (fn [{:keys [props state refs]}]
     [:div {:style {:backgroundColor "white"}}
      [:div {:style {:borderBottom (str "1px solid " (:line-gray style/colors))
                     :padding "20px 48px 18px"
                     :fontSize "137%" :fontWeight 400 :lineHeight 1}}
       "Select Entity"]
      [:div {:style {:position "absolute" :top 4 :right 4}}
       [comps/Button {:icon :x :onClick #((:on-cancel props))}]]
      [:div {:style {:marginTop "1em" :minHeight "10em"}}
       (let [{:keys [server-response]} @state
             {:keys [entities error-message]} server-response]
         (cond
           (nil? server-response)
           [:div {:style {:textAlign "center"}}
            [comps/Spinner {:text "Loading data..."}]]
           error-message (style/create-server-error-message error-message)
           (zero? (count @state))
           (style/create-message-well "No data found.")
           :else
           [:div {:style {:marginTop "-1em"}}
            (render-form entities state refs props)]))]])
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-entities-by-type (:workspace-id props))
        :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                   (swap! state assoc
                     :server-response (if success?
                                        {:entities (get-parsed-response)}
                                        {:error-message status-text})))}))})


(react/defc ShowLaunchModalButton
  {:render
   (fn [{:keys [props state]}]
     [:span {}
      (when (:display-modal? @state)
        [comps/Dialog {:width "80%"
                       :dismiss-self #(swap! state dissoc :display-modal?)
                       :content (react/create-element
                                 Page
                                 (merge props
                                  {:on-cancel #(swap! state dissoc :display-modal?)}))}])
      [comps/Button {:text "Launch Analysis..."
                     :onClick #(swap! state assoc :display-modal? true)}]])})


(defn render-button [props]
  (react/create-element ShowLaunchModalButton props))
