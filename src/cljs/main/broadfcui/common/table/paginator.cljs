(ns broadfcui.common.table.paginator
  (:require
    [inflections.core :refer [pluralize]]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.style :as style]
    [broadfcui.utils :as utils]
    ))


(defn- create-page-range [current-page total-pages]
  (cond
    (<= total-pages 5) (range 1 (inc total-pages))
    (<= current-page 3) (range 1 6)
    (>= current-page (- total-pages 2)) (range (- total-pages 4) (inc total-pages))
    :else (range (- current-page 2) (+ current-page 3))))


(defn paginator [{:keys [filtered-count total-count page-num per-page per-page-options
                         page-selected per-page-selected
                         style]}]
  [:div {:style {:border "1px solid #ebebeb" :padding "1em"}}
   (let [{:keys [width]} style
         num-pages (js/Math.ceil (/ filtered-count per-page))
         allow-prev (> page-num 1)
         allow-next (< page-num num-pages)
         right-num (min filtered-count (* page-num per-page))
         left-num (if (zero? right-num) 0 (inc (* (dec page-num) per-page)))
         narrow? (= :narrow width)
         container (fn [child align]
                     (if narrow?
                       [:div {:style {:margin "0.25em 0"}} child]
                       [:div {:style {:width "33%" :textAlign align}} child]))
         view-component
         [:div {:style {:display "inline-flex"}}
          [:b {:style {:marginRight "1ex"}} (str left-num " - " right-num)]
          (str "of " (pluralize filtered-count " result")
               (when-not (= filtered-count total-count)
                 (str " (filtered from " total-count " total)")))]
         page-component
         (style/create-unselectable
          :div {:style {:display "inline-flex" :alignItems "baseline"}}
          [:div {:style {:display "inline-flex" :alignItems "baseline"
                         :padding "0em 0.9em"
                         :color (if allow-prev
                                  (:button-primary style/colors)
                                  (:border-light style/colors))
                         :cursor (when allow-prev "pointer")}
                 :onClick (when allow-prev #(page-selected (dec page-num)))}
           (icons/icon {:style {:alignSelf "center" :paddingRight "0.5rem"}} :angle-left)
           "Prev"]
          [:span {:style {:whiteSpace "nowrap"}}
           (map (fn [n]
                  (let [selected? (= n page-num)]
                    [:div {:style {:textAlign "center"
                                   :paddingTop 5 :display "inline-block" :width 29 :height 24
                                   :backgroundColor (when selected? (:button-primary style/colors))
                                   :color (if selected? "white" (:button-primary style/colors))
                                   :borderRadius (when selected? "100%")
                                   :cursor (when-not selected? "pointer")}
                           :onClick (when-not selected? #(page-selected n))}
                     n]))
                (create-page-range page-num num-pages))]
          [:div {:style {:display "inline-flex" :alignItems "baseline"
                         :padding "0em 0.9em"
                         :color (if allow-next
                                  (:button-primary style/colors)
                                  (:border-light style/colors))
                         :cursor (when allow-next "pointer")}
                 :onClick (when allow-next #(page-selected (inc page-num)))}
           "Next"
           (icons/icon {:style {:alignSelf "center" :paddingLeft "0.5rem"}} :angle-right)])
         rows-component
         [:div {:style {:display "inline-flex" :alignItems "baseline"}}
          "Display"
          (style/create-select
           {:defaultValue (utils/index-of per-page-options per-page-options)
            :style {:width 60 :margin "0em 1em"}
            :onChange #(per-page-selected (nth per-page-options (-> % .-target .-value js/parseInt)))}
           per-page-options)
          "rows per page"]]
     [:div {:style {:fontSize 13 :lineHeight 1.5 :padding "0 48px"
                    :display "flex" :flexDirection (if narrow? "column" "row")
                    :alignItems (if narrow? "center" "baseline")}}
      (container view-component "left")
      (container page-component "center")
      (container rows-component "right")])])
