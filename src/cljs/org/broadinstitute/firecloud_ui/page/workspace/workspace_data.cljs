(ns org.broadinstitute.firecloud-ui.page.workspace.workspace-data
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.style :as style]
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
  {:render
   (fn [{:keys [props]}]
     (let [filtered-entities (filter-entities (:entities props) (:active-filter props))]
       [:div {:style {:padding "0 4em"}}
        (if (zero? (count filtered-entities))
          [:div {:style {:textAlign "center" :backgroundColor (:background-gray style/colors)
                         :padding "1em 0" :borderRadius 8}}
           "No entities to display."]
          [table/Table
           (let [cell-style {:flexBasis "8ex" :flexGrow 1 :whiteSpace "nowrap" :overflow "hidden"
                             :borderLeft (str "1px solid " (:line-gray style/colors))}
                 header-label (fn [text & [padding]]
                                [:span {:style {:paddingLeft (or padding "1em")}}
                                 [:span {:style {:fontSize "90%"}} text]])]
             {:columns [{:label (header-label "Entity Type")
                         :style (merge cell-style {:borderLeft "none"})}
                        {:label (header-label "Entity Name")
                         :style cell-style
                         :header-style {:borderLeft "none"}}
                        {:label (header-label "Attributes")
                         :style (merge cell-style {:flexBasis "30ex"})
                         :header-style {:borderLeft "none"}}]
              :data (map (fn [m]
                           [(m "entityType")
                            (m "name")
                            (m "attributes")])
                      filtered-entities)})])]))})


(react/defc WorkspaceData
  {:get-initial-state
   (fn []
     {:active-filter :sample})
   :render
   (fn [{:keys [state props]}]
     (let [make-button (fn [name filter]
                         {:text name
                          :active? (= filter (:active-filter @state))
                          :onClick #(swap! state assoc :active-filter filter)})]
       [:div {:style {:padding "2em 0" :textAlign "center"}}
        [comps/FilterButtons {:buttons [(make-button "Sample" :sample)
                                        (make-button "Aliquot" :aliquot)
                                        (make-button "All" :all)]}]

        [:div {:style {:padding "2em 0"}}
         (cond
           (:entities-loaded? @state) [EntitiesList {:entities (:entities @state) :active-filter (:active-filter @state)}]
           (:error-message @state) [:div {:style {:color "red"}}
                                    "FireCloud service returned error: " (:error-message @state)]
           :else [comps/Spinner {:text "Loading entities..."}])]]))
    :component-did-mount
    (fn [{:keys [state props]}]
      (utils/ajax-orch
        (str "/workspaces/" (get-in props [:workspace "namespace"]) "/" (get-in props [:workspace "name"]) "/entities/sample")
        {:on-done (fn [{:keys [success? xhr]}]
                    (if success?
                      (swap! state assoc :entities-loaded? true :entities (utils/parse-json-string (.-responseText xhr)))
                      (swap! state assoc :error-message (.-statusText xhr))))
         :canned-response {:responseText (utils/->json-string (create-mock-entities))
                           :status 200
                           :delay-ms (rand-int 2000)}}))})

(defn render-workspace-data [workspace entities]
  [WorkspaceData {:workspace workspace :entities entities}])
