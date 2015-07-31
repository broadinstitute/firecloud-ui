(ns org.broadinstitute.firecloud-ui.common.table
  (:require
   [clojure.string]
   [dmohs.react :as react]
   [inflections.core :refer [pluralize]]
   [org.broadinstitute.firecloud-ui.common :refer [clear-both]]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.common.icons :as icons]
   ))


(react/defc DefaultCell
  {:render
   (fn [{:keys [props]}]
     [:span {:style {:display "inline-block" :padding "1em"}} (:data props)])})


(react/defc CellContainer
  {:render
   (fn [{:keys [props]}]
     [:div {:style (:style props)}
      (let [CellComponent (or (:component props) DefaultCell)]
        [CellComponent {:data (:data props) :index (:index props)}])])})


(react/defc Body
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:border (str "1px solid " (:line-gray style/colors))
                    :borderRadius 4}}
      (map-indexed (fn [i row]
                     [:div {:style {:display "flex" :height "3.4em"
                                    :borderTop (when-not (zero? i)
                                                 (str "1px solid " (:line-gray style/colors)))}}
                      (map-indexed
                       (fn [i cell-data]
                         [CellContainer {:index i
                                         :component (get-in props [:columns i :component])
                                         :style (get-in props [:columns i :style])
                                         :data cell-data}])
                       row)])
                   (:data props))])})


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

         (clear-both)]]))})


(react/defc Table
  {:get-initial-state
   (fn []
     {:rows-per-page 10
      :current-page 1})
   :render
   (fn [{:keys [state props]}]
     (let [paginator-above (= :above (:paginator props))
           paginator-below (= :below (get props :paginator :below))
           paginator [Paginator {:num-rows (count (:data props)) :parent-state state}]
           paginator-space (or (:paginator-space props) 24)]
       [:div {}
        (when paginator-above [:div {:style {:paddingBottom paginator-space}} paginator])
        [:div {:style {:color (:text-light style/colors)
                       :paddingBottom "1em" :display "flex"}}
         (map (fn [column]
                [:div {:style (merge (:style column) (:header-style column))}
                 (:label column)])
           (:columns props))]
        [Body {:columns (:columns props)
               :data (take (:rows-per-page @state)
                       (drop (* (- (:current-page @state) 1) (:rows-per-page @state))
                         (:data props)))}]
        (when paginator-below [:div {:style {:paddingTop paginator-space}} paginator])]))
   :component-will-receive-props
   (fn [{:keys [state]}]
     (swap! state assoc :current-page 1))})

(react/defc AdvancedTable
  {:get-initial-state
   (fn [{:keys [props]}]
     {:rows-per-page 10
      :current-page 1
      :column-widths (mapv #(:starting-width %) (:columns props))
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
         [:div {:style {:display "inline-block" :position "relative"
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
          (style/create-unselectable :div {:style {:whiteSpace "nowrap" :overflow "hidden" :textOverflow "clip"}}
            [:div {}
             (map-indexed
               (fn [col-num column]
                 (let [width (nth (:column-widths @state) col-num)]
                   ;; TODO - make min/max width available as properties, default to min of 10
                   [:div {:style {:float "left" :position "relative" :width width :minWidth 10}}
                    (when (get column :resizable? true)
                      [:div {:style {:position "absolute" :width 20 :top 0 :bottom 0 :left (- width 10) :zIndex 1
                                     :cursor "ew-resize"}
                             :onMouseDown (fn [e] (swap! state assoc
                                                    :dragging? true :mouse-x (.-clientX e) :drag-column col-num))}])
                    (:header-component column)]))
               (:columns props))])
          (clear-both)

          (map-indexed
            (fn [row-num row]
              [:div ((:row-props props) row-num row)
               (map-indexed (fn [col-num col]
                              [:div {:style {:width (nth (:column-widths @state) col-num) :float "left"
                                             :whiteSpace "nowrap" :overflow "hidden" :textOverflow "ellipsis"}}
                               ((:cell-renderer col) row-num row)])
                 (:columns props))
               (clear-both)])
            (take (:rows-per-page @state)
              (drop (* (- (:current-page @state) 1) (:rows-per-page @state))
                (:data props))))]]

        (when paginator-below [:div {:style {:paddingTop paginator-space}} paginator])]))})
