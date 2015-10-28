(ns org.broadinstitute.firecloud-ui.page.workspace.data.entity-selector
  (:require
    [dmohs.react :as react]
    [clojure.set :refer [union difference]]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    ))

(def ^:private box-width "calc(50% - 4px)")

(react/defc EntitySelector
  {:get-selected-entities
   (fn [{:keys [props state]}]
     (replace (:entities props) (:selected @state)))
   :get-initial-state
   (fn []
     {:selected #{}})
   :render
   (fn [{:keys [state props]}]
     (let [attribute-keys (apply union (map #(set (keys (% "attributes"))) (:entities props)))
           columns (fn [source?]
                     (concat
                       [{:header "Entity Type" :starting-width 100}
                        {:header "Entity Name" :starting-width 100
                         :as-text #(get-in % [0 "name"]) :sort-by :text
                         :content-renderer
                         (fn [[entity index]]
                           (style/create-link
                             #(swap! state update-in [:selected] (if source? conj disj) index)
                             (entity "name")))}]
                       (map (fn [k] {:header k :starting-width 100 :show-initial? false})
                         attribute-keys)))
           data (fn [source?]
                  (replace
                    (into [] (zipmap (range) (:entities props)))
                    (if source?
                      (difference (-> (:entities props) count range set) (:selected @state))
                      (:selected @state))))
           ->row (fn [[index entity]]
                   (concat
                     [(entity "entityType")
                      [entity index]]
                     (map (fn [k] (get-in entity ["attributes" k])) attribute-keys)))
           create-table (fn [source?]
                          [:div {:style {:float (if source? "left" "right") :width box-width
                                         :padding "0.5em" :boxSizing "border-box"
                                         :backgroundColor "#fff" :border (str "1px solid" (:line-gray style/colors))}}
                           [table/Table {:width :narrow
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
        (create-table false)
        (common/clear-both)]))})
