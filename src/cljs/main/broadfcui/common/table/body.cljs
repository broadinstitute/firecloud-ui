(ns broadfcui.common.table.body
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.utils :as utils]
    ))


(defn- flex-params [width]
  (if (= width :auto)
    {:flexBasis "auto" :flexGrow 1 :flexShrink 1}
    {:flexBasis width :flexGrow 0 :flexShrink 0}))


(defn- header [{:keys [joined-columns sort-column sort-order style]}]
  [:div {:style (merge {:display "flex"} (:header-row style))}
   (map
    (fn [{:keys [width header]}]
      [:div {:style (merge (flex-params width) (:header-cell style))}
       header])
    joined-columns)])


(defn- body [{:keys [rows joined-columns style]}]
  [:div {}
   (map-indexed
    (fn [index row]
      [:div {:style (merge {:display "flex"}
                           ((or (:body-row style) identity) (utils/restructure index row)))}
       (map
        (fn [{:keys [width row->col render]}]
          [:div {:style (merge (flex-params width) (:body-cell style))}
           (-> row row->col render)])
        joined-columns)])
    rows)])


(defn- resolve-id [{:keys [header id]}]
  (assert (or (string? id) (string? header)) "Every column must have a string header or id")
  (or id (str header)))

(defn- resolve-all-props [{:keys [row->col as-text sort-by filter-as render] :as props}]
  (merge props
         {:row->col (or row->col identity)
          :as-text (or as-text str)
          :sort-fn (if (= sort-by :text) as-text (or sort-by as-text identity))
          :filter-fn (or filter-as as-text str)
          :render (or render identity)}))

(defn- join-columns [{:keys [raw-columns-by-id column-display]}]
  (map (fn [{:keys [id] :as data}]
         (merge data (resolve-all-props (raw-columns-by-id id))))
       column-display))


(react/defc TableBody
  (->>
   {:get-initial-state
    (fn [{:keys [props]}]
      {:column-display
       (map (fn [{:keys [initial-width show-initial?] :as raw-column}]
              {:id (resolve-id raw-column)
               :width (or initial-width 100)
               :visible? (if (some? show-initial?) show-initial? true)})
            (:columns props))})
    :render
    (fn [{:keys [props state]}]
      (let [{:keys [rows columns sort-column sort-order style]} props
            joined-columns (join-columns {:raw-columns-by-id (utils/index-by resolve-id columns)
                                          :column-display (:column-display @state)})]
        [:div {}
         (header (utils/restructure joined-columns sort-column sort-order style))
         (body (utils/restructure rows joined-columns style))]))}
   (utils/with-window-listeners
    {"mousemove"
     (fn [{:keys [state]} e]
       (when (:dragging? @state)
         (let [{:keys [drag-column mouse-x]} @state
               current-width (:width (nth (:column-display @state) drag-column))
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
