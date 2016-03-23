(ns org.broadinstitute.firecloud-ui.common.table-utils
  (:require
   [dmohs.react :as react]
   [inflections.core :refer [pluralize]]
   [org.broadinstitute.firecloud-ui.common :as common]
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
  {:get-current-slice
   (fn [{:keys [state]}]
     [(:current-page @state) (:rows-per-page @state)])
   :set-num-rows-visible
   (fn [{:keys [state]} n]
     (when (not= n (:num-rows-visible @state))
       (swap! state assoc :num-rows-visible n :current-page 1)))
   :get-initial-state
   (fn [{:keys [props]}]
     {:rows-per-page (:initial-rows-per-page props)
      :current-page 1
      :num-rows-visible (:num-total-rows props)})
   :render
   (fn [{:keys [props state]}]
     (let [rows-per-page (:rows-per-page @state)
           current-page (:current-page @state)
           num-rows-visible (:num-rows-visible @state)
           num-pages (js/Math.ceil (/ num-rows-visible rows-per-page))
           allow-prev (> current-page 1)
           allow-next (< current-page num-pages)
           right-num (min num-rows-visible (* current-page rows-per-page))
           left-num (if (zero? right-num) 0 (inc (* (dec current-page) rows-per-page)))]
       [:div {:style {:border "1px solid #ebebeb" :padding "1em"}}
        (let [layout-style (if (= :narrow (:width props))
                             {:textAlign "center" :padding "1ex"}
                             {:display "inline-block" :width "33.33%"})
              view-component
              [:div {:style layout-style}
               [:b {} (str left-num " - " right-num)]
               (str " of " (pluralize num-rows-visible " result")
                 (when-not (= num-rows-visible (:num-total-rows props))
                   (str " (filtered from " (:num-total-rows props) " total)")))]
              page-component
              (style/create-unselectable
                :div {:style (merge {:textAlign "center"} layout-style)}
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
              rows-component
              [:div {:style (merge {:textAlign "right"} layout-style)}
               "Display"
               (style/create-select
                 {:style {:width 60 :margin "0em 1em"}
                  :onChange #(swap! state assoc
                              :rows-per-page (nth rows-per-page-options (-> % .-target .-value js/parseInt))
                              :current-page 1)}
                 rows-per-page-options)
               "rows per page"]]
          [:div {:style {:fontSize 13 :lineHeight 1.5 :padding "0px 48px"}}
           view-component page-component rows-component
           (common/clear-both)])]))
   :component-did-update
   (fn [{:keys [props state prev-state]}]
     (when-not (and (apply = (map :rows-per-page [prev-state @state]))
                    (apply = (map :current-page [prev-state @state])))
       ((:onChange props))))})


(defn- render-cell [{:keys [width onResizeMouseDown onSortClick sortOrder] :as props}]
  [:div {:style (merge {:display "inline-block" :verticalAlign "bottom"
                        :position "relative" :width width :minWidth 10}
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


(defn default-render [data]
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
     (if (zero? (count (:rows @state)))
       nil
       [:div {:style (merge {:fontSize "80%" :fontWeight 500} (:body-style props))}
        (map-indexed
          (fn [row-index row]
            (let [row-style (:row-style props)
                  row-style (if (fn? row-style) (row-style row-index row) row-style)]
              [:div {:style (merge {:whiteSpace "nowrap"} row-style)}
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
         (:rows @state))]))})


(defn render-header [state props this after-update]
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
            :cell-style (when onResizeMouseDown {:borderRight (str "1px solid " (or (:resize-tab-color props) "#777777"))
                                                 :marginRight -1})
            :cell-padding-left (or (:cell-padding-left props) 0)
            :content-container-style (merge
                                      {:padding (str "0.8em 0 0.8em "
                                                     (or (:cell-padding-left props) 0))}
                                      (:header-style props))
            :onResizeMouseDown onResizeMouseDown
            :onResizeDoubleClick #(swap! state assoc-in [:columns i :width]
                                         (:starting-width column))
            :onSortClick (when (and (or (:sort-by column)
                                        (:sortable-columns? props))
                                    (not= :none (:sort-by column)))
                           (fn [e]
                             (if (= i (:sort-column @state))
                               (case (:sort-order @state)
                                 :asc (swap! state assoc :sort-order :desc)
                                 :desc (swap! state dissoc :sort-column :sort-order :key-fn))
                               (swap! state assoc :sort-column i :sort-order :asc
                                 :key-fn (let [sort-fn (or (:sort-by column) identity)]
                                           (if (= sort-fn :text)
                                             (fn [row] ((:as-text column) (nth row i)))
                                             (fn [row] (sort-fn (nth row i)))))))
                             (after-update #(react/call :set-body-rows this))))
            :sortOrder (when (= i (:sort-column @state)) (:sort-order @state))})))
     (filter :visible? (react/call :get-ordered-columns this)))
   (common/clear-both)])


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


(react/defc FilterBar
  {:apply-filter
   (fn [{:keys [props state]} & [filter-index]]
     (let [selected-filter (nth (:filters props) (or filter-index (:selected-index @state)))]
       (filter (:pred selected-filter) (:data props))))
   :get-initial-state
   (fn [{:keys [props]}]
     {:selected-index (or (:selected-index props) 0)})
   :render
   (fn [{:keys [this props state]}]
     [:div {:style {:display "inline-block"}}
      (map-indexed (fn [index item]
                     (let [first? (zero? index)
                           last? (= index (dec (count (:filters props))))]
                       [:div {:style {:float "left" :textAlign "center"
                                      :backgroundColor (if (= index (:selected-index @state))
                                                         (:button-blue style/colors)
                                                         (:background-gray style/colors))
                                      :color (when (= index (:selected-index @state)) "white")
                                      :padding "1ex" :minWidth 50
                                      :marginLeft (when-not first? -1)
                                      :border style/standard-line
                                      :borderTopLeftRadius (when first? 8)
                                      :borderBottomLeftRadius (when first? 8)
                                      :borderTopRightRadius (when last? 8)
                                      :borderBottomRightRadius (when last? 8)
                                      :cursor "pointer"}
                              :onClick #(swap! state assoc :selected-index index)}
                        (str (:text item)
                          " ("
                          (or (:count item) (count (react/call :apply-filter this index)))
                          ")")]))
        (:filters props))
      (common/clear-both)])
   :component-did-update
   (fn [{:keys [props state prev-state]}]
     (when-not (= (:selected-index @state) (:selected-index prev-state))
       ((:on-change props) (:selected-index @state))))})



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
                                                           .getBoundingClientRect .-top)})
                                              (range (inc (count (:columns props)))))
                                   closest-div (apply min-key #(js/Math.abs (- y (:y %))) div-locs)]
                               (when (not= (:index closest-div) (:drop-index @state))
                                 (swap! state assoc :drop-index (:index closest-div)))
                               (when (and (not (:drag-active @state)) (> dist 5.0))
                                 (swap! state assoc :drag-active true)))))
            :onMouseUp (when (:drag-index @state)
                         (fn [e]
                           ((:on-reorder props) (:drag-index @state) (:drop-index @state))
                           (swap! state dissoc :drag-index :drag-active :drop-index)))}
      "Show:"
      (let [style {:padding "4px 8px" :marginRight 5 :borderRadius 5
                   :cursor (when-not (:drag-active @state) "pointer")
                   :backgroundColor (:button-blue style/colors) :color "#fff"}]
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
                        :onChange #((:on-visibility-change props) (:index column) (not visible?))}]
               [:span {:style {:paddingLeft "0.5em"}} (:header column)]]])))
       (:columns props))
      (let [i (count (:columns props))]
        [:div {:ref (str "div" i)
               :style {:borderTop (when (= i (:drop-index @state)) "1px solid gray")}}])])})
