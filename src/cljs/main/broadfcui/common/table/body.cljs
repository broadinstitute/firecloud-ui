(ns broadfcui.common.table.body
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.flex-utils :as flex]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.table.utils :as table-utils]
    [broadfcui.utils :as utils]
    ))


(defn- flex-params [width]
  (if (= width :auto)
    {:flexBasis "auto" :flexGrow 1 :flexShrink 1}
    {:flexBasis width :flexGrow 0 :flexShrink 0}))


(def ^:private column-drag-margin 11)


(defn- header [{:keys [joined-columns sort-column sort-order set-sort style
                       start-column-drag column-reset allow-no-sort?]}]
  [:div {:style (merge {:display "flex"} (:row style) (:header-row style))}
   (map-indexed
    (fn [index {:keys [id width initial-width header visible? resizable? sortable?]}]
      (when visible?
        [:div {:style (merge (flex-params width)
                             {:position "relative" :cursor (when sortable? "pointer")}
                             (when resizable? (or (:resize-tab style)
                                                  {:borderRight "1px solid" :marginRight -1})))}
         [:div {:style (merge {:display "flex" :width width}
                              (:cell style)
                              (:header-cell style))
                :onClick (when sortable?
                           #(cond (not= sort-column id) (set-sort id :asc)
                                  (= sort-order :asc) (set-sort id :desc)
                                  allow-no-sort? (set-sort nil nil)
                                  :else (set-sort id :asc)))}
          [:div {:style {:flex "1 1 auto" :overflow "hidden" :textOverflow "ellipsis"}}
           header]
          flex/spring
          (when (= id sort-column)
            (icons/icon {:style {:margin "0 0.4rem 0 0.1rem"}}
                        (if (= :asc sort-order) :sort-asc :sort-desc)))]
         (when resizable?
           [:div {:style {:position "absolute" :cursor "col-resize"
                          :right (- column-drag-margin) :width (dec (* 2 column-drag-margin))
                          :top 0 :height "100%" :zIndex 1}
                  :onMouseDown (fn [e] (start-column-drag (utils/restructure e width index)))
                  :onDoubleClick #(column-reset {:index index :initial-width (or initial-width 100)})}])]))
    joined-columns)])


(defn- body [{:keys [rows joined-columns style]}]
  [:div {:style (:body style)}
   (map-indexed
    (fn [index row]
      [:div {:style (merge {:display "flex"}
                           (:row style)
                           ((or (:body-row style) identity) (utils/restructure index row)))}
       (map
        (fn [{:keys [width visible? column-data render as-text]}]
          (when visible?
            (let [column-value (column-data row)
                  rendered (render column-value)]
              [:div {:style (merge (flex-params width) (:cell style) (:body-cell style))
                     :title (cond as-text (as-text column-value)
                                  (string? rendered) rendered)}
               rendered])))
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
   {:render
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
            properties (merge props (utils/restructure joined-columns start-column-drag column-reset))]
        [:div {:style (merge {:width "-webkit-fit-content" :minWidth "100%"} (:table style))}
         (header properties)
         (body properties)]))
    :-on-mouse-move
    (fn [{:keys [props state locals]} e]
      (when (:dragging? @state)
        (let [{:keys [update-column-display column-display]} props
              {:keys [drag-column]} @state
              {:keys [start-mouse-x start-width]} @locals
              delta-x (- (.-clientX e) start-mouse-x)
              new-width (max (+ start-width delta-x) 10)]
          (update-column-display (assoc-in column-display [drag-column :width] new-width)))))}
   (utils/with-window-listeners
    {"mousemove"
     (fn [{:keys [this]} e]
       (this :-on-mouse-move e))
     "mouseup"
     (fn [{:keys [state locals]}]
       (when (:dragging? @state)
         (common/restore-text-selection (:saved-user-select-state @state))
         (swap! locals dissoc :mouse-x)
         (swap! state dissoc :dragging? :drag-column :saved-user-select-state)))})))
