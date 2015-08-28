(ns org.broadinstitute.firecloud-ui.page.workspace.data-tab
  (:require
    [dmohs.react :as react]
    [clojure.set :refer [union]]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.page.import-data :as import-data]
    [org.broadinstitute.firecloud-ui.paths :as paths]
    [org.broadinstitute.firecloud-ui.utils :as utils]))


(defn- create-mock-entities []
  (map
    (fn [i]
      {:entityType (rand-nth ["sample" "participant"])
       :name (str "entity" (inc i))
       :attributes {}})
    (range (rand-int 20))))

(react/defc EntitiesList
  {:get-initial-state
   (fn [{:keys [props]}]
     {:entities (get (:entity-map props) (first (keys (:entity-map props))))})
   :render
   (fn [{:keys [props state refs]}]
     [:div {}
      [:div {:style {:padding "0 0 0.5em 1em"}}
       (style/create-form-label "Select Entity Type")
       (style/create-select
         {:style {:width "50%" :minWidth 50 :maxWidth 200} :ref "filter"
          :onChange #(let [value (-> (@refs "filter") .getDOMNode .-value)
                           entities (get-in props [:entity-map value])]
                      (swap! state assoc :entities entities :entity-type value))}
         (keys (:entity-map props)))]
      (if (zero? (count (:entities @state)))
        (style/create-message-well "No entities to display.")
        (let [attribute-keys (apply union (map (fn [e] (set (keys (e "attributes")))) (:entities @state)))]
          [table/Table
           {:key (:entity-type @state)
            :columns (concat
                       [{:header "Entity Type" :starting-width 100 :sort-by :value}
                        {:header "Entity Name" :starting-width 100 :sort-by :value}]
                       (map (fn [k] {:header k :starting-width 100 :sort-by :value}) attribute-keys))
            :data (map (fn [m]
                         (concat
                           [(m "entityType")
                            (m "name")]
                           (map (fn [k] (get-in m ["attributes" k])) attribute-keys)))
                    (:entities @state))}]))])})


(react/defc WorkspaceData
  {:render
   (fn [{:keys [props state refs]}]
     [:div {:style {:marginTop "1em"}}
      (cond
        (:show-import? @state)
        [:div {:style {:margin "1em 0 0 2em"}}
         [:div {}
          [:a {:href "javascript:;"
               :style {:textDecoration "none"}
               :onClick #(swap! state merge
                          {:show-import? false}
                          (when (react/call :did-load-data? (@refs "data-import"))
                            {:entity-map false}))}
           "< Back to Data List"]]
         [import-data/Page {:ref "data-import" :workspace-id (:workspace-id props)}]]
        (:entity-map @state) [EntitiesList {:entity-map (:entity-map @state)}]
        (:error @state) (style/create-server-error-message (:error @state))
        :else [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading entities..."}]])
      (when-not (:show-import? @state)
        [:div {:style {:margin "1em 0 0 1em"}}
         [comps/Button {:text "Import Data..."
                        :onClick #(swap! state assoc :show-import? true)}]])])
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-entities this))
   :component-did-update
   (fn [{:keys [this state]}]
     (when-not (or (:entity-map @state) (:error @state))
       (react/call :load-entities this)))
   :load-entities
   (fn [{:keys [state props]}]
     (utils/call-ajax-orch
       (paths/get-entities-by-type (:workspace-id props))
       {:on-success (fn [{:keys [parsed-response]}]
                      (swap! state assoc :entity-map (group-by #(% "entityType") parsed-response)))
        :on-failure (fn [{:keys [status-text]}]
                      (swap! state assoc :error status-text))
        :mock-data (create-mock-entities)}))})

(defn render [workspace]
  [WorkspaceData {:workspace-id workspace}])
