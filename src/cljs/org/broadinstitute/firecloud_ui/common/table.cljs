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


(def ^:private initial-rows-per-page 10)


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
;;   :resizable-columns? (optional, default true)
;;     Fallback value for column resizing.
;;   :resize-tab-color (optional, default gray)
;;     Color for the drag-to-resize tab
;;   :reorderable-columns? (optional, default true)
;;     Controls whether or not columns are reorderable.  When true, a reorder widget is presented
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
(react/defc Table
  {:get-default-props
   (fn []
     {:pagination :internal
      :paginator-space 24
      :resizable-columns? true
      :reorderable-columns? true
      :sortable-columns? true
      :always-sort? false
      :filterable? true
      :cell-padding-left "16px"
      :row-style (fn [index row]
                   {:backgroundColor (if (even? index) (:background-light style/colors) "#fff")})})
   :get-initial-state
   (fn [{:keys [props]}]
     (persistence/try-restore
      {:key (:state-key props)
       :initial
       (let [columns (vec (map-indexed (fn [i col]
                                         {:width (or (:starting-width col) 100)
                                          :visible? (get col :show-initial? true)
                                          :index i
                                          :display-index i})
                                       (:columns props)))
             initial-sort-column (or (first (filter #(contains? % :sort-initial)
                                                    (map merge (:columns props) columns)))
                                     (when (:always-sort? props)
                                       (first (map merge (:columns props) columns))))]
         {:columns columns
          :dragging? false
          :filter-group-index (or (:initial-filter-group-index props) 0)
          :query-params (merge
                         {:current-page 1 :rows-per-page initial-rows-per-page
                          :filter-text ""}
                         (when initial-sort-column
                           {:sort-column (:index initial-sort-column)
                            ; default needed when forcing sort
                            :sort-order (or (:sort-initial initial-sort-column) :asc)}))})}))
   :render
   (fn [{:keys [this state props refs after-update]}]
     (let [{:keys [filterable? reorderable-columns? toolbar retain-header-on-empty?]} props
           {:keys [no-data? error]} @state
           any-width=remaining? (->> (:columns @state)
                                     (map :width)
                                     (some (partial = :remaining)))]
       [:div {}
        (when (or filterable? reorderable-columns? toolbar)
          (let [built-in
                [:div {:style {:paddingBottom "1em"}}
                 (when reorderable-columns?
                   [:div {:style {:float "left"}}
                    [comps/Button {:icon :settings :title-text "Select Columns..."
                                   :ref "col-edit-button"
                                   :onClick #(swap! state assoc :reordering-columns? true)}]
                    (when (:reordering-columns? @state)
                      [overlay/Overlay
                       {:get-anchor-dom-node #(react/find-dom-node (@refs "col-edit-button"))
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
                                  ; TODO: fix this screwy logic
                                  new-order (utils/move column-order source-index (if (> target-index source-index) (dec target-index) target-index))
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
                 (when filterable?
                   [:div {:style {:float "left" :marginLeft "1em"}}
                    [table-utils/TextFilter {:initial-text (get-in @state [:query-params :filter-text])
                                             :on-filter #(swap! state update-in [:query-params]
                                                                assoc :filter-text % :current-page 1)}]])
                 (when (:filter-groups props)
                   [:div {:style {:float "left" :marginLeft "1em" :marginTop -3}}
                    [table-utils/FilterGroupBar
                     (merge (select-keys props [:filter-groups :data])
                            {:selected-index (:filter-group-index @state)
                             :on-change (fn [new-index]
                                          (swap! state assoc :filter-group-index new-index)
                                          (after-update #(react/call :refresh-rows this))
                                          (when-let [f (:on-filter-change props)]
                                            (f new-index)))})]])
                 (common/clear-both)]]
            ((or toolbar identity) built-in)))
        [:div {:style {:position "relative"}}
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
             :on-change #(swap! state update-in [:query-params] merge %)}]])]))
   :get-ordered-columns
   (fn [{:keys [props state]}]
     (vec
      (sort-by
       :display-index
       (map merge (repeat {:starting-width 100}) (:columns props) (:columns @state)))))
   :refresh-rows
   (fn [{:keys [props state refs]}]
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
                             (let [column (nth (:columns props) sort-column)
                                   key-fn (or (:sort-by column) identity)
                                   key-fn (if (= key-fn :text) (:as-text column) key-fn)]
                               (sort-by (fn [row] (key-fn (nth row sort-column))) rows))
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
   :component-did-mount
   (fn [{:keys [this state]}]
     (react/call :refresh-rows this)
     (set! (.-onMouseMoveHandler this)
           (fn [e]
             (when (:dragging? @state)
               (let [current-width (:width (nth (:columns @state) (:drag-column @state)))
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
             (swap! state dissoc :dragging? :drag-column :mouse-x :saved-user-select-state)))
     (.addEventListener js/window "mouseup" (.-onMouseUpHandler this)))
   :component-did-update
   (fn [{:keys [this prev-props props prev-state state]}]
     (when (or (not= (:data props) (:data prev-props))
               (not= (:query-params @state) (:query-params prev-state)))
       (react/call :refresh-rows this))
     (when (and (:state-key props)
                (not (:dragging? @state)))
       (persistence/save {:key (:state-key props) :state state :except [:display-rows]})))
   :component-will-unmount
   (fn [{:keys [this]}]
     (.removeEventListener js/window "mousemove" (.-onMouseMoveHandler this))
     (.removeEventListener js/window "mouseup" (.-onMouseUpHandler this)))})
