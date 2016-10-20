(ns org.broadinstitute.firecloud-ui.page.workspace.data.entity-selector
  (:require
    [dmohs.react :as react]
    [clojure.set :refer [union difference]]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.table-utils :refer [default-render]]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

(def ^:private box-width "calc(50% - 20px)")

(react/defc EntitySelector
  {:get-selected-entities
   (fn [{:keys [props state]}]
     (replace (:entities props) (:selected @state)))
   :get-default-props
   (fn []
     {:right-empty-text "Nothing selected"})
   :get-initial-state
   (fn []
     {:selected #{}})
   :component-will-receive-props
   (fn [{:keys [props state next-props]}]
     (when (not= (:entities props) (:entities next-props))
       (swap! state assoc :selected #{})))
   :render
   (fn [{:keys [state props]}]
     (let [attribute-keys (apply union (map #(set (keys (% "attributes"))) (:entities props)))
           columns (fn [source?]
                     (into
                      [{:header "Entity Type" :starting-width 100}
                       {:header "Entity Name" :starting-width 200
                        :as-text #(get-in % [1 "name"]) :sort-by :text
                        :content-renderer
                        (fn [[index entity]]
                          (style/create-link {:text (entity "name")
                                              :onClick #(swap! state update-in [:selected]
                                                               (if source? conj disj) index)}))}]
                      (map (fn [k] {:header k :starting-width 100 :show-initial? false
                                    :content-renderer
                                    (fn [attr-value]
                                      (if (and (map? attr-value)
                                               (= (set (keys attr-value)) #{"entityType" "entityName"}))
                                        (attr-value "entityName")
                                        (default-render attr-value)))})
                           attribute-keys)))
           data (fn [source?]
                  (replace
                   (mapv vector (range) (:entities props))
                   (if source?
                     (difference (-> (:entities props) count range set) (:selected @state))
                     (:selected @state))))
           ->row (fn [[index entity :as item]]
                   (into
                    [(entity "entityType")
                     item]
                    (map (fn [k] (get-in entity ["attributes" k])) attribute-keys)))
           create-table (fn [source?]
                          [:div {:style {:float (if source? "left" "right") :width box-width
                                         :padding "0.5em" :boxSizing "border-box"
                                         :backgroundColor "#fff" :border (str "1px solid" (:line-default style/colors))}}
                           [table/Table {:width :narrow
                                         :empty-message ((if source? :left-empty-text :right-empty-text) props)
                                         :columns (columns source?)
                                         :data (data source?)
                                         :->row ->row}]])]
       [:div {}
        [:div {:style {:float "left" :width box-width}}
         (:left-text props)]
        [:div {:style {:float "right" :width box-width}}
         (:right-text props)]
        (common/clear-both)
        (create-table true)
        [:div {:style {:float "left" :width 40 :paddingTop 120
                       :textAlign "center" :fontSize "180%"}}
         "⇄"]
        (create-table false)
        (common/clear-both)]))})
