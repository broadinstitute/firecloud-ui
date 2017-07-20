(ns broadfcui.page.workspace.data.entity-selector
  (:require
   [dmohs.react :as react]
   [clojure.set :as set]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :as table]
   [broadfcui.common.table-utils :as table-utils]
   [broadfcui.utils :as utils]
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
     (let [attribute-keys (apply set/union (map #(set (keys (% "attributes"))) (:entities props)))
           columns (fn [source?]
                     (into
                      [{:starting-width 40 :resizable? false :reorderable? false :sort-by :none
                        :content-renderer
                        (fn [index]
                          (style/create-link {:text (icons/icon {} (if source? :add :remove))
                                              :onClick #(swap! state update :selected
                                                               (if source? conj disj) index)}))}
                       {:header (:id-name props) :starting-width 150
                        :as-text #(% "name") :sort-by :text}]
                      (map (fn [k] {:header k :starting-width 100
                                    :content-renderer
                                    (fn [attr-value]
                                      (if (and (map? attr-value)
                                               (= (set (keys attr-value)) #{"entityName"}))
                                        (attr-value "entityName")
                                        ((table-utils/render-gcs-links (:selected-workspace-bucket props)) attr-value)))})
                           attribute-keys)))
           data (fn [source?]
                  (replace
                   (mapv vector (range) (:entities props))
                   (if source?
                     (set/difference (-> (:entities props) count range set) (:selected @state))
                     (:selected @state))))
           ->row (fn [[index entity]]
                   (into
                    [index entity]
                    (map (fn [k] (get-in entity ["attributes" k])) attribute-keys)))
           create-table (fn [source?]
                          [:div {:style {:width box-width :display "inline-block"
                                         :padding "0.5em" :boxSizing "border-box"
                                         :backgroundColor "#fff" :border (str "1px solid" (:line-default style/colors))}}
                           [table/Table {:width :narrow
                                         :toolbar
                                         (table-utils/add-right
                                          [comps/Button {:onClick #(if source?
                                                                     (swap! state assoc :selected (set (range (count (:entities props)))))
                                                                     (swap! state assoc :selected #{}))
                                                         :text (if source? (str "Add all " (:type props) "s") "Clear")}])
                                         :empty-message ((if source? :left-empty-text :right-empty-text) props)
                                         :columns (columns source?)
                                         :data (data source?)
                                         :->row ->row}]])]
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
