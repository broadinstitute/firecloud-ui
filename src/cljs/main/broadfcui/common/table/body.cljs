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


(defn- header [{:keys [joined-columns sort-column sort-order set-sort style state]}]
  [:div {:style (merge {:display "flex"} (:row style) (:header-row style))}
   (map-indexed
    (fn [index {:keys [id width initial-width header resizable? sortable?]}]
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
                :onMouseDown (fn [e]
                               (swap! state assoc
                                      :dragging? true :mouse-x (.-clientX e) :drag-column index
                                      :saved-user-select-state (common/disable-text-selection)))
                :onDoubleClick #(swap! state assoc-in [:column-display index :width] initial-width)}])])
    joined-columns)])


(defn- body [{:keys [rows joined-columns style]}]
  [:div {:style {:body style}}
   (map-indexed
    (fn [index row]
      [:div {:style (merge {:display "flex"}
                           (:row style)
                           ((or (:body-row style) identity) (utils/restructure index row)))}
       (map
        (fn [{:keys [width column-data render]}]
          [:div {:style (merge (flex-params width) (:cell style) (:body-cell style))}
           (-> row column-data render)])
        joined-columns)])
    rows)])


(defn- resolve-column-props [{:keys [initial-width] :as props} behavior]
  (merge {:resizable? (and (not= initial-width :auto) (:resizable-columns? behavior))
          :sortable? (:sortable-columns? behavior)
          :column-data identity
          :render identity}
         props))

(defn- join-columns [{:keys [raw-columns-by-id column-display table-behavior]}]
  (map (fn [{:keys [id] :as data}]
         (merge data (resolve-column-props (raw-columns-by-id id) table-behavior)))
       column-display))


(react/defc TableBody
  (->>
   {:get-initial-state
    (fn [{:keys [props]}]
      {:column-display
       (mapv (fn [{:keys [initial-width show-initial?] :as raw-column}]
               {:id (table-utils/resolve-id raw-column)
                :width (or initial-width 100)
                :visible? (if (some? show-initial?) show-initial? true)})
             (:columns props))})
    :render
    (fn [{:keys [props state]}]
      (let [{:keys [rows columns sort-column sort-order set-sort style]} props
            joined-columns (join-columns {:raw-columns-by-id (table-utils/index-by-id columns)
                                          :column-display (:column-display @state)
                                          :table-behavior (:behavior props)})]
        [:div {:style (:table style)}
         (header (utils/restructure joined-columns sort-column sort-order set-sort style state))
         (body (utils/restructure rows joined-columns style))]))}
   (utils/with-window-listeners
    {"mousemove"
     (fn [{:keys [state]} e]
       (when (:dragging? @state)
         (let [{:keys [drag-column mouse-x column-display]} @state
               current-width (:width (nth column-display drag-column))
               new-mouse-x (.-clientX e)
               drag-amount (- new-mouse-x mouse-x)
               new-width (+ current-width drag-amount)]
           (when (and (>= new-width 10) (not (zero? drag-amount)))
             (swap! state #(-> %
                               (assoc :mouse-x new-mouse-x)
                               (assoc-in [:column-display drag-column :width] new-width)))))))
     "mouseup"
     (fn [{:keys [state]}]
       (when (:dragging? @state)
         (common/restore-text-selection (:saved-user-select-state @state))
         (swap! state dissoc :dragging? :drag-column :mouse-x :saved-user-select-state)))})))
