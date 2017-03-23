(ns broadfcui.common.table.column-editor
  (:require
    [dmohs.react :as react]
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
  {:render
   (fn [{:keys [props state refs]}]
     (let [{:keys [columns column-display update-column-display]} props]
       [:div {:style {:border (str "2px solid " (:line-default style/colors))
                      :padding "1em" :lineHeight "1.5em" :cursor (when (:drag-active @state) "grab")}
              :onMouseMove (when (:drag-index @state)
                             (fn [e]
                               (let [x (.-clientX e)
                                     y (.-clientY e)
                                     dist (utils/distance (:start-x @state) (:start-y @state) x y)
                                     div-locs (map
                                               (fn [i] {:index i :y
                                                        (-> (@refs (str "div" i))
                                                            .getBoundingClientRect .-top)})
                                               (range (inc (count column-display))))
                                     closest-div (apply min-key #(js/Math.abs (- y (:y %))) div-locs)]
                                 (when (not= (:index closest-div) (:drop-index @state))
                                   (swap! state assoc :drop-index (:index closest-div)))
                                 (when (and (not (:drag-active @state)) (> dist 5.0))
                                   (swap! state assoc :drag-active true)))))
              :onMouseUp (when (:drag-index @state)
                           (fn [_]
                             (update-column-display (utils/move column-display (:drag-index @state) (:drop-index @state)))
                             (swap! state dissoc :drag-index :drag-active :drop-index)))}
        [:div {:style {:display "flex" :marginBottom "0.5em" :justifyContent "space-between"
                       :padding "4px 8px" :borderRadius 5 :cursor "pointer"
                       :border (str "1px solid " (:button-primary style/colors))
                       :color (:button-primary style/colors)}
               :onClick #(update-column-display (table-utils/build-column-display columns))}
         (icons/icon {:style {:fontSize 18 :lineHeight "inherit"}} :reset)
         [:span {:style {:fontSize 14 :flexGrow 1 :textAlign "center"}} "Reset Columns"]]
        "Show:"
        (let [style {:width "4rem" :padding "4px 8px" :marginRight "0.5rem" :borderRadius 5
                     :cursor (when-not (:drag-active @state) "pointer")}]
          [:div {:style {:padding "0.5em 0"}}
           [comps/Button {:style style :onClick #(set-all props true) :text "All"}]
           [comps/Button {:style style :onClick #(set-all props false) :text "None"}]])
        (map-indexed
         (fn [i column]
           (let [visible? (:visible? column)]
             [:div {:ref (str "div" i)
                    :style {:borderTop (when (= i (:drop-index @state)) "1px solid gray")}}
              (icons/icon {:style {:color (style/colors :text-light) :fontSize 16 :verticalAlign "middle" :marginRight "1ex"
                                   :cursor "ns-resize"}
                           :draggable false
                           :onMouseDown (fn [e] (swap! state assoc
                                                       :drag-index i
                                                       :start-x (.-clientX e)
                                                       :start-y (.-clientY e)))} :reorder)
              [:label {:style {:cursor (when-not (:drag-active @state) "pointer")}}
               [:input {:type "checkbox" :checked visible?
                        :onChange #(update-column-display (update-in column-display [i :visible?] not))}]
               [:span {:style {:paddingLeft "0.5em"} :title (:id column)} (:id column)]]]))
         (filter #(reorderable? (:id %) columns) column-display))
        (let [i (count column-display)]
          [:div {:ref (str "div" i)
                 :style {:borderTop (when (= i (:drop-index @state)) "1px solid gray")}}])]))})


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
