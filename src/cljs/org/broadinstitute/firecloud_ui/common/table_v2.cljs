(ns org.broadinstitute.firecloud-ui.common.table-v2
  (:require
    clojure.string
    [dmohs.react :as react]
    [inflections.core :refer [pluralize]]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    ))


(react/defc Paginator
  {:get-initial-state
   (fn [] {:rows-per-page 10
           :current-page 1})
   :render
   (fn [{:keys [props refs]}]
     (let [state (:parent-state props)
           rows-per-page (:rows-per-page @state)
           current-page (:current-page @state)
           num-total (:num-rows props)
           num-pages (js/Math.ceil (/ num-total rows-per-page))
           num-onClick (fn [n] (fn [e] (swap! state assoc :current-page n)))
           first-page-box (max 1 (- current-page 4))
           last-page-box (min num-pages (+ current-page 4))
           allow-prev (> current-page 1)
           allow-next (< current-page num-pages)
           left-num (+ 1 (* (- current-page 1) rows-per-page))
           right-num (min num-total (* current-page rows-per-page))]
       [:div {:style {:border "1px solid #ebebeb" :boxShadow "-3px -6px 23px -7px #ebebeb inset"}}
        [:div {:style {:display "block" :fontSize 13 :lineHeight 1.5
                       :padding "0px 48px" :verticalAlign "middle"}}

         [:div {:style {:float "left" :display "inline-block" :width "33.33%" :padding "2.15em 0em" :verticalAlign "middle"}}
          [:b {} (str left-num " - " right-num)] (str " of " (pluralize num-total " result"))]

         (style/create-unselectable :div {:style {:float "left" :display "inline-block" :width "33.33%"
                                                  :padding "1.6em 0em" :verticalAlign "middle" :textAlign "center"}}
           [:div {:style {:display "inline-block" :padding "0.55em 0.9em"
                          :color (if allow-prev (:button-blue style/colors) (:border-gray style/colors))
                          :cursor (when allow-prev "pointer")}
                  :onClick (when allow-prev #(swap! state assoc :current-page (- current-page 1)))}
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
              (range first-page-box (+ 1 last-page-box)))]

           [:div {:style {:display "inline-block" :padding "0.55em 0.9em"
                          :color (if allow-next (:button-blue style/colors) (:border-gray style/colors))
                          :cursor (when allow-next "pointer")}
                  :onClick (when allow-next #(swap! state assoc :current-page (+ current-page 1)))}
            [:span {:style {:paddingRight "1em"}} "Next"]
            (icons/font-icon {:style {:fontSize "70%"}} :angle-right)])

         [:div {:style {:float "left" :display "inline-block" :width "33.33%"
                        :padding "2.15em 0em" :textAlign "right"}}
          "Display"
          (style/create-select {:style {:width 60 :margin "0em 1em"} :ref "numRows"
                                :onChange #(swap! state assoc
                                            :rows-per-page (-> (@refs "numRows") .getDOMNode .-value)
                                            :current-page 1)}
            10 25 100 500)
          "rows per page"]
         (common/clear-both)]]))})


(defn- render-cell [width content cell-style content-container-style onResizeMouseDown]
  [:div {:style (merge {:float "left" :position "relative" :width width :minWidth 10}
                       cell-style)}
   (when onResizeMouseDown
     [:div {:style {:position "absolute" :width 20 :top 0 :bottom 0 :left (- width 10) :zIndex 1
                    :cursor "ew-resize"}
            :onMouseDown onResizeMouseDown}])
   [:div {:style (merge {:whiteSpace "nowrap" :overflow "hidden" :textOverflow "ellipsis"}
                   content-container-style)}
    content]])


(defn- render-header-cell [props index width content onResizeMouseDown]
  (render-cell
    width
    content
    (when (and (pos? index) onResizeMouseDown) {:borderLeft "1px solid #777777" :marginLeft -1})
    (merge
      {:padding "0.8em 0 0.8em 16px"}
      (:header-style props))
    onResizeMouseDown))


(defn- render-body-cell [props width content]
  (render-cell
    width
    content
    nil
    (merge
      {:padding "0.6em 0 0.6em 16px"}
      (:cell-content-style props))
    nil))


(react/defc Body
  {:render
   (fn [{:keys [props]}]
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
                   (render-body-cell
                     props
                     (nth (:column-widths props) col-index)
                     (render-content row-index (nth row col-index)))))
               (:columns props))
             (common/clear-both)]))
        (:data props))])})


(react/defc Table
  {:get-initial-state
   (fn [{:keys [props]}]
     {:rows-per-page 10
      :current-page 1
      :column-widths (mapv #(or (:starting-width %) 100) (:columns props))
      :dragging? false})
   :render
   (fn [{:keys [state props]}]
     (let [paginator-above (= :above (:paginator props))
           paginator-below (= :below (get props :paginator :below))
           paginator [Paginator {:num-rows (count (:data props)) :parent-state state}]
           paginator-space (or (:paginator-space props) 24)]
       [:div {}
        (when paginator-above [:div {:style {:paddingBottom paginator-space}} paginator])
        [:div {:style {:overflowX "auto"}}
         [:div {:style {:position "relative"
                        :minWidth (reduce + (:column-widths @state))
                        :cursor (when (:dragging? @state) "ew-resize")}
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
          [:div {:style (merge
                          {:fontWeight 500 :fontSize "80%"
                           :color "#fff" :backgroundColor (:header-darkgray style/colors)}
                          (:header-row-style props))}
           (map-indexed
             (fn [i column]
               (let [width (nth (:column-widths @state) i)]
                 (render-header-cell
                   props
                   i
                   width
                   (:header column)
                   (when (get column :resizable? (:resizable-columns? props))
                     (fn [e] (swap! state assoc
                               :dragging? true :mouse-x (.-clientX e) :drag-column i))))))
             (:columns props))
           (common/clear-both)]
          [Body (assoc props
                  :column-widths (:column-widths @state)
                  :data (take (:rows-per-page @state)
                          (drop (* (- (:current-page @state) 1) (:rows-per-page @state))
                            (:data props))))]]]
        (when paginator-below [:div {:style {:paddingTop paginator-space}} paginator])]))})
