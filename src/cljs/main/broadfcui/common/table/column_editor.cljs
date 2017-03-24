(ns broadfcui.common.table.column-editor
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.overlay :as overlay]
    [broadfcui.common.table.utils :as table-utils]
    [broadfcui.common.style :as style]
    [broadfcui.utils :as utils]
    ))


(defn- reorderable? [id columns]
  (get (table-utils/find-by-id id columns) :reorderable? true))

(defn- set-all [{:keys [columns column-display update-column-display]} visible?]
  (update-column-display (mapv (fn [{:keys [id] :as col}]
                                 (assoc col :visible?
                                            (if (reorderable? id columns) visible? true)))
                               column-display)))


(react/defc ColumnEditor
  (->>
   {:render
    (fn [{:keys [props state]}]
      (let [{:keys [drag-index drop-index]} @state
            {:keys [columns column-display update-column-display]} props
            reorderable-columns (filterv #(reorderable? (:id %) columns) column-display)]
        [:div {:style {:border (str "2px solid " (:line-default style/colors))
                       :padding "1em" :lineHeight "1.5em" :cursor (when drag-index "grab")}}
         [:div {:style {:display "flex" :marginBottom "0.5em" :justifyContent "space-between"
                        :padding "4px 8px" :borderRadius 5 :cursor "pointer"
                        :border (str "1px solid " (:button-primary style/colors))
                        :color (:button-primary style/colors)}
                :onClick #(update-column-display (table-utils/build-column-display columns))}
          (icons/icon {:style {:fontSize 18 :lineHeight "inherit"}} :reset)
          [:span {:style {:fontSize 14 :flexGrow 1 :textAlign "center"}} "Reset Columns"]]
         "Show:"
         (let [style {:width "4rem" :padding "4px 8px" :marginRight "0.5rem" :borderRadius 5
                      :cursor (when-not drag-index "pointer")}]
           [:div {:style {:padding "0.5em 0"}}
            [comps/Button {:style style :onClick #(set-all props true) :text "All"}]
            [comps/Button {:style style :onClick #(set-all props false) :text "None"}]])
         (map-indexed
          (fn [i column]
            [:div {:ref (str "div" i)
                   :style {:height 24}}
             (if (= column :dummy)
               [:div {} "< Drop Here >"]
               (let [visible? (:visible? column)]
                 [:div {}
                  (icons/icon {:style {:color (style/colors :text-light) :fontSize 16 :verticalAlign "middle" :marginRight "1ex"
                                       :cursor "ns-resize"}
                               :draggable false
                               :onMouseDown #(swap! state assoc :drag-index i :drop-index i
                                                    :saved-user-select-state (common/disable-text-selection))}
                              :reorder)
                  [:label {:style {:cursor (when-not drag-index "pointer")}}
                   [:input {:type "checkbox" :checked visible?
                            :onChange #(update-column-display (update-in column-display [i :visible?] not))}]
                   [:span {:style {:paddingLeft "0.5em"} :title (:id column)} (:id column)]]]))])
          (if drag-index
            (-> reorderable-columns
                (utils/delete drag-index)
                (utils/insert drop-index :dummy))
            reorderable-columns))]))
    :-on-mouse-move
    (fn [{:keys [props state refs]} e]
      (when (:drag-index @state)
        (let [y (.-clientY e)
              div-locs (map (fn [i]
                              {:index i
                               :midpoint
                               (let [rect (.getBoundingClientRect (@refs (str "div" i)))]
                                 (/ (+ (.-top rect) (.-bottom rect)) 2))})
                            (range (count (:columns props) #_"TODO: FIX" )))
              closest-div-index (:index (apply min-key #(js/Math.abs (- y (:midpoint %))) div-locs))]
          (when-not (= (:drop-index @state) closest-div-index)
            (swap! state assoc :drop-index closest-div-index)))))
    :-on-mouse-up
    (fn [{:keys [props state]}]
      (when (:drag-index @state)
        (let [{:keys [update-column-display column-display]} props]
          (update-column-display (utils/move column-display (:drag-index @state) (:drop-index @state)))
          (common/restore-text-selection (:saved-user-select-state @state))
          (swap! state dissoc :drag-index :drop-index :saved-user-select-state))))}
   (utils/with-window-listeners
    {"mousemove"
     (fn [{:keys [this]} e]
       (this :-on-mouse-move e))
     "mouseup"
     (fn [{:keys [this]}]
       (this :-on-mouse-up))})))


(react/defc ColumnEditButton
  {:get-default-props
   (fn []
     {:reorder-anchor :left})
   :render
   (fn [{:keys [props state refs]}]
     [:div {:style {:marginRight "1rem"}}
      [comps/Button {:ref "col-edit-button" :icon :settings
                     :onClick #(swap! state assoc :reordering-columns? true)}]
      (when (:reordering-columns? @state)
        [overlay/Overlay
         {:get-anchor-dom-node #(react/find-dom-node (@refs "col-edit-button"))
          :dismiss-self #(swap! state assoc :reordering-columns? false)
          :anchor-x (:reorder-anchor props)
          :content
          (react/create-element
           ColumnEditor (select-keys props [:columns :column-display :update-column-display]))}])])})
