(ns org.broadinstitute.firecloud-ui.common.table-utils
  (:require
   [dmohs.react :as react]
   [inflections.core :refer [pluralize]]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.gcs-file-preview :refer [GCSFilePreviewLink]]
   [org.broadinstitute.firecloud-ui.common.components :as comps]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.common.icons :as icons]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))


(def ^:private rows-per-page-options [10 25 100 500])


(defn- create-page-range [current-page total-pages]
  (cond
    (<= total-pages 5) (range 1 (inc total-pages))
    (<= current-page 3) (range 1 6)
    (>= current-page (- total-pages 2)) (range (- total-pages 4) (inc total-pages))
    :else (range (- current-page 2) (+ current-page 3))))


(react/defc Paginator
  {:render
   (fn [{:keys [props]}]
     (let [{:keys [pagination-params num-visible-rows num-total-rows width]} props
           {:keys [current-page rows-per-page]} pagination-params
           num-pages (js/Math.ceil (/ num-visible-rows rows-per-page))
           allow-prev (> current-page 1)
           allow-next (< current-page num-pages)
           right-num (min num-visible-rows (* current-page rows-per-page))
           left-num (if (zero? right-num) 0 (inc (* (dec current-page) rows-per-page)))]
       [:div {:style {:border "1px solid #ebebeb" :padding "1em"}}
        (let [narrow? (= :narrow width)
              container (fn [child align]
                          (if narrow?
                            [:div {:style {:margin "0.25em 0"}} child]
                            [:div {:style {:width "33%" :textAlign align}} child]))
              view-component
              [:div {:style {:display "inline-flex"}}
               [:b {:style {:marginRight "1ex"}} (str left-num " - " right-num)]
               (str "of " (pluralize num-visible-rows " result")
                    (when-not (= num-visible-rows num-total-rows)
                      (str " (filtered from " num-total-rows " total)")))]
              page-component
              (style/create-unselectable
               :div {:style {:display "inline-flex" :alignItems "baseline"}}
               [:div {:style {:display "inline-flex" :alignItems "baseline"
                              :padding "0em 0.9em"
                              :color (if allow-prev
                                       (:button-primary style/colors)
                                       (:border-light style/colors))
                              :cursor (when allow-prev "pointer")}
                      :onClick (when allow-prev #((:on-change props) (update-in pagination-params [:current-page] dec)))}
                (icons/icon {:style {:alignSelf "center" :margin "-0.5em 0"}} :angle-left)
                "Prev"]
               [:span {:style {:whiteSpace "nowrap"}}
                (map (fn [n]
                       (let [selected? (= n current-page)]
                         [:div {:style {:textAlign "center"
                                        :paddingTop 5 :display "inline-block" :width 29 :height 24
                                        :backgroundColor (when selected? (:button-primary style/colors))
                                        :color (if selected? "white" (:button-primary style/colors))
                                        :borderRadius (when selected? "100%")
                                        :cursor (when-not selected? "pointer")}
                                :onClick (when-not selected? #((:on-change props) (assoc-in pagination-params [:current-page] n)))}
                          n]))
                     (create-page-range current-page num-pages))]
               [:div {:style {:display "inline-flex" :alignItems "baseline"
                              :padding "0em 0.9em"
                              :color (if allow-next
                                       (:button-primary style/colors)
                                       (:border-light style/colors))
                              :cursor (when allow-next "pointer")}
                      :onClick (when allow-next #((:on-change props) (update-in pagination-params [:current-page] inc)))}
                "Next"
                (icons/icon {:style {:alignSelf "center" :margin "-0.5em 0"}} :angle-right)])
              rows-component
              [:div {:style {:display "inline-flex" :alignItems "baseline"}}
               "Display"
               (style/create-select
                {:defaultValue (utils/index-of rows-per-page-options (:initial-rows-per-page props))
                 :style {:width 60 :margin "0em 1em"}
                 :onChange #((:on-change props) {:rows-per-page (nth rows-per-page-options (-> % .-target .-value js/parseInt))
                                                 :current-page 1})}
                rows-per-page-options)
               "rows per page"]]
          [:div {:style {:fontSize 13 :lineHeight 1.5 :padding "0 48px"
                         :display "flex" :flexDirection (if narrow? "column" "row")
                         :alignItems (if narrow? "center" "baseline")}}
           (container view-component "left")
           (container page-component "center")
           (container rows-component "right")])]))})


(defn float-right [component & [style]]
  (fn [built-in]
    [:div {}
     [:div {:style {:float "left"}} built-in]
     [:div {:style (merge (or style {}) {:float "right"})} component]
     (common/clear-both)]))


(defn- render-cell [{:keys [width onResizeMouseDown onSortClick sortOrder] :as props}]
  [:div {:style (merge {:position "relative" :minWidth 10
                        :flexGrow (if (= width :remaining) 1 0)
                        :flexShrink 0
                        :flexBasis (if (= width :remaining) 0 width)}
                       (:cell-style props))}
   (when (and onResizeMouseDown (not= width :remaining))
     [:div {:style {:position "absolute" :width 20 :top 0 :bottom 0 :left (- width 10) :zIndex 1
                    :cursor "col-resize"}
            :onMouseDown onResizeMouseDown
            :onDoubleClick (:onResizeDoubleClick props)}])
   (when onSortClick
     [:div {:style {:position "absolute" :top 0 :bottom 0 :left 0 :right (if onResizeMouseDown 10 0)
                    :cursor "pointer"}
            :onClick onSortClick}])
   (when sortOrder
     [:div {:style {:position "absolute" :top "50%" :right 0 :width 16 :transform "translateY(-50%)"}}
      (if (= :asc sortOrder) "↑" "↓")])
   [:div {:title (:title props)
          :style (merge {:whiteSpace "nowrap" :overflow "hidden" :textOverflow "ellipsis"}
                        (when (not= width :remaining)
                          {:width (str "calc(" (- width (if sortOrder 16 0)) "px - " (:cell-padding-left props) ")")})
                        (:content-container-style props))}
    (:content props)]])


(defn default-render [data]
  (cond (map? data) (utils/map-to-string data)
        (sequential? data) (clojure.string/join ", " data)
        :else (str data)))

(defn render-gcs-links [workspace-bucket]
  (fn [maybe-uri]
    (if (string? maybe-uri)
      (if-let [parsed (common/parse-gcs-uri maybe-uri)]
        [GCSFilePreviewLink (assoc parsed
                              :workspace-bucket workspace-bucket
                              :attributes {:style {:direction "rtl" :marginRight "0.5em"
                                                   :overflow "hidden" :textOverflow "ellipsis"
                                                   :textAlign "left"}})]
        maybe-uri)
      (default-render maybe-uri))))


(react/defc Body
  {:render
   (fn [{:keys [props]}]
     (if (zero? (count (:rows props)))
       nil
       [:div {:style (merge {:fontSize "80%" :fontWeight 500} (:body-style props))}
        (map-indexed
          (fn [row-index row]
            (let [row-style (:row-style props)
                  row-style (if (fn? row-style) (row-style row-index row) row-style)]
              [:div {:style (merge {:display "flex" :alignItems "center"} row-style)}
               (map
                 (fn [col]
                   (let [render-content (or (:content-renderer col)
                                          (:as-text col)
                                          default-render)
                         render-title (or (:as-text col)
                                          default-render)]
                     (render-cell
                       {:width (:width col)
                        :content (render-content (nth row (:declared-index col)))
                        :title (render-title (nth row (:declared-index col)))
                        :cell-padding-left (or (:cell-padding-left props) 0)
                        :content-container-style (merge
                                                   {:padding (str "0.6em 0 0.6em " (or (:cell-padding-left props) 0))}
                                                   (:cell-content-style props))})))
                 (:columns props))]))
         (:rows props))]))})


(defn render-header [state props this]
  (let [{:keys [sort-column sort-order]} (:query-params @state)]
    [:div {:style (merge
                   {:display "flex" :fontWeight 500 :fontSize "80%"
                    :color "#fff" :backgroundColor (:background-dark style/colors)}
                   (:header-row-style props))}
     (map-indexed
       (fn [index column]
         (let [{:keys [header header-key width starting-width sort-by]} column
               sort-key (or header-key header)
               onResizeMouseDown
               (when (get column :resizable? (:resizable-columns? props))
                 (fn [e]
                   (swap! state assoc :dragging? true :mouse-x (.-clientX e) :drag-column index
                          :saved-user-select-state (common/disable-text-selection))))]
           (render-cell
             {:width width
              :content header
              :cell-style (when onResizeMouseDown {:borderRight (str "1px solid " (or (:resize-tab-color props) "#777777"))
                                                   :marginRight -1})
              :cell-padding-left (or (:cell-padding-left props) 0)
              :content-container-style (merge
                                         {:padding (str "0.8em 0 0.8em "
                                                        (or (:cell-padding-left props) 0))}
                                         (:header-style props))
              :onResizeMouseDown onResizeMouseDown
              :onResizeDoubleClick #(swap! state assoc-in [:column-meta index :width] starting-width)
              :onSortClick (when (and (or sort-by
                                          (:sortable-columns? props))
                                      (not= :none sort-by))
                             (fn [_]
                               (if (= sort-key sort-column)
                                 (case sort-order
                                   :asc (swap! state update :query-params assoc :sort-order :desc)
                                   :desc (if (:always-sort? props)
                                           (swap! state update :query-params assoc :sort-order :asc)
                                           (swap! state update :query-params dissoc :sort-column :sort-order)))
                                 (swap! state update :query-params assoc :sort-column sort-key :sort-order :asc))))
              :sortOrder (when (= sort-key sort-column) sort-order)})))
       (filter :visible? (react/call :get-ordered-columns this)))
     (common/clear-both)]))


(react/defc TextFilter
  {:get-initial-state
   (fn [] {:synced true})
   :render
   (fn [{:keys [props state this]}]
     [:div {:style {:display "inline-flex"}}
      (style/create-text-field
        {:ref "filter-field" :placeholder "Filter" :defaultValue (:initial-text props)
         :style {:backgroundColor (if (:synced @state) "#fff" (:tag-background style/colors))
                 :borderRadius "3px 0 0 3px" :marginBottom 0}
         :onKeyDown (common/create-key-handler [:enter] #(react/call :apply-filter this))
         :onChange #(swap! state assoc :synced false)})
      [comps/Button {:icon :search :onClick #(react/call :apply-filter this)
                     :style {:borderRadius "0 3px 3px 0"}}]])
   :apply-filter
   (fn [{:keys [state props refs]}]
     (swap! state assoc :synced true)
     ((:on-filter props) (common/get-text refs "filter-field")))})


(react/defc FilterGroupBar
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:display "inline-block"}}
      (map-indexed (fn [index item]
                     (let [first? (zero? index)
                           last? (= index (dec (count (:filter-groups props))))]
                       [:div {:style {:float "left" :textAlign "center"
                                      :backgroundColor (if (= index (:selected-index props))
                                                         (:button-primary style/colors)
                                                         (:background-light style/colors))
                                      :color (when (= index (:selected-index props)) "white")
                                      :padding "1ex" :minWidth 50
                                      :marginLeft (when-not first? -1)
                                      :border style/standard-line
                                      :borderTopLeftRadius (when first? 8)
                                      :borderBottomLeftRadius (when first? 8)
                                      :borderTopRightRadius (when last? 8)
                                      :borderBottomRightRadius (when last? 8)
                                      :cursor "pointer"}
                              :onClick #((:on-change props) index)}
                        (str (:text item)
                             " ("
                             (or (:count item)
                                 (count (filter (:pred item) (:data props))))
                             ")")]))
                   (:filter-groups props))
      (common/clear-both)])})



(defn- filter-data [data ->row columns filter-text]
  (if (empty? filter-text)
    data
    (filter (fn [item]
              (utils/matches-filter-text
               (apply str (map-indexed
                           (fn [i column]
                             (let [func (or (:filter-by column)
                                            (:as-text column)
                                            str)]
                               (if (= func :none) "" (func (nth (->row item) i)))))
                           columns))
               filter-text))
            data)))


(react/defc ColumnEditor
  {:render
   (fn [{:keys [props state refs]}]
     [:div {:style {:border (str "2px solid " (:line-default style/colors))
                    :padding "1em" :lineHeight "1.5em" :cursor (when (:drag-active @state) "ns-resize")}
            :onMouseMove (when (:drag-index @state)
                           (fn [e]
                             (let [x (.-clientX e)
                                   y (.-clientY e)
                                   dist (utils/distance (:start-x @state) (:start-y @state) x y)
                                   div-locs (map
                                             (fn [i] {:index i :y
                                                      (-> (@refs (str "div" i))
                                                          .getBoundingClientRect .-top)})
                                             (range (inc (count (:columns props)))))
                                   closest-div (apply min-key #(js/Math.abs (- y (:y %))) div-locs)]
                               (when (not= (:index closest-div) (:drop-index @state))
                                 (swap! state assoc :drop-index (:index closest-div)))
                               (when (and (not (:drag-active @state)) (> dist 5.0))
                                 (swap! state assoc :drag-active true)))))
            :onMouseUp (when (:drag-index @state)
                         (fn [_]
                           ((:on-reorder props) (:drag-index @state) (:drop-index @state))
                           (swap! state dissoc :drag-index :drag-active :drop-index)))}
      "Show:"
      (let [style {:padding "4px 8px" :marginRight 5 :borderRadius 5
                   :cursor (when-not (:drag-active @state) "pointer")
                   :backgroundColor (:button-primary style/colors) :color "#fff"}]
        [:div {:style {:padding "0.5em 0"}}
         [:span {:style style :onClick #((:on-visibility-change props) :all true)} "All"]
         [:span {:style style :onClick #((:on-visibility-change props) :all false)} "None"]])
      (map-indexed
       (fn [i column]
         (when (get column :reorderable? true)
           (let [visible? (:visible? column)]
             [:div {:ref (str "div" i)
                    :style {:borderTop (when (= i (:drop-index @state)) "1px solid gray")}}
              [:img {:src "assets/drag_temp.png"
                     :style {:height 16 :verticalAlign "middle" :marginRight "1ex"
                             :cursor "ns-resize"}
                     :draggable false
                     :onMouseDown (fn [e] (swap! state assoc
                                                 :drag-index i
                                                 :start-x (.-clientX e)
                                                 :start-y (.-clientY e)))}]
              [:label {:style {:cursor (when-not (:drag-active @state) "pointer")}}
               [:input {:type "checkbox" :checked visible?
                        :onChange #((:on-visibility-change props) i (not visible?))}]
               [:span {:style {:paddingLeft "0.5em"}} (:header column)]]])))
       (:columns props))
      (let [i (count (:columns props))]
        [:div {:ref (str "div" i)
               :style {:borderTop (when (= i (:drop-index @state)) "1px solid gray")}}])])})
