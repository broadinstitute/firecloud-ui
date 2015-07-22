(ns org.broadinstitute.firecloud-ui.page.workspace.workspace-data
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.style :as style]))


(defn- contains-text [text fields]
  (fn [entity]
    (some #(not= -1 (.indexOf (entity %) text)) fields)))

(defn- filter-entities [entities fields text]
  (filter (contains-text text fields) entities))


(react/defc EntitiesList
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:padding "0 4em"}}
      (if (zero? (count (:entities props)))
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
                         [(m "entityType") ;;properly map to entities after
                          (m "name")
                          (m "attributes")])
                    (:entities props))})])])})


(react/defc EntityFilterButtons
  (let [Button
        (react/create-class
          {:render
           (fn [{:keys [props]}]
             [:div {:style {:float "left"
                            :backgroundColor (if (:active? props)
                                               (:button-blue style/colors)
                                               (:background-gray style/colors))
                            :color (when (:active? props) "white")
                            :marginLeft "1em" :padding "1ex" :width "16ex"
                            :border (str "1px solid " (:line-gray style/colors))
                            :borderRadius "2em"
                            :cursor "pointer"}}
              (:text props)])})]
    {:render
     (fn []
       [:div {:style {:display "inline-block" :marginLeft "-1em"}}
        [Button {:text "sample" :active? true}]
        [Button {:text "aliquot"}]
        [Button {:text "all"}]
        [:div {:style {:clear "both"}}]])}))


(defn render-workspace-data [entities]
  [:div {:style {:padding "2em 0" :textAlign "center"}}
   [EntityFilterButtons]
   [:div {:style {:padding "2em 0"}} [EntitiesList {:entities entities}]]])
