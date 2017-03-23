(ns broadfcui.common.table.body
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.table.utils :as table-utils]
    [broadfcui.utils :as utils]
    ))


(defn- flex-params [width]
  (if (= width :auto)
    {:flexBasis "auto" :flexGrow 1 :flexShrink 1}
    {:flexBasis width :flexGrow 0 :flexShrink 0}))


(defn- header [{:keys [joined-columns sort-column sort-order set-sort style
                       start-column-drag column-reset]}]
  [:div {:style (merge {:display "flex"} (:row style) (:header-row style))}
   (map-indexed
    (fn [index {:keys [id width initial-width header visible? resizable? sortable?]}]
      (when visible?
        [:div {:style (merge (flex-params width)
                             {:position "relative" :cursor (when sortable? "pointer")}
                             (when resizable? (or (:resize-tab style)
                                                  {:borderRight "1px solid" :marginRight -1})))}
         [:div {:style (merge {:width width} (:cell style) (:header-cell style))
                :onClick (when sortable?
                           #(set-sort id (if (or (not= sort-column id)
                                                 (= sort-order :desc))
                                           :asc
                                           :desc)))}
          header
          (when (= id sort-column)
            [:span {:style {:marginLeft "0.4rem"}}
             (if (= :asc sort-order) "↑" "↓")])]
         (when resizable?
           [:div {:style {:position "absolute" :cursor "col-resize"
                          :right -11 :top 0 :width 21 :height "100%" :zIndex 1}
                  :onMouseDown (fn [e] (start-column-drag (utils/restructure e width index)))
                  :onDoubleClick #(column-reset (utils/restructure index initial-width))}])]))
    joined-columns)])


(defn- body [{:keys [rows joined-columns style]}]
  [:div {:style (:body style)}
   (map-indexed
    (fn [index row]
      [:div {:style (merge {:display "flex"}
                           (:row style)
                           ((or (:body-row style) identity) (utils/restructure index row)))}
       (map
        (fn [{:keys [width visible? column-data render]}]
          (when visible?
            [:div {:style (merge (flex-params width) (:cell style) (:body-cell style))}
             (-> row column-data render)]))
        joined-columns)])
    rows)])


(defn- resolve-column-props [{:keys [initial-width as-text] :as props} behavior]
  (merge {:resizable? (and (not= initial-width :auto) (:resizable-columns? behavior))
          :sortable? (:sortable-columns? behavior)
          :column-data identity
          :render (or as-text identity)}
         props))

(defn- join-columns [{:keys [raw-columns-by-id column-display table-behavior]}]
  (map (fn [{:keys [id] :as data}]
         (merge data (resolve-column-props (raw-columns-by-id id) table-behavior)))
       column-display))


(react/defc TableBody
  (->>
   {:swap-columns
    (fn [{:keys [state]} source-index target-index]
      (swap! state update :column-display utils/move source-index target-index))
    :set-column-visibility
    (fn [{:keys [props state]} column-index visible?]
      (if (= :all column-index)
        ;; if you have explicitly set a column to not be reorderable, then we will always
        ;; display it (even if you've clicked on none)
        (swap! state update :column-display
               #(vec (map merge %
                          (map (fn [column]
                                 (if (= (:reorderable? column) false)
                                   {:visible? true}
                                   {:visible? visible?}))
                               (:columns props)))))
        (swap! state assoc-in [:column-display column-index :visible?] visible?)))
    :get-initial-state
    (fn [{:keys [props]}]
      {:column-display
       (mapv (fn [{:keys [initial-width show-initial?] :as raw-column}]
               {:id (table-utils/resolve-id raw-column)
                :width (or initial-width 100)
                :visible? (if (some? show-initial?) show-initial? true)})
             (:columns props))})
    :render
    (fn [{:keys [props state locals]}]
      (let [{:keys [columns style update-column-display column-display]} props
            joined-columns (join-columns {:raw-columns-by-id (table-utils/index-by-id columns)
                                          :column-display (:column-display props)
                                          :table-behavior (:behavior props)})
            start-column-drag
            (fn [{:keys [e width index]}]
              (swap! locals assoc :start-mouse-x (.-clientX e) :start-width width)
              (swap! state assoc
                     :dragging? true :drag-column index
                     :saved-user-select-state (common/disable-text-selection)))
            column-reset
            (fn [{:keys [index initial-width]}]
              (update-column-display (assoc-in column-display [index :width] initial-width)))
            +props (merge props (utils/restructure joined-columns start-column-drag column-reset))]
        [:div {:style (:table style)}
         (header +props)
         (body +props)]))}
   (utils/with-window-listeners
    {"mousemove"
     (fn [{:keys [props state locals]} e]
       (when (:dragging? @state)
         (let [{:keys [drag-column]} @state
               {:keys [start-mouse-x start-width]} @locals
               {:keys [column-display]} props
               delta-x (- (.-clientX e) start-mouse-x)
               new-width (max (+ start-width delta-x) 10)]
           ((:update-column-display props) (assoc-in column-display [drag-column :width] new-width)))))
     "mouseup"
     (fn [{:keys [state locals]}]
       (when (:dragging? @state)
         (common/restore-text-selection (:saved-user-select-state @state))
         (swap! locals dissoc :mouse-x)
         (swap! state dissoc :dragging? :drag-column :saved-user-select-state)))})))
