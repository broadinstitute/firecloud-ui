(ns org.broadinstitute.firecloud-ui.page.workspace.workspace-data
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.page.import-data :as import-data]
    [org.broadinstitute.firecloud-ui.paths :refer [list-all-entities-path]]
    [org.broadinstitute.firecloud-ui.utils :as utils]))


(defn- filter-entities [entities active-filter]
  (case active-filter
    :sample (filter (fn [entity] (= "sample" (entity "entityType"))) entities)
    :aliquot (filter (fn [entity] (= "aliquot" (entity "entityType"))) entities)
    entities))

(defn- create-mock-entities []
  (map
    (fn [i]
      {:entityType (rand-nth ["sample" "participant"])
       :name (str "entity" (inc i))
       :attributes (str "entity" (inc i) " has no attributes")})
    (range (rand-int 20))))

(react/defc EntitiesList
  {:get-initial-state
   (fn []
     {:active-filter :sample})
   :render
   (fn [{:keys [props state]}]
     (let [filtered-entities (filter-entities (:entities props) (:active-filter @state))
           make-button (fn [name filter]
                         {:text name
                          :active? (= filter (:active-filter @state))
                          :onClick #(swap! state assoc :active-filter filter)})]
       [:div {}
        [:div {:style {:margin "2em 0" :textAlign "center"}}
         [comps/FilterButtons {:buttons [(make-button "Sample" :sample)
                                         (make-button "Aliquot" :aliquot)
                                         (make-button "All" :all)]}]]
        (if (zero? (count filtered-entities))
          (style/create-message-well "No entities to display.")
          [table/Table
           {:columns [{:header "Entity Type" :starting-width 100}
                      {:header "Entity Name" :starting-width 100}
                      {:header "Attributes" :starting-width 400}]
            :data (map (fn [m]
                         [(m "entityType")
                          (m "name")
                          (m "attributes")])
                    filtered-entities)}])]))})


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
                            {:entities-loaded? false}))}
           "< Back to Data List"]]
         [import-data/Page {:ref "data-import"
                            :workspace-id {:namespace (get-in props [:workspace "namespace"])
                                           :name (get-in props [:workspace "name"])}}]]
        (:entities-loaded? @state) [EntitiesList {:entities (:entities @state)}]
        (:error @state) (style/create-server-error-message (get-in @state [:error :message]))
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
     (when-not (or (:entities-loaded? @state) (:error @state))
       (react/call :load-entities this)))
   :load-entities
   (fn [{:keys [state props]}]
     (utils/ajax-orch
       (list-all-entities-path (:workspace props) "sample")
       {:on-done (fn [{:keys [success? xhr]}]
                   (if success?
                     (swap! state assoc
                       :entities-loaded? true
                       :entities (utils/parse-json-string (.-responseText xhr)))
                     (swap! state assoc :error {:message (.-statusText xhr)})))
        :canned-response {:responseText (utils/->json-string (create-mock-entities))
                          :status 200
                          :delay-ms (rand-int 2000)}}))})

(defn render-workspace-data [workspace]
  [WorkspaceData {:workspace workspace}])
