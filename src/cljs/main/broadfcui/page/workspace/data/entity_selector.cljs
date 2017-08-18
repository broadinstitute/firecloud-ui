(ns broadfcui.page.workspace.data.entity-selector
  (:require
   [dmohs.react :as react]
   [clojure.set :as set]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.common.table.utils :as table-utils]
   [broadfcui.utils :as utils]
   ))

(def ^:private box-width "calc(50% - 20px)")

(react/defc EntitySelector
  {:get-selected-entities
   (fn [{:keys [props state]}]
     (replace (:entities props) (:selected @state)))
   :get-default-props
   (fn []
     {:left-empty-text "No entities available"
      :right-empty-text "Nothing selected"})
   :get-initial-state
   (fn []
     {:selected #{}})
   :component-will-receive-props
   (fn [{:keys [props state next-props]}]
     (when (not= (:entities props) (:entities next-props))
       (swap! state assoc :selected #{})))
   :render
   (fn [{:keys [state props]}]
     (let [attribute-keys (->> (:entities props)
                               (map (comp set keys :attributes))
                               (apply set/union))
           columns (fn [source?]
                     (into
                      [{:id "add-button" :initial-width 40
                        :hidden? true :resizable? false :sortable? false :filterable? false
                        :column-data first
                        :render
                        (fn [index]
                          (links/create-internal {:onClick #(swap! state update :selected
                                                                   (if source? conj disj) index)}
                                                 (icons/icon {} (if source? :add :remove))))}
                       {:header (:id-name props) :initial-width 150
                        :column-data second
                        :as-text :name :sort-by :text}]
                      (map (fn [k] {:header (name k) :initial-width 100
                                    :column-data (comp k :attributes second)
                                    :render
                                    (fn [attr-value]
                                      (if (and (map? attr-value)
                                               (= (set (keys attr-value)) #{:entityName :entityType}))
                                        (:entityName attr-value)
                                        ((table-utils/render-gcs-links (:selected-workspace-bucket props)) attr-value)))})
                           attribute-keys)))
           data (fn [source?]
                  (replace
                   (mapv vector (range) (:entities props))
                   (if source?
                     (set/difference (-> (:entities props) count range set) (:selected @state))
                     (:selected @state))))
           create-table (fn [source?]
                          [:div {:style {:width box-width :display "inline-block"
                                         :padding "0.5em" :boxSizing "border-box"
                                         :backgroundColor "#fff" :border (str "1px solid" (:line-default style/colors))}}
                           [Table
                            {:data (data source?)
                             :body {:empty-message ((if source? :left-empty-text :right-empty-text) props)
                                    :style table-style/table-heavy
                                    :columns (columns source?)}
                             :toolbar
                             {:style {:minWidth 600}
                              :get-items
                              (constantly
                               [flex/spring
                                [comps/Button {:onClick #(swap! state assoc :selected
                                                                (if source?
                                                                  (-> (:entities props) count range set)
                                                                  #{}))
                                               :text (if source? (str "Add all " (:type props) "s") "Clear")}]])}}]])]
       [:div {}
        [:div {:style {:width box-width :paddingBottom "0.5rem" :display "inline-block"
                       :fontWeight 500}}
         (:left-text props)]
        [:div {:style {:width box-width :paddingBottom "0.5rem" :display "inline-block"
                       :fontWeight 500 :marginLeft 40}}
         (:right-text props)]
        [:div {:style {:display "flex"}}
         (create-table true)
         [:div {:style {:width 40 :paddingTop 120 :display "inline-block"
                        :textAlign "center" :fontSize "180%"}}
          "⇄"]
         (create-table false)]]))})
