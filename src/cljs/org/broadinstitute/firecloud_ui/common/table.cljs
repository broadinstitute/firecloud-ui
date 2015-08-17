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
   :get-initial-state
   (fn []
     {:rows-per-page initial-rows-per-page
      :current-page 1})
   :render
   (fn [{:keys [props state refs]}]
     (let [rows-per-page (:rows-per-page @state)
           current-page (:current-page @state)
           num-total (:num-rows props)
           num-pages (js/Math.ceil (/ num-total rows-per-page))
           num-onClick (fn [n] #(swap! state assoc :current-page n))
           page-range (create-page-range current-page num-pages)
           allow-prev (> current-page 1)
           allow-next (< current-page num-pages)
           right-num (min num-total (* current-page rows-per-page))
           left-num (if (zero? right-num) 0 (inc (* (dec current-page) rows-per-page)))]
       [:div {:style {:border "1px solid #ebebeb" :boxShadow "-3px -6px 23px -7px #ebebeb inset"}}
        [:div {:style {:fontSize 13 :lineHeight 1.5 :padding "0px 48px" :verticalAlign "middle"}}

         [:div {:style {:float "left" :display "inline-block" :width "33.33%" :padding "2.15em 0em" :verticalAlign "middle"}}
          [:b {} (str left-num " - " right-num)] (str " of " (pluralize num-total " result"))]

         (style/create-unselectable :div {:style {:float "left" :display "inline-block" :width "33.33%"
                                                  :padding "1.6em 0em" :verticalAlign "middle" :textAlign "center"}}
           [:div {:style {:display "inline-block" :padding "0.55em 0.9em"
                          :color (if allow-prev (:button-blue style/colors) (:border-gray style/colors))
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
                            :onClick (when-not selected? (num-onClick n))}
                      n]))
              page-range)]

           [:div {:style {:display "inline-block" :padding "0.55em 0.9em"
                          :color (if allow-next (:button-blue style/colors) (:border-gray style/colors))
                          :cursor (when allow-next "pointer")}
                  :onClick (when allow-next #(swap! state update-in [:current-page] inc))}
            [:span {:style {:paddingRight "1em"}} "Next"]
            (icons/font-icon {:style {:fontSize "70%"}} :angle-right)])

         [:div {:style {:float "left" :display "inline-block" :width "33.33%"
                        :padding "2.15em 0em" :textAlign "right"}}
          "Display"
          (style/create-select {:style {:width 60 :margin "0em 1em"} :ref "numRows"
                                :onChange #(swap! state assoc
                                            :rows-per-page (-> (@refs "numRows") .getDOMNode .-value)
                                            :current-page 1)}
            [10 25 100 500])
          "rows per page"]
         (common/clear-both)]]))
   :component-did-update
   (fn [{:keys [props]}]
     ((:onChange props)))})


(defn- render-cell [{:keys [width onResizeMouseDown onResizeDoubleClick onSortClick sortOrder] :as props}]
  [:div {:style (merge {:float "left" :position "relative" :width width :minWidth 10}
                  (:cell-style props))}
   (when (:onResizeMouseDown props)
     [:div {:style {:position "absolute" :width 20 :top 0 :bottom 0 :left (- width 10) :zIndex 1
                    :cursor "col-resize"}
            :onMouseDown onResizeMouseDown
            :onDoubleClick onResizeDoubleClick}])
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


(defn- render-header [state props]
  [:div {:style (merge
                  {:fontWeight 500 :fontSize "80%"
                   :color "#fff" :backgroundColor (:header-darkgray style/colors)}
                  (:header-row-style props))}
   (map-indexed
     (fn [i column]
       (let [onResizeMouseDown (when (get column :resizable? (:resizable-columns? props))
                                 (fn [e] (swap! state assoc
                                           :dragging? true :mouse-x (.-clientX e) :drag-column i)))]
         (render-cell
           {:width (nth (:column-widths @state) i)
            :content (:header column)
            :cell-style (when onResizeMouseDown {:borderRight "1px solid #777777" :marginRight -1})
            :cell-padding-left (or (:cell-padding-left props) 0)
            :content-container-style (merge
                                       {:padding (str "0.8em 0 0.8em " (or (:cell-padding-left props) 0))}
                                       (:header-style props))
            :onResizeMouseDown onResizeMouseDown
            :onResizeDoubleClick #(swap! state update-in [:column-widths]
                                     assoc i (get-in props [:columns i :starting-width]))
            :onSortClick (when-let [sorter (:sort-by column)]
                           #(if (= i (:sort-column @state))
                             (case (:sort-order @state)
                               :asc (swap! state assoc :sort-order :desc)
                               :desc (swap! state dissoc :sort-column :sort-order :key-fn)
                               (assert false "bad state"))
                             (swap! state assoc :sort-column i :sort-order :asc
                               :key-fn (if (= :value sorter)
                                         (fn [row] (nth row i))
                                         (fn [row] (sorter (nth row i)))))))
            :sortOrder (when (= i (:sort-column @state)) (:sort-order @state))})))
     (:columns props))
   (common/clear-both)])


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
          (let [row-style (:row-style props)
                row-style (merge
                            row-style
                            (if (even? row-index)
                              (merge
                                {:backgroundColor (:background-gray style/colors)}
                                (:even-row-style props))
                              (:odd-row-style props)))]
            [:div {:style row-style}
             (map-indexed
               (fn [col-index col]
                 (let [render-content (or (:content-renderer col) (fn [i data] data))]
                   (render-cell
                     {:width (nth (:column-widths props) col-index)
                      :content (render-content row-index (nth row col-index))
                      :cell-padding-left (or (:cell-padding-left props) 0)
                      :content-container-style (merge
                                                 {:padding (str "0.6em 0 0.6em " (or (:cell-padding-left props) 0))}
                                                 (:cell-content-style props))})))
               (:columns props))
             (common/clear-both)]))
        (:rows @state))])})


(react/defc Table
  {:get-default-props
   (fn []
     {:cell-padding-left "16px"
      :paginator :below
      :paginator-space 24
      :resizable-columns? true
      :filterable? true})
   :get-initial-state
   (fn [{:keys [props]}]
     {:column-widths (mapv #(or (:starting-width %) 100) (:columns props))
      :dragging? false
      :filtered-data (:data props)})
   :render
   (fn [{:keys [this state refs props]}]
     (let [paginator-above (= :above (:paginator props))
           paginator-below (= :below (:paginator props))
           paginator [Paginator {:ref "paginator"
                                 :num-rows (count (:filtered-data @state))
                                 :onChange #(react/call :handle-pagination-change this)}]]
       [:div {}
        (when (:filterable? props)
          (let [apply-filter #(swap! state assoc :filtered-data
                               (react/call :filter-data this props
                                 (-> (@refs "filter-field") .getDOMNode .-value trim)))]
            [:div {:style {:padding "0 0 1em 1em"}}
             (style/create-text-field {:ref "filter-field" :placeholder "Filter"
                                       :onKeyDown (common/create-key-handler
                                                    [:enter] apply-filter)})
             [:span {:style {:paddingLeft "1em"}}]
             [comps/Button {:icon :search :onClick apply-filter}]]))
        (when paginator-above [:div {:style {:paddingBottom (:paginator-space props)}} paginator])
        [:div {:style {:overflowX "auto"}}
         [:div {:style {:position "relative"
                        :minWidth (reduce + (:column-widths @state))
                        :cursor (when (:dragging? @state) "col-resize")}
                :onMouseMove (fn [e]
                               (when (:dragging? @state)
                                 (let [widths (:column-widths @state)
                                       current-width (nth widths (:drag-column @state))
                                       new-mouse-x (.-clientX e)
                                       drag-amount (- new-mouse-x (:mouse-x @state))
                                       new-width (+ current-width drag-amount)]
                                   (when (>= new-width 10)
                                     (swap! state update-in [:column-widths]
                                       assoc (:drag-column @state) (+ current-width drag-amount))
                                     (swap! state assoc :mouse-x new-mouse-x)))))
                :onMouseUp #(swap! state assoc :dragging? false)}
          (render-header state props)
          [Body (assoc props
                  :ref "body"
                  :columns (:columns props)
                  :column-widths (:column-widths @state)
                  :initial-rows (react/call :get-sliced-data this true))]]]
        (when paginator-below [:div {:style {:paddingTop (:paginator-space props)}} paginator])]))
   :get-sliced-data
   (fn [{:keys [state refs]} & [initial-render?]]
     (let [[n c] (if initial-render?
                   [1 initial-rows-per-page]
                   (react/call :get-current-slice (@refs "paginator")))
           filtered-data (:filtered-data @state)
           sorted-data (if-let [keyfn (:key-fn @state)] (sort-by keyfn filtered-data) filtered-data)
           ordered-data (if (= :desc (:sort-order @state)) (reverse sorted-data) sorted-data)]
       (take c (drop (* (dec n) c) ordered-data))))
   :filter-data
   (fn [{:keys []} & [props filter-text]]
     (if (empty? filter-text)
       (:data props)
       (filter
         (fn [row]
           (utils/matches-filter-text
             (apply str
               (map-indexed
                 (fn [i column]
                   (if-let [f (:filter-by column)]
                     (if (= f :none) "" (f (nth row i)))
                     (str (nth row i))))
                 (:columns props)))
             filter-text))
         (:data props))))
   :handle-pagination-change
   (fn [{:keys [this refs]}]
     (react/call :set-rows (@refs "body") (react/call :get-sliced-data this)))
   :component-will-receive-props
   (fn [{:keys [this state next-props refs]}]
     (swap! state assoc :filtered-data
       (react/call :filter-data this next-props (-> (@refs "filter-field") .getDOMNode .-value trim))))})
