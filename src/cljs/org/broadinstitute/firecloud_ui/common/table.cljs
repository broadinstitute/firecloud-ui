(ns org.broadinstitute.firecloud-ui.common.table
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.overlay :as overlay]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table-utils :as table-utils]
    [org.broadinstitute.firecloud-ui.persistence :as persistence]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(def ^:private default-initial-rows-per-page 20)

(def persistence-keys #{:column-meta :query-params :filter-group-index})


(defn date-column [props]
  {:header (or (:header props) "Create Date")
   :starting-width (or (:starting-width props) 200)
   :as-text #(common/format-date % (:format props))})

;; Table component with specifiable style and column behaviors.
;;
;; Properties:
;;   :pagination (optional, default :internal)
;;     Defines how the table is paginated.  Options are:
;;       :internal -- data is given via the :data property and client-side pagination is provided
;;       :none -- don't paginate
;;       (fn [query-params callback] ...) -- paginate externally (e.g. server-side) by providing a function
;;           that takes the query parameters and sets the data via a callback.
;;           query-params structure:
;;             {:current-page <1-indexed int>
;;              :rows-per-page <positive int>
;;              :filter-text <possibly empty string>
;;              :sort-column <0-indexed int>
;;              :sort-order <:asc or :desc>
;;              :filter-group-index <the selected 0-indexed filter group, ignore if filter groups not used>}
;;           To complete, call `callback` providing:
;;             {:group-count <size of the filter group (size of all data if not using filter groups)>
;;              :filtered-count <number of rows post-filtering>
;;              :rows <the row data>}
;;   :paginator-space (optional, default 24 or unused)
;;     A CSS padding value used to separate the table and paginator.
;;   :initial-rows-per-page (optional, default 20 or unused)
;;     The default number of rows to show if the table has a paginator.
;;   :resizable-columns? (optional, default true)
;;     Fallback value for column resizing.
;;   :resize-tab-color (optional, default gray)
;;     Color for the drag-to-resize tab
;;   :reorderable-columns? (optional, default true)
;;     Controls whether or not columns are reorderable.  When true, a reorder widget is presented
;;   :reorder-anchor (optional, default :left)
;;     Which side to anchor the reordering overlay.  Set to :right if placing the widget on the right side.
;;   :reorder-style (optional, no default style)
;;     Applies style properties to the displayed columns in the reorder widget
;;   :reorder-prefix (optional, nil)
;;     Prefixes the widget with the provided text
;;   :sortable-columns? (optional, default true)
;;     Fallback value for column sorting.
;;   :always-sort? (optional, default false)
;;     Set to 'true' to force a column to always be sorting.  If no :initial-sort column is specified
;;     then the first column will sort :asc initially.
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
;;   :cell-padding-left (optional, default 16px)
;;     A CSS padding-left value to apply to each cell
;;   :header-row-style (optional)
;;     Style to apply to the header row.  When omitted, style is a dark gray background with bold white text
;;   :toolbar (optional)
;;     Use to provide more items in the toolbar, along with the filterer and column reorderer (if present).
;;     This value should be a function that takes the built-in elements as a map:
;;     {:reorderer <column reorder component>
;;      :filterer <filter component>
;;      :filter-groups <filter group component>}
;;     and returns a DOM element to be used for the toolbar.  See table-utils for more.
;;   :width (optional, default normal)
;;     Specify :width :narrow to make the paginator layout in a narrow width friendly way.  Any other value
;;     (including none) corresponds to the normal layout
;;   :columns (REQUIRED)
;;     A sequence of column maps.  The order given is used as the initial order.
;;     Columns have the following properties:
;;       :header (optional, default none)
;;         The text to display.
;;       :header-key (optional)
;;         A serializable identifier for when the header is not persistable
;;       :reorderable? (optional, default true)
;;         Whether or not the column should be allowed to be reordered.  Unused if :reorderable-columns?
;;         is false on the table.
;;       :show-initial? (optional, default true)
;;         Whether or not to initially show the column.
;;       :starting-width (optional, default 100)
;;         The initial width, which may be resized.  Use :remaining to make it take up the remaining space.
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
;;   :filter-groups (optional)
;;     A vector of filter groups to apply to the data. Each item as the following properties:
;;       :text (REQUIRED)
;;         A label for the filter.
;;       :pred (REQUIRED)
;;         A function that, given a data item, returns true if that item matches the filter.
;;       :count (optional)
;;         Use to override the displayed count, which normally uses the :pred
;;   :initial-filter-group-index (optional)
;;     Initially selected filter group.
;;   :on-filter-change (optional)
;;     A function called when the active filter is changed. Passed the new filter index.
;;   :data (REQUIRED unless paginating externally)
;;     A sequence items that will appear in the table.
;;   :num-total-rows (optional)
;;     The total number of rows that would normally be shown in the table.  Specify if you are externally
;;     filtering; this value will show up in the paginator.
;;   :->row (REQUIRED)
;;     A function that takes a data item and returns a vector representing a table row.

(defn- get-default-table-props []
  {:pagination :internal
   :paginator-space 24
   :initial-rows-per-page default-initial-rows-per-page
   :resizable-columns? true
   :reorderable-columns? true
   :reorder-anchor :left
   :sortable-columns? true
   :always-sort? false
   :filterable? true
   :cell-padding-left "16px"
   :row-style (fn [index row]
                {:backgroundColor (if (even? index) (:background-light style/colors) "#fff")})})

(defn- get-initial-table-state [{:keys [props]}]
  (merge
   {:given-columns-by-header (->> (:columns props)
                                  (map-indexed (fn [index {:keys [header header-key] :as col}]
                                                 [(or header-key header) (assoc col :declared-index index)]))
                                  (into {}))
    :dragging? false}
   (let [restored
         (persistence/try-restore
          {:key (:state-key props)
           :validator (fn [stored-value] (= (set (keys stored-value)) persistence-keys))
           :initial (fn []
                      (let [processed-columns (if-let [defaults (:column-defaults props)]
                                                (let [by-header (utils/index-by :header (:columns props))
                                                      default-showing (try
                                                                        (doall
                                                                         (->> defaults
                                                                              (replace by-header)
                                                                              (map #(assoc % :show-initial? true))))
                                                                        (catch :default e
                                                                          (map #(assoc % :show-initial? true) (:columns props))))
                                                      default-hiding (as-> by-header $
                                                                           (apply dissoc $ defaults)
                                                                           (vals $)
                                                                           (map #(assoc % :show-initial? false) $))]
                                                  (concat default-showing default-hiding))
                                                (:columns props))
                            column-meta (mapv (fn [{:keys [header header-key starting-width] :as col}]
                                                {:header (or header-key header)
                                                 :width (or starting-width 100)
                                                 :visible? (get col :show-initial? true)})
                                              processed-columns)
                            initial-sort-column (or (some->> (:columns props)
                                                             (filter #(contains? % :sort-initial))
                                                             first)
                                                    (when (:always-sort? props)
                                                      (first (:columns props))))]
                        {:column-meta column-meta
                         :filter-group-index (get props :initial-filter-group-index 0)
                         :query-params (merge
                                        {:current-page 1 :rows-per-page (:initial-rows-per-page props)
                                         :filter-text ""}
                                        (when initial-sort-column
                                          {:sort-column (:header initial-sort-column)
                                           ; default needed when forcing sort
                                           :sort-order (or (:sort-initial initial-sort-column) :asc)}))}))})]

     (update restored :column-meta
             (fn [cols]
               (let [headers-restored (set (map #(or (:header-key %) (:header %)) cols))
                     col-headers (map #(or (:header-key %) (:header %)) (:columns props))
                     unmentioned-headers (set (remove #(contains? headers-restored %) col-headers))
                     unmentioned-cols (->> (:columns props)
                                           (filter #(contains? unmentioned-headers (:header %)))
                                           (map #(select-keys % [:header]))
                                           (map #(assoc % :width 100 :visible? true)))]

                 (vec (concat cols unmentioned-cols))))))))

(defn- render-table [{:keys [this state props refs after-update]}]
  (assert (vector? (:column-meta @state)) "column-meta got un-vec'd")
  (let [{:keys [filterable? reorderable-columns? toolbar retain-header-on-empty?]} props
        {:keys [no-data? error]} @state
        any-width=remaining? (->> (:column-meta @state)
                                  (map :width)
                                  (some (partial = :remaining)))]
    [:div {}
     (when (or filterable? reorderable-columns? toolbar)
       (let [reorderer (when reorderable-columns?
                         [:div {:style {:marginRight "1em"}}
                          (when-let [prefix (:reorder-prefix props)]
                            [:div {:style {:display "inline" :cursor "pointer" :marginRight ".5em"}
                                   :onClick #(swap! state assoc :reordering-columns? true)} prefix])
                          [comps/Button {:icon :settings :title-text "Select Columns..."
                                         :ref "col-edit-button"
                                         :onClick #(swap! state assoc :reordering-columns? true)}]
                          (when (:reordering-columns? @state)
                            [overlay/Overlay
                             {:get-anchor-dom-node #(react/find-dom-node (@refs "col-edit-button"))
                              :dismiss-self #(swap! state assoc :reordering-columns? false)
                              :anchor-x (:reorder-anchor props)
                              :content
                              (react/create-element
                                table-utils/ColumnEditor
                                {:columns (react/call :get-ordered-columns this)
                                 :on-reorder
                                 (fn [source-index target-index]
                                   (swap! state update :column-meta utils/move source-index target-index))
                                 :on-visibility-change
                                 (fn [column-index visible?]
                                   (if (= :all column-index)
                                     (swap! state update :column-meta #(vec (map merge % (repeat {:visible? visible?}))))
                                     (swap! state assoc-in [:column-meta column-index :visible?] visible?)))
                                 :reorder-style (:reorder-style props)})}])])
             filter (when filterable?
                      [:div {:style {:marginRight "1em"}}
                       [comps/TextFilter {:initial-text (get-in @state [:query-params :filter-text])
                                          :on-filter #(swap! state update-in [:query-params]
                                                             assoc :filter-text % :current-page 1)}]])
             filter-groups (when (:filter-groups props)
                             [:div {:style {:marginRight "1em"}}
                              [table-utils/FilterGroupBar
                               (merge (select-keys props [:filter-groups :data])
                                      {:selected-index (:filter-group-index @state)
                                       :on-change (fn [new-index]
                                                    (swap! state assoc :filter-group-index new-index)
                                                    (after-update #(react/call :refresh-rows this))
                                                    (when-let [f (:on-filter-change props)]
                                                      (f new-index)))})]])
             params {:reorderer reorderer :filter filter :filter-groups filter-groups}]
         ((or toolbar (table-utils/default-toolbar-layout)) params)))
     [:div {}
      [comps/DelayedBlocker {:ref "blocker" :banner "Loading..."}]
      ;; When using an auto-width column the table ends up ~1px wider than its parent
      [:div {:style {:overflowX (if any-width=remaining? "hidden" "auto")}}
       [:div {:style {:position "relative"
                      :paddingBottom 10
                      :minWidth (when-not (or no-data? any-width=remaining?)
                                  (->> (react/call :get-ordered-columns this)
                                       (filter :visible?)
                                       (map :width)
                                       (apply +)))
                      :cursor (when (:dragging? @state) "col-resize")}}
        (when (or (not no-data?) retain-header-on-empty?)
          (table-utils/render-header state props this))
        (when error
          [:div {:style {:padding "0.5em"}}
           (style/create-server-error-message error)])
        (when (and (not error) no-data?)
          (style/create-message-well (or (:empty-message props) "There are no rows to display.")))
        [table-utils/Body
         (assoc props
           :columns (filter :visible? (react/call :get-ordered-columns this))
           :rows (:display-rows @state))]]]]
     (when-not (= (:pagination props) :none)
       [:div {:style {:paddingTop (:paginator-space props)}}
        [table-utils/Paginator
         {:width (:width props)
          :pagination-params (select-keys (:query-params @state) [:current-page :rows-per-page])
          :num-visible-rows (:filtered-count @state)
          :num-total-rows (or (:num-total-rows props) (:grouped-count @state))
          :on-change #(swap! state update-in [:query-params] merge %)
          :initial-rows-per-page (get-in @state [:query-params :rows-per-page])}]])]))

(defn- refresh-table-rows [{:keys [props state refs]}]
  (react/call :show (@refs "blocker"))
  (let [{:keys [pagination data ->row]} props]
    (if (fn? pagination)
      (pagination (merge (select-keys @state [:filter-group-index]) (:query-params @state))
                  (fn [{:keys [group-count filtered-count rows error]}]
                    (react/call :hide (@refs "blocker"))
                    (swap! state assoc
                           :grouped-count group-count
                           :filtered-count filtered-count
                           :display-rows (map ->row rows)
                           :no-data? (empty? rows)
                           :error error)))
      (let [{:keys [current-page rows-per-page sort-column sort-order filter-text]} (:query-params @state)
            grouped-data (if-not (:filter-groups props)
                           data
                           (filter (:pred (get-in props [:filter-groups (:filter-group-index @state)]))
                                   data))
            filtered-data (if-let [txt (not-empty filter-text)]
                            (table-utils/filter-data grouped-data ->row (:columns props) txt)
                            grouped-data)
            rows (map ->row filtered-data)
            sorted-rows (if sort-column
                          (let [column (get (:given-columns-by-header @state) sort-column)
                                column-index (utils/first-matching-index (fn [{:keys [header]}] (= header sort-column)) (:column-meta @state))
                                key-fn (or (:sort-by column) identity)
                                key-fn (if (= key-fn :text) (:as-text column) key-fn)]
                            (sort-by (fn [row] (key-fn (nth row column-index))) rows))
                          rows)
            ordered-rows (if (= :desc sort-order) (reverse sorted-rows) sorted-rows)
            ;; realize this sequence so errors can be caught early:
            clipped-rows (if (= pagination :none)
                           ordered-rows
                           (doall (take rows-per-page (drop (* (dec current-page) rows-per-page) ordered-rows))))]
        (react/call :hide (@refs "blocker"))
        (swap! state assoc
               :grouped-count (count grouped-data)
               :filtered-count (count filtered-data)
               :display-rows clipped-rows
               :no-data? (empty? clipped-rows))))))

(defn- table-component-did-mount [{:keys [this state locals]}]
  (swap! locals assoc :initial-state (select-keys @state persistence-keys))
  (react/call :refresh-rows this)
  (set! (.-onMouseMoveHandler this)
        (fn [e]
          (when (:dragging? @state)
            (let [{:keys [drag-column mouse-x]} @state
                  current-width (:width (nth (:column-meta @state) drag-column))
                  new-mouse-x (.-clientX e)
                  drag-amount (- new-mouse-x mouse-x)
                  new-width (+ current-width drag-amount)]
              (when (and (>= new-width 10) (not (zero? drag-amount)))
                ;; Update in a single step like this to avoid multiple re-renders
                (swap! state #(-> %
                                  (assoc :mouse-x new-mouse-x)
                                  (assoc-in [:column-meta drag-column :width] new-width))))))))
  (.addEventListener js/window "mousemove" (.-onMouseMoveHandler this))
  (set! (.-onMouseUpHandler this)
        #(when (:dragging? @state)
          (common/restore-text-selection (:saved-user-select-state @state))
          (swap! state dissoc :dragging? :drag-column :mouse-x :saved-user-select-state)))
  (.addEventListener js/window "mouseup" (.-onMouseUpHandler this)))

(defn- table-component-did-update [{:keys [this prev-props props prev-state state locals]}]
  (when (or (not= (:data props) (:data prev-props))
            (not= (:query-params @state) (:query-params prev-state)))
    (react/call :refresh-rows this))
  (when (and (:state-key props)
             (not (:dragging? @state))
             (not (= (select-keys @state persistence-keys) (:initial-state @locals))))
    (persistence/save {:key (:state-key props) :state state :only [:column-meta :query-params :filter-group-index]})))


(react/defc Table
  {:get-query-params
   (fn [{:keys [state]}]
     (:query-params @state))
   :update-query-params
   (fn [{:keys [state]} new-params]
     (swap! state update :query-params merge new-params))
   :get-default-props get-default-table-props
   :get-initial-state get-initial-table-state
   :render render-table
   :get-ordered-columns
   (fn [{:keys [state]}]
     (->> (:column-meta @state)
          (map (fn [{:keys [header] :as column-meta}]
                 (merge column-meta (get (:given-columns-by-header @state) header))))
          vec))
   :refresh-rows #(refresh-table-rows %)
   :component-did-mount table-component-did-mount
   :component-did-update table-component-did-update
   :component-will-unmount
   (fn [{:keys [this]}]
     (.removeEventListener js/window "mousemove" (.-onMouseMoveHandler this))
     (.removeEventListener js/window "mouseup" (.-onMouseUpHandler this)))})
