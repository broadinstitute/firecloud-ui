(ns org.broadinstitute.firecloud-ui.common.table
  (:require
    [clojure.string :refer [trim]]
    [dmohs.react :as react]
    [inflections.core :refer [pluralize]]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(def ^:private initial-rows-per-page 10)

(defn- create-page-range [current-page total-pages]
  (cond
    (<= total-pages 5) (range 1 (inc total-pages))
    (<= current-page 3) (range 1 6)
    (>= current-page (- total-pages 2)) (range (- total-pages 4) (inc total-pages))
    :else (range (- current-page 2) (+ current-page 3))))

(react/defc Paginator
  {:get-current-slice
   (fn [{:keys [state]}]
     [(:current-page @state) (:rows-per-page @state)])
   :set-num-rows-visible
   (fn [{:keys [state]} n]
     (when (not= n (:num-rows-visible @state))
       (swap! state assoc :num-rows-visible n :current-page 1)))
   :get-initial-state
   (fn [{:keys [props]}]
     {:rows-per-page initial-rows-per-page
      :current-page 1
      :num-rows-visible (:num-total-rows props)})
   :render
   (fn [{:keys [props state refs]}]
     (let [rows-per-page (:rows-per-page @state)
           current-page (:current-page @state)
           num-rows-visible (:num-rows-visible @state)
           num-pages (js/Math.ceil (/ num-rows-visible rows-per-page))
           allow-prev (> current-page 1)
           allow-next (< current-page num-pages)
           right-num (min num-rows-visible (* current-page rows-per-page))
           left-num (if (zero? right-num) 0 (inc (* (dec current-page) rows-per-page)))]
       [:div {:style {:border "1px solid #ebebeb" :boxShadow "-3px -6px 23px -7px #ebebeb inset"}}
        [:div {:style {:fontSize 13 :lineHeight 1.5 :padding "0px 48px" :verticalAlign "middle"}}

         [:div {:style {:float "left" :display "inline-block" :width "33.33%" :padding "2.15em 0em"
                        :verticalAlign "middle"}}
          [:b {} (str left-num " - " right-num)]
          (str " of " (pluralize num-rows-visible " result")
               (when-not (= num-rows-visible (:num-total-rows props))
                 (str " (filtered from " (:num-total-rows props) " total)")))]
         (style/create-unselectable
          :div {:style {:float "left" :display "inline-block" :width "33.33%"
                        :padding "1.6em 0em" :verticalAlign "middle" :textAlign "center"}}
           [:div {:style {:display "inline-block" :padding "0.55em 0.9em"
                          :color (if allow-prev
                                   (:button-blue style/colors)
                                   (:border-gray style/colors))
                          :cursor (when allow-prev "pointer")}
                  :onClick (when allow-prev #(swap! state update-in [:current-page] dec))}
            (icons/font-icon {:style {:fontSize "70%"}} :angle-left)
            [:span {:style {:paddingLeft "1em"}} "Prev"]]
           [:span {}
            (map (fn [n]
                   (let [selected? (= n current-page)]
                     [:div {:style {:paddingTop 5 :display "inline-block" :width 29 :height 24
                                    :backgroundColor (when selected? (:button-blue style/colors))
                                    :color (if selected? "white" (:button-blue style/colors))
                                    :borderRadius (when selected? "100%")
                                    :cursor (when-not selected? "pointer")}
                            :onClick (when-not selected? #(swap! state assoc :current-page n))}
                      n]))
              (create-page-range current-page num-pages))]
           [:div {:style {:display "inline-block" :padding "0.55em 0.9em"
                          :color (if allow-next
                                   (:button-blue style/colors)
                                   (:border-gray style/colors))
                          :cursor (when allow-next "pointer")}
                  :onClick (when allow-next #(swap! state update-in [:current-page] inc))}
            [:span {:style {:paddingRight "1em"}} "Next"]
            (icons/font-icon {:style {:fontSize "70%"}} :angle-right)])
         [:div {:style {:float "left" :display "inline-block" :width "33.33%"
                        :padding "2.15em 0em" :textAlign "right"}}
          "Display"
          (style/create-select
           {:style {:width 60 :margin "0em 1em"} :ref "numRows"
            :onChange #(swap! state assoc
                              :rows-per-page (js/parseInt
                                               (-> (@refs "numRows") .getDOMNode .-value))
                              :current-page 1)}
            [10 25 100 500])
          "rows per page"]
         (common/clear-both)]]))
   :component-did-update
   (fn [{:keys [props state prev-state]}]
     (when-not (and (apply = (map :rows-per-page [prev-state @state]))
                    (apply = (map :current-page [prev-state @state])))
       ((:onChange props))))})


(defn- render-cell [{:keys [width onResizeMouseDown onSortClick sortOrder] :as props}]
  [:div {:style (merge {:float "left" :position "relative" :width width :minWidth 10}
                  (:cell-style props))}
   (when (:onResizeMouseDown props)
     [:div {:style {:position "absolute" :width 20 :top 0 :bottom 0 :left (- width 10) :zIndex 1
                    :cursor "col-resize"}
            :onMouseDown onResizeMouseDown
            :onDoubleClick (:onResizeDoubleClick props)}])
   (when onSortClick
     [:div {:style {:position "absolute" :top 0 :bottom 0 :left 0 :width (if onResizeMouseDown (- width 10) width)
                    :cursor "pointer"}
            :onClick onSortClick}])
   (when sortOrder
     [:div {:style {:position "absolute" :top "50%" :right 0 :width 16 :transform "translateY(-50%)"}}
      (if (= :asc sortOrder) "↑" "↓")])
   [:div {:style (merge {:whiteSpace "nowrap" :overflow "hidden" :textOverflow "ellipsis"
                         :width (str "calc(" (- width (if sortOrder 16 0)) "px - " (:cell-padding-left props) ")")}
                   (:content-container-style props))}
    (:content props)]])


(defn- render-header [state props this]
  [:div {:style (merge
                  {:fontWeight 500 :fontSize "80%"
                   :color "#fff" :backgroundColor (:header-darkgray style/colors)}
                  (:header-row-style props))}
   (map-indexed
     (fn [display-index column]
       (let [i (:index column)
             onResizeMouseDown
             (when (get column :resizable? (:resizable-columns? props))
               (fn [e]
                 (swap! state assoc :dragging? true :mouse-x (.-clientX e) :drag-column display-index
                   :saved-user-select-state (common/disable-text-selection))))]
         (render-cell
           {:width (:width column)
            :content (:header column)
            :cell-style (when onResizeMouseDown {:borderRight "1px solid #777777" :marginRight -1})
            :cell-padding-left (or (:cell-padding-left props) 0)
            :content-container-style (merge
                                       {:padding (str "0.8em 0 0.8em " (or (:cell-padding-left props) 0))}
                                       (:header-style props))
            :onResizeMouseDown onResizeMouseDown
            :onResizeDoubleClick #(swap! state update-in [:ordered-columns display-index]
                                     assoc :width (:starting-width column))
            :onSortClick (when (and (or (:sort-by column)
                                        (:sortable-columns? props))
                                    (not= :none (:sort-by column)))
                           (fn [e]
                             (if (= i (:sort-column @state))
                               (case (:sort-order @state)
                                 :asc (swap! state assoc :sort-order :desc)
                                 :desc (swap! state dissoc :sort-column :sort-order :key-fn)
                                 (assert false "bad state"))
                               (swap! state assoc :sort-column i :sort-order :asc
                                 :key-fn (let [sort-fn (or (:sort-by column) identity)]
                                           (if (= sort-fn :text)
                                             (fn [row] ((:as-text column) (nth row i)))
                                             (fn [row] (sort-fn (nth row i)))))))
                             (react/call :set-body-rows this)))
            :sortOrder (when (= i (:sort-column @state)) (:sort-order @state))})))
     (filter :showing? (:ordered-columns @state)))
   (common/clear-both)])


(defn- default-render [data]
  (cond (map? data) (utils/map-to-string data)
        (sequential? data) (clojure.string/join ", " data)
        :else data))


(react/defc Body
  {:set-rows
   (fn [{:keys [state]} rows]
     (swap! state assoc :rows rows))
   :get-initial-state
   (fn [{:keys [props]}]
     {:rows (:initial-rows props)})
   :render
   (fn [{:keys [props state]}]
     [:div {:style (merge {:fontSize "80%" :fontWeight 500} (:body-style props))}
      (map-indexed
        (fn [row-index row]
          (let [row-style (merge
                            (:row-style props)
                            (if (even? row-index)
                              (merge
                                {:backgroundColor (:background-gray style/colors)}
                                (:even-row-style props))
                              (:odd-row-style props)))]
            [:div {:style row-style}
             (map
               (fn [col]
                 (let [render-content (or (:content-renderer col)
                                          (:as-text col)
                                          default-render)]
                   (render-cell
                     {:width (:width col)
                      :content (render-content (nth row (:index col)))
                      :cell-padding-left (or (:cell-padding-left props) 0)
                      :content-container-style (merge
                                                 {:padding (str "0.6em 0 0.6em " (or (:cell-padding-left props) 0))}
                                                 (:cell-content-style props))})))
               (:columns props))
             (common/clear-both)]))
        (:rows @state))])})


(react/defc Filterer
  {:get-filter-text
   (fn [{:keys [refs]}]
     (common/get-text refs "filter-field"))
   :get-initial-state
   (fn [] {:initial true :synced true})
   :render
   (fn [{:keys [state this]}]
     [:div {}
      (style/create-text-field
        {:ref "filter-field" :placeholder "Filter"
         :style {:backgroundColor (if (:synced @state) "#fff" (:tag-background style/colors))}
         :onKeyDown (common/create-key-handler
                      [:enter] #(react/call :apply-filter this))
         :onChange #(swap! state assoc :initial false :synced false)})
      [:span {:style {:paddingLeft "1em"}}]
      [comps/Button {:icon :search :onClick #(react/call :apply-filter this)}]])
   :apply-filter
   (fn [{:keys [this state props]}]
     (swap! state assoc :synced true)
     (let [text (react/call :get-filter-text this)]
       (when (empty? text) (swap! state assoc :initial true))
       ((:onFilter props) text)))})


(react/defc ColumnEditor
  {:render
   (fn [{:keys [props state refs]}]
     [:div {:style {:border (str "2px solid " (:line-gray style/colors))
                    :padding "1em" :lineHeight "1.5em" :cursor (when (:drag-active @state) "ns-resize")}
            :onMouseMove (when (:drag-index @state)
                           (fn [e]
                             (let [x (.-clientX e)
                                   y (.-clientY e)
                                   dist (utils/distance (:start-x @state) (:start-y @state) x y)
                                   div-locs (map
                                              (fn [i] {:index i :y
                                                       (-> (@refs (str "div" i))
                                                         .getDOMNode .getBoundingClientRect .-top)})
                                              (range (inc (count (:columns props)))))
                                   closest-div (apply min-key #(js/Math.abs (- y (:y %))) div-locs)]
                               (when (not= (:index closest-div) (:drop-index @state))
                                 (swap! state assoc :drop-index (:index closest-div)))
                               (when (and (not (:drag-active @state)) (> dist 5.0))
                                 (swap! state assoc :drag-active true)))))
            :onMouseUp (when (:drag-index @state)
                         (fn [e]
                           ((:submit props)
                             (utils/move (:columns props) (:drag-index @state) (:drop-index @state)))
                           (swap! state dissoc :drag-index :drag-active :drop-index)))}
      "Show:"
      (let [style {:padding "4px 8px" :marginRight 5 :borderRadius 5
                   :cursor (when-not (:drag-active @state) "pointer")
                   :backgroundColor (:button-blue style/colors) :color "#fff"}
            submit (fn [b] (fn [] ((:submit props) (mapv #(assoc % :showing? b) (:columns props)))))]
        [:div {:style {:padding "0.5em 0"}}
         [:span {:style style :onClick (submit true)} "All"]
         [:span {:style style :onClick (submit false)} "None"]])
      (map-indexed
        (fn [i column]
          (when (get column :reorderable? true)
            (let [showing? (:showing? column)]
              [:div {:ref (str "div" i)
                     :style {:borderTop (when (= i (:drop-index @state)) "1px solid gray")}}
               [:img {:src "assets/drag_temp.png"
                      :style {:height 16 :verticalAlign "middle" :marginRight "1ex" :cursor "ns-resize"}
                      :draggable false
                      :onMouseDown (fn [e] (swap! state assoc :drag-index i
                                             :start-x (.-clientX e) :start-y (.-clientY e)))}]
               [:label {:style {:cursor (when-not (:drag-active @state) "pointer")}}
                [:input {:type "checkbox" :checked showing?
                         :onChange #((:submit props)
                                     (update-in (:columns props) [i] assoc :showing? (not showing?)))}]
                [:span {:style {:paddingLeft "0.5em"}} (:header column)]]])))
        (:columns props))
      (let [i (count (:columns props))]
        [:div {:ref (str "div" i)
               :style {:borderTop (when (= i (:drop-index @state)) "1px solid gray")}}])])})


(defn- filter-data [data columns filter-text]
  (if (empty? filter-text)
    data
    (filter (fn [row]
              (utils/matches-filter-text
               (apply str (map-indexed
                           (fn [i column]
                             (let [func (or (:filter-by column)
                                            (:as-text column)
                                            str)]
                               (if (= func :none) "" (func (nth row i)))))
                           columns))
               filter-text))
            data)))


(defn- create-ordered-columns [columns]
  (vec (map-indexed
        (fn [index col]
          (assoc col
                 :index index :showing? true
                 :width (or (:starting-width col) 100)
                 :starting-width (or (:starting-width col) 100)))
        columns)))


(defn date-column [props]
  {:header (or (:header props) "Create Date")
   :starting-width (or (:starting-width props) 200)
   :as-text #(common/format-date % (:format props))})


;; Table component with specifiable style and column behaviors.
;;
;; Properties:
;;   :cell-padding-left (optional, default 16px)
;;     A CSS padding-left value to apply to each cell
;;   :paginator (optional, default :below)
;;     Either :above or :below, determines where the paginator appears relative to the table
;;   :paginator-space (optional, default 24)
;;     A CSS padding value used to separate the table and paginator.
;;   :resizable-columns? (optional, default true)
;;     Fallback value for column resizing.
;;   :reorderable-columns? (optional, default true)
;;     Controls whether or not columns are reorderable.  When true, a reorder widget is presented
;;   :sortable-columns? (optional, default true)
;;     Fallback value for column sorting.
;;   :filterable? (optional, default true)
;;     Controls whether or not columns are filterable.  When true, a filter widget is presented
;;   :empty-message (optional, default "There are no rows to display.")
;;     A banner to display when the table is empty
;;   :row-style (optional)
;;     Style to apply to each row.  Properties overridden by :even-row-style and :odd-row-style.
;;     When row styling is omitted, default properties create alternating white and gray backgrounds
;;   :even-row-style (optional)
;;     Style to apply to even-numbered rows.  Properties override :row-style
;;   :odd-row-style (optional)
;;     Style to apply to odd-numbered-rows.  Properties override :row-style
;;   :header-row-style (optional)
;;     Style to apply to the header row.  When omitted, style is a dark gray background with bold white text
;;   :toolbar (optional)
;;     Use to provide more items in the toolbar, along with the filterer and column reorderer (if present).
;;     This value should be a function that takes the "built-in" toolbar as a parameter, and returns an
;;     HTML element.  If this property is not supplied, the built-in toolbar is placed as normal.
;;   :columns (REQUIRED)
;;     A sequence of column maps.  The order given is used as the initial order.
;;     Columns have the following properties:
;;       :header (optional, default none)
;;         The text to display.
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
;;   :data (REQUIRED)
;;     A sequence-of-sequences forming the data grid.
(react/defc Table
  {:get-default-props
   (fn []
     {:cell-padding-left "16px"
      :paginator :below
      :paginator-space 24
      :resizable-columns? true
      :reorderable-columns? true
      :sortable-columns? true
      :filterable? true
      :empty-message "There are no rows to display."
      :toolbar identity})
   :get-initial-state
   (fn [{:keys [props]}]
     (let [ordered-columns (create-ordered-columns (:columns props))]
       (merge
         {:ordered-columns ordered-columns
          :dragging? false}
         (when-let [col (first (filter #(contains? % :sort-initial) ordered-columns))]
           {:key-fn (if-let [sorter (:sort-by col)]
                      (fn [row] (sorter (nth row (:index col))))
                      (fn [row] (nth row (:index col))))
            :sort-order (:sort-initial col)
            :sort-column (:index col)}))))
   :render
   (fn [{:keys [this state props refs]}]
     (let [paginator-above (= :above (:paginator props))
           paginator-below (= :below (:paginator props))
           paginator [Paginator {:ref "paginator"
                                 :num-total-rows (count (:data props))
                                 :onChange #(react/call :set-body-rows this)}]]
       [:div {}
        (when (or (:filterable? props) (:reorderable-columns? props) (:toolbar props))
          (let [built-in [:div {:style {:paddingBottom "1em"}}
                          (when (:filterable? props)
                            [:div {:style {:float "left" :marginLeft "1em"}}
                             [Filterer {:ref "filterer" :onFilter #(react/call :set-body-rows this)}]])
                          (when (:reorderable-columns? props)
                            [:div {:style {:float "left" :marginLeft "1em"}}
                             [comps/Button {:icon :gear :title-text "Select Columns..." :ref "col-edit-button"
                                            :onClick #(swap! state assoc :reordering-columns? true)}]
                             (when (:reordering-columns? @state)
                               [comps/Dialog {:get-anchor-dom-node #(.getDOMNode (@refs "col-edit-button"))
                                              :blocking? false
                                              :dismiss-self #(swap! state assoc :reordering-columns? false)
                                              :content (react/create-element
                                                         ColumnEditor
                                                         {:columns (:ordered-columns @state)
                                                          :submit #(swap! state assoc :ordered-columns %)})}])])
                          (common/clear-both)]]
            ((:toolbar props) built-in)))
        (if (zero? (count (:data props)))
          (style/create-message-well (:empty-message props))
          [:div {}
           (when paginator-above [:div {:style {:paddingBottom (:paginator-space props)}} paginator])
           [:div {:style {:overflowX "auto"}}
            [:div {:style {:position "relative"
                           :paddingBottom 10
                           :minWidth (reduce
                                       + (map :width (filter :showing? (:ordered-columns @state))))
                           :cursor (when (:dragging? @state) "col-resize")}}
             (render-header state props this)
             [Body (assoc props
                     :ref "body"
                     :columns (filter :showing? (:ordered-columns @state))
                     :initial-rows (react/call :get-body-rows this (:data props) true))]]]
           (when paginator-below [:div {:style {:paddingTop (:paginator-space props)}} paginator])])]))
   :get-filtered-data
   (fn [{:keys [props refs]}]
     (filter-data (:data props) (:columns props)
                  (react/call :get-filter-text (@refs "filterer"))))
   :get-body-rows
   (fn [{:keys [state props refs]} filtered-data & [initial-render?]]
     (if (zero? (count (:data props)))
       []
       (let [[n c] (if initial-render?
                     [1 initial-rows-per-page]
                     (react/call :get-current-slice (@refs "paginator")))
             sorted-data (if-let [keyfn (:key-fn @state)] (sort-by keyfn filtered-data) filtered-data)
             ordered-data (if (= :desc (:sort-order @state)) (reverse sorted-data) sorted-data)]
         (take c (drop (* (dec n) c) ordered-data)))))
   :set-body-rows
   (fn [{:keys [this props refs]}]
     (when (pos? (count (:data props)))
       (let [rows (react/call :get-filtered-data this)]
         (react/call :set-rows (@refs "body") (react/call :get-body-rows this rows))
         (react/call :set-num-rows-visible (@refs "paginator") (count rows)))))
   :component-did-mount
   (fn [{:keys [this state]}]
     (set! (.-onMouseMoveHandler this)
       (fn [e]
         (when (:dragging? @state)
           (let [current-width (:width (nth (:ordered-columns @state) (:drag-column @state)))
                 new-mouse-x (.-clientX e)
                 drag-amount (- new-mouse-x (:mouse-x @state))
                 new-width (+ current-width drag-amount)]
             (when (and (>= new-width 10) (not (zero? drag-amount)))
               ;; Update in a single step like this to avoid multiple re-renders
               (let [new-state (assoc @state :mouse-x new-mouse-x)
                     new-state (update-in new-state [:ordered-columns (:drag-column @state)]
                                 assoc :width new-width)]
                 (reset! state new-state)))))))
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
