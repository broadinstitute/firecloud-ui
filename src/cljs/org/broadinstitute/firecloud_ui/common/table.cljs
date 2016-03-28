(ns org.broadinstitute.firecloud-ui.common.table
  (:require
   [clojure.set :refer [union]]
   [clojure.string :refer [join]]
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.components :as comps]
   [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.common.table-utils :as table-utils]
   [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))


(def ^:private initial-rows-per-page 10)


(defn date-column [props]
  {:header (or (:header props) "Create Date")
   :starting-width (or (:starting-width props) 200)
   :as-text #(common/format-date % (:format props))})


;; Table component with specifiable style and column behaviors.
;;
;; Properties:
;;   :cell-padding-left (optional, default 16px)
;;     A CSS padding-left value to apply to each cell
;;   :paginator-space (optional, default 24)
;;     A CSS padding value used to separate the table and paginator.
;;   :resizable-columns? (optional, default true)
;;     Fallback value for column resizing.
;;   :resize-tab-color (optional, default gray)
;;     Color for the drag-to-resize tab
;;   :reorderable-columns? (optional, default true)
;;     Controls whether or not columns are reorderable.  When true, a reorder widget is presented
;;   :sortable-columns? (optional, default true)
;;     Fallback value for column sorting.
;;   :filterable? (optional, default true)
;;     Controls whether or not columns are filterable.  When true, a filter widget is presented
;;   :empty-message (optional, default "There are no rows to display.")
;;     A banner to display when the table is empty
;;   :retain-header-on-empty? (optional, default false)
;;     Whether or not to show the header when there are no rows to display
;;   :row-style (optional)
;;     Style to apply to each row.  Value is either a style map, or a function taking the index and row
;;     data and returning a style map.
;;     When omitted, default properties create alternating white and gray backgrounds.
;;   :header-row-style (optional)
;;     Style to apply to the header row.  When omitted, style is a dark gray background with bold white text
;;   :toolbar (optional)
;;     Use to provide more items in the toolbar, along with the filterer and column reorderer (if present).
;;     This value should be a function that takes the "built-in" toolbar as a parameter, and returns an
;;     HTML element.  If this property is not supplied, the built-in toolbar is placed as normal.
;;   :width (optional, default normal)
;;     Specify :width :narrow to make the paginator layout in a narrow width friendly way.  Any other value
;;     (including none) corresponds to the normal layout
;;   :columns (REQUIRED)
;;     A sequence of column maps.  The order given is used as the initial order.
;;     Columns have the following properties:
;;       :header (optional, default none)
;;         The text to display.
;;       :reorderable? (optional, default true)
;;         Whether or not the column should be allowed to be reordered.  Unused if :reorderable-columns?
;;         is false on the table.
;;       :show-initial? (optional, default true)
;;         Whether or not to initially show the column.
;;       :starting-width (optional, default 100)
;;         The initial width, which may be resized
;;       :as-text (optional)
;;         A function from the column value to a one-line text representation.  Used as a fallback for
;;         rendering, filtering, and sorting, and TODO: will be used for exporting tables
;;       :content-renderer (optional)
;;         A function from the column value to a displayable representation.  If omitted, :as-text is used.
;;         If :as-text is also omitted, a default renderer is used.
;;       :resizable? (optional)
;;         Controls if the column is resizable.  If absent, falls back to the table.
;;       :filter-by (optional, defaults to :as-text and then to 'str')
;;         A function from the column value to a string to use for matching filter text.
;;         Use ':filter-by :none' to disable filtering a specific column of an otherwise filterable table.
;;       :sort-by (optional)
;;         A function from the column value to a sortable type.  If present, the column is made
;;         sortable.  If omitted, the :sortable-columns? top-level property is checked to see if
;;         the column should be sortable, and if so, the column is sorted by the column type directly.
;;         Use ':sort-by :text' to sort on the value returned by :as-text.
;;         Use ':sort-by :none' to disable sorting a specific column of an otherwise sortable table.
;;       :sort-initial (optional)
;;         A flag to set the initial column to sort.  Value is either :asc or :desc.  If present on multiple
;;         columns, the first one will be used.
;;   :filters (OPTIONAL)
;;     A vector of filters to apply to the data. Each item as the following properties:
;;       :text (required)
;;         A label for the filter.
;;       :pred (required)
;;         A function that, given a data item, returns true if that item matches the filter.
;;       :count (optional)
;;         Use to override the displayed count, which normally uses the :pred
;;   :selected-filter-index (OPTIONAL)
;;     Currently selected filter.
;;   :on-filter-change (OPTIONAL)
;;     A function called when the active filter is changed. Passed the new filter index.
;;   :data (REQUIRED)
;;     A sequence items that will appear in the table.
;;   :num-total-rows (OPTIONAL)
;;     The total number of rows that would normally be shown in the table.  Specify if you are externally
;;     filtering; this value will show up in the paginator.
;;   :->row (REQUIRED)
;;     A function that takes a data item and returns a vector representing a table row.
(react/defc Table
  {:get-default-props
   (fn []
     {:cell-padding-left "16px"
      :paginator-space 24
      :resizable-columns? true
      :reorderable-columns? true
      :sortable-columns? true
      :filterable? true
      :row-style (fn [index row]
                   {:backgroundColor (if (even? index) (:background-gray style/colors) "#fff")})})
   :get-initial-state
   (fn [{:keys [this props]}]
     (set! (.-filtered-data this) (if-let [filters (:filters props)]
                                    (filter
                                     (get-in filters [(or (:selected-filter-index props) 0) :pred])
                                     (:data props))
                                    (:data props)))
     (let [columns (vec (map-indexed (fn [i col]
                                       {:width (or (:starting-width col) 100)
                                        :visible? (get col :show-initial? true)
                                        :index i
                                        :display-index i})
                                     (:columns props)))]
       (merge
        {:no-data? (zero? (count (.-filtered-data this)))
         :columns columns
         :dragging? false}
        (when-let [col (first (filter #(contains? % :sort-initial)
                                      (map merge (:columns props) columns)))]
          {:key-fn (if-let [sorter (:sort-by col)]
                     (fn [row] (sorter (nth row (:index col))))
                     (fn [row] (nth row (:index col))))
           :sort-order (:sort-initial col)
           :sort-column (:index col)}))))
   :render
   (fn [{:keys [this state props refs after-update]}]
     (let [paginator [table-utils/Paginator
                      {:ref "paginator"
                       :width (:width props)
                       :initial-rows-per-page initial-rows-per-page
                       :num-total-rows (or (:num-total-rows props) (count (:data props)))
                       :onChange #(react/call :set-body-rows this)}]]
       [:div {}
        (when (or (:filterable? props) (:reorderable-columns? props) (:toolbar props))
          (let [built-in
                [:div {:style {:paddingBottom "1em"}}
                 (when (:reorderable-columns? props)
                   [:div {:style {:float "left"}}
                    [comps/Button {:icon :gear :title-text "Select Columns..."
                                   :ref "col-edit-button"
                                   :onClick #(swap! state assoc :reordering-columns? true)}]
                    (when (:reordering-columns? @state)
                      [dialog/Dialog
                       {:get-anchor-dom-node #(react/find-dom-node (@refs "col-edit-button"))
                        :blocking? false
                        :dismiss-self #(swap! state assoc :reordering-columns? false)
                        :content
                        (react/create-element
                         table-utils/ColumnEditor
                         {:columns (react/call :get-ordered-columns this)
                          :on-reorder
                          (fn [source-index target-index]
                            (let [column-order (vec (sort-by
                                                     :display-index
                                                     (map #(select-keys % [:index :display-index])
                                                          (:columns @state))))
                                  new-order (utils/move column-order source-index target-index)
                                  new-order (map-indexed (fn [i c] (assoc c :display-index i))
                                                         new-order)
                                  new-order (map :display-index (sort-by :index new-order))]
                              (swap! state update-in [:columns]
                                     #(mapv (fn [new-index c] (assoc c :display-index new-index))
                                            new-order %))))
                          :on-visibility-change
                          (fn [column-index visible?]
                            (swap!
                             state update-in [:columns]
                             (fn [columns]
                               (if (= :all column-index)
                                 (mapv #(assoc % :visible? visible?) columns)
                                 (assoc-in columns [column-index :visible?] visible?)))))})}])])
                 (when (:filterable? props)
                   [:div {:style {:float "left" :marginLeft "1em"}}
                    [table-utils/Filterer {:ref "filterer"
                                           :onFilter #(react/call :set-body-rows this)}]])
                 (when (:filters props)
                   [:div {:style {:float "left" :marginLeft "1em" :marginTop -3}}
                    [table-utils/FilterBar
                     (merge (select-keys props [:filters :columns :data])
                            {:ref "filter-bar"
                             :selected-index (:selected-filter-index props)
                             :on-change #(do
                                           (react/call :set-body-rows this)
                                           (when-let [f (:on-filter-change props)]
                                             (f %)))})]])
                 (common/clear-both)]]
            ((or (:toolbar props) identity) built-in)))
        [:div {}
         [:div {:style {:overflowX "auto"}}
          [:div {:style {:position "relative"
                         :paddingBottom 10
                         :minWidth (when-not (:no-data? @state)
                                     (reduce
                                      + (map :width
                                             (filter :visible?
                                                     (react/call :get-ordered-columns this)))))
                         :cursor (when (:dragging? @state) "col-resize")}}
           (when (or (not (:no-data? @state)) (:retain-header-on-empty? props))
             (table-utils/render-header state props this after-update))
           (when (:no-data? @state)
             (style/create-message-well (or (:empty-message props) "There are no rows to display.")))
           [table-utils/Body
            (assoc props
                   :ref "body"
                   :columns (filter :visible? (react/call :get-ordered-columns this))
                   :initial-rows
                   (react/call :get-body-rows this))]]]]
        [:div {:style {:paddingTop (:paginator-space props)}} paginator]]))
   :get-ordered-columns
   (fn [{:keys [props state]}]
     (vec
      (sort-by
       :display-index
       (map merge (repeat {:starting-width 100}) (:columns props) (:columns @state)))))
   :get-filtered-data
   (fn [{:keys [props refs]}]
     (table-utils/filter-data
      (if (@refs "filter-bar")
        (react/call :apply-filter (@refs "filter-bar"))
        (:data props))
      (:->row props) (:columns props) (react/call :get-filter-text (@refs "filterer"))))
   :get-body-rows
   (fn [{:keys [this state props refs]}]
     (if (zero? (count (.-filtered-data this)))
       []
       (let [[n c] (if (@refs "paginator")
                     (react/call :get-current-slice (@refs "paginator"))
                     [1 initial-rows-per-page])
             rows (map (:->row props) (.-filtered-data this))
             sorted-data (if-let [keyfn (:key-fn @state)] (sort-by keyfn rows) rows)
             ordered-data (if (= :desc (:sort-order @state)) (reverse sorted-data) sorted-data)]
         ;; realize this sequence so errors can be caught early
         (doall (take c (drop (* (dec n) c) ordered-data))))))
   :set-body-rows
   (fn [{:keys [this props state refs]}]
     (set! (.-filtered-data this) (react/call :get-filtered-data this))
     (swap! state assoc :no-data? (zero? (count (.-filtered-data this))))
     (react/call :set-rows (@refs "body") (react/call :get-body-rows this))
     (react/call :set-num-rows-visible (@refs "paginator") (count (.-filtered-data this))))
   :component-did-mount
   (fn [{:keys [this state]}]
     (set! (.-onMouseMoveHandler this)
       (fn [e]
         (when (:dragging? @state)
           (let [current-width (:width (nth (react/call :get-ordered-columns this)
                                            (:drag-column @state)))
                 new-mouse-x (.-clientX e)
                 drag-amount (- new-mouse-x (:mouse-x @state))
                 new-width (+ current-width drag-amount)]
             (when (and (>= new-width 10) (not (zero? drag-amount)))
               ;; Update in a single step like this to avoid multiple re-renders
               (swap! state (fn [s]
                              (assoc-in (assoc s :mouse-x new-mouse-x)
                                        [:columns (:drag-column s) :width] new-width))))))))
     (.addEventListener js/window "mousemove" (.-onMouseMoveHandler this))
     (set! (.-onMouseUpHandler this)
       #(when (:dragging? @state)
         (common/restore-text-selection (:saved-user-select-state @state))
         (swap! state assoc :dragging? false)))
     (.addEventListener js/window "mouseup" (.-onMouseUpHandler this)))
   :component-did-update
   (fn [{:keys [this prev-props props]}]
     (when (not= (:data props) (:data prev-props))
       (react/call :set-body-rows this)))
   :component-will-unmount
   (fn [{:keys [this]}]
     (.removeEventListener js/window "mousemove" (.-onMouseMoveHandler this))
     (.removeEventListener js/window "mouseup" (.-onMouseUpHandler this)))})

;; for attributes referring to a single other entity
;; e.g. samples referring to participants
(defn- is-single-ref? [attr-value]
  (and (map? attr-value)
       (= (set (keys attr-value)) #{"entityType" "entityName"})))

;; for attributes referring to a list of entities
;; e.g. sample sets referring to samples
(defn- is-ref-list? [attr-value]
  (and (sequential? attr-value)
       (map? (first attr-value)) ;; we'll just assume homogeneous lists
       (= (set (keys (first attr-value))) #{"entityType" "entityName"})))

(react/defc EntityTable
  {:get-entity-list
   (fn [{:keys [state]}]
     (get-in @state [:server-response :entity-list]))
   :get-default-props
   (fn []
     {:empty-message "There are no entities to display."
      :attribute-renderer table-utils/default-render})
   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [server-response]} @state
           {:keys [server-error entity-types selected-entity-type entity-list]} server-response]
       [:div {}
        (when (:loading-entities? @state)
          [comps/Blocker {:banner "Loading entities..."}])
        (cond
          server-error (style/create-server-error-message server-error)
          (nil? entity-types) [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Retrieving entity types..."}]]
          :else
          (let [attribute-keys (->> entity-list (map #(-> (% "attributes") keys set)) (apply union))
                attr-col-width (->> attribute-keys count (/ 1000) int (min 400) (max 100))]
            [Table
             (merge props
               {:key (gensym)
                :columns (into
                           [{:header "Entity Name" :starting-width 200
                             :as-text #(% "name") :sort-by :text
                             :content-renderer (or (:entity-name-renderer props)
                                                 (fn [entity] (entity "name")))}]
                           (map (fn [k] {:header k :starting-width attr-col-width :sort-by :text
                                         :as-text
                                         (fn [attr-value]
                                           (cond
                                             (is-single-ref? attr-value) (attr-value "entityName")
                                             (is-ref-list? attr-value) (map #(% "entityName") attr-value)
                                             :else (str attr-value)))
                                         :content-renderer
                                         (fn [attr-value]
                                           (cond
                                             (is-single-ref? attr-value) (attr-value "entityName")
                                             (is-ref-list? attr-value) (join ", " (map #(% "entityName") attr-value))
                                             :else ((:attribute-renderer props) attr-value)))})
                             attribute-keys))
                :filters (mapv (fn [[type count]] {:text type :count count :pred (constantly true)})
                           entity-types)
                :selected-filter-index (max 0 (.indexOf (to-array (map first entity-types))
                                                selected-entity-type))
                :on-filter-change (fn [index]
                                    (let [type (first (nth entity-types index))]
                                      (swap! state update-in [:server-response] assoc :selected-entity-type type)
                                      (react/call :load-type this type)
                                      (when-let [func (:on-filter-change props)]
                                        (func type))))
                :data entity-list
                :->row (fn [m]
                         (into [m] (map (fn [k] (get-in m ["attributes" k])) attribute-keys)))})]))]))
   :component-did-mount
   (fn [{:keys [props state this]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-entity-types (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response]}]
                   (if success?
                     (let [types (vec (get-parsed-response))
                           first-type (or (:initial-entity-type props) (first (first types)))]
                       (swap! state update-in [:server-response]
                         assoc :entity-types types :selected-entity-type first-type)
                       (react/call :load-type this first-type))
                     (swap! state update-in [:server-response]
                       assoc :server-error (get-parsed-response))))}))
   :load-type
   (fn [{:keys [props state]} type]
     (if-not type
       (swap! state update-in [:server-response] assoc :entity-list [])
       (do
         (swap! state assoc :loading-entities? true)
         (endpoints/call-ajax-orch
           {:endpoint (endpoints/get-entities-of-type (:workspace-id props) type)
            :on-done (fn [{:keys [success? get-parsed-response]}]
                       (swap! state update-in [:server-response]
                         assoc (if success? :entity-list :server-error) (get-parsed-response))
                       (swap! state dissoc :loading-entities?)
                       (when-let [func (:on-filter-change props)]
                         (func type)))}))))})
