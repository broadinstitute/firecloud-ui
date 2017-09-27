(ns broadfcui.common.table.column-editor
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.overlay :as overlay]
   [broadfcui.common.style :as style]
   [broadfcui.common.table.utils :as table-utils]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.utils :as utils]
   ))


(defn- set-all [{:keys [column-display update-column-display fixed-column-count]} visible?]
  (->> (drop fixed-column-count column-display)
       (map #(assoc % :visible? visible?))
       (concat (take fixed-column-count column-display))
       vec
       update-column-display))


(defn- render-row [column {:keys [columns fixed? dragging? on-drag-mouse-down on-visibility-change]}]
  (let [{:keys [id visible?]} column
        {:keys [hidden?] :as resolved} (table-utils/find-by-id id columns)
        text (table-utils/canonical-name resolved)]
    (when-not hidden?
      [:div {}
       (icons/icon {:className "grab-icon"
                    :style {:visibility (when fixed? "hidden")
                            :color (style/colors :text-light) :fontSize 16
                            :verticalAlign "middle" :marginRight "0.5rem"}
                    :draggable false
                    :onMouseDown on-drag-mouse-down}
                   :reorder)
       [:label {:style {:cursor (when-not (or dragging? fixed?) "pointer")}}
        [:input {:type "checkbox" :checked (or fixed? visible?)
                 :disabled fixed?
                 :onChange on-visibility-change}]
        [:span {:style {:paddingLeft "0.5em"} :title text} text]]])))


(react/defc ColumnEditor
  (->>
   {:render
    (fn [{:keys [props state]}]
      (let [{:keys [drag-index drop-index]} @state
            {:keys [columns column-display update-column-display fixed-column-count dismiss]} props
            fixed-columns (take fixed-column-count column-display)
            reorderable-columns (vec (drop fixed-column-count column-display))]
        [:div {:className (when drag-index "grabbing-icon")
               :style {:border (str "2px solid " (:line-default style/colors))
                       :padding "1em" :lineHeight "1.5em"}}
         [:div {:style {:display "flex" :marginBottom "0.5em" :justifyContent "space-between"
                        :padding "4px 8px" :borderRadius 5 :cursor (when-not drag-index "pointer")
                        :border (str "1px solid " (:button-primary style/colors))
                        :color (:button-primary style/colors)}
                :onClick (fn [_]
                           (update-column-display (table-utils/build-column-display columns))
                           (dismiss))}
          (icons/icon {:style {:fontSize 18 :lineHeight "inherit"}} :reset)
          [:span {:style {:fontSize 14 :flexGrow 1 :textAlign "center"}}
           "Reset Columns"]]
         "Show:"
         (let [style {:width "4rem" :padding "4px 8px" :marginRight "0.5rem" :borderRadius 5}]
           [:div {:style {:padding "0.5em 0"}}
            [buttons/Button {:style style :onClick #(set-all props true) :text "All"}]
            [buttons/Button {:style style :onClick #(set-all props false) :text "None"}]])
         (map (fn [column]
                (render-row column (merge props {:fixed? true :dragging? drag-index})))
              fixed-columns)
         (map-indexed
          (fn [i {:keys [id] :as column}]
            [:div {:ref (str "div" i)
                   :style {:height 24}}
             (if (= id :dummy)
               [:div {:style {:backgroundColor (:tag-background style/colors)
                              :color (:tag-foreground style/colors)
                              :borderRadius 4
                              :textAlign "center"}}
                (:drop-text @state)]
               (render-row
                column
                (merge props
                       {:dragging? drag-index
                        :on-drag-mouse-down #(swap! state assoc :drag-index i :drop-index i
                                                    :drop-text (table-utils/resolve-canonical-name id columns)
                                                    :saved-user-select-state (common/disable-text-selection))
                        :on-visibility-change #(update-column-display
                                                (update-in column-display [(+ i fixed-column-count) :visible?] not))})))])
          (if drag-index
            (-> reorderable-columns
                (utils/delete drag-index)
                (utils/insert drop-index {:id :dummy}))
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
                            (range (- (count (:columns props)) (:fixed-column-count props))))
              closest-div-index (:index (apply min-key #(js/Math.abs (- y (:midpoint %))) div-locs))]
          (when-not (= (:drop-index @state) closest-div-index)
            (swap! state assoc :drop-index closest-div-index)))))
    :-on-mouse-up
    (fn [{:keys [props state]}]
      (let [{:keys [update-column-display column-display fixed-column-count]} props
            {:keys [drag-index drop-index]} @state]
        (when drag-index
          (update-column-display (utils/move column-display
                                             (+ drag-index fixed-column-count)
                                             (+ drop-index fixed-column-count)))
          (common/restore-text-selection (:saved-user-select-state @state))
          (swap! state dissoc :drag-index :drop-index :drop-text :saved-user-select-state))))}
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
     [:div {}
      [buttons/Button (merge
                       {:ref "col-edit-button" :onClick #(swap! state assoc :reordering-columns? true)}
                       (:button props))]
      (when (:reordering-columns? @state)
        (let [dismiss #(swap! state assoc :reordering-columns? false)]
          [overlay/Overlay
           {:get-anchor-dom-node #(react/find-dom-node (@refs "col-edit-button"))
            :dismiss-self dismiss
            :anchor-x (:reorder-anchor props)
            :content
            (react/create-element
             ColumnEditor
             (merge
              {:dismiss dismiss}
              (select-keys props [:columns :column-display :update-column-display :fixed-column-count])))}]))])})
