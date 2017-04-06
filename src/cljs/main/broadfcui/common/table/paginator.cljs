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


(defn- container [narrow? child align]
  (if narrow?
    [:div {:style {:margin "0.2rem 0"}} child]
    [:div {:style {:width "33%" :textAlign align}} child]))


(defn- view-component [{:keys [page-number rows-per-page filtered-count total-count]}]
  (let [right-num (min filtered-count (* page-number rows-per-page))
        left-num (if (zero? right-num) 0 (inc (* (dec page-number) rows-per-page)))]
    [:div {:style {:display "inline-flex"}}
     [:strong {:style {:marginRight "0.3rem"}} (str left-num " - " right-num)]
     (str " of " (pluralize filtered-count " result")
          (when-not (= filtered-count total-count)
            (str " (filtered from " total-count " total)")))]))


(defn- page-component [{:keys [filtered-count rows-per-page page-number page-selected]}]
  (let [num-pages (js/Math.ceil (/ filtered-count rows-per-page))
        allow-prev (> page-number 1)
        allow-next (< page-number num-pages)]
    (style/create-unselectable
     :div {:style {:display "inline-flex" :alignItems "baseline"}}
     [:div {:style {:display "inline-flex" :alignItems "baseline"
                    :padding "0 0.75rem"
                    :color (if allow-prev
                             (:button-primary style/colors)
                             (:border-light style/colors))
                    :cursor (when allow-prev "pointer")}
            :onClick (when allow-prev #(page-selected (dec page-number)))}
      (icons/icon {:style {:alignSelf "center" :paddingRight "0.5rem"}} :angle-left)
      "Prev"]
     [:span {:style {:whiteSpace "nowrap"}}
      (map (fn [n]
             (let [selected? (= n page-number)]
               [:div {:style {:textAlign "center"
                              ;; FIXME: find a better way to make this div square
                              :paddingTop 5 :display "inline-block" :width 29 :height 24
                              :backgroundColor (when selected? (:button-primary style/colors))
                              :color (if selected? "white" (:button-primary style/colors))
                              :borderRadius (when selected? "100%")
                              :cursor (when-not selected? "pointer")}
                      :onClick (when-not selected? #(page-selected n))}
                n]))
           (create-page-range page-number num-pages))]
     [:div {:style {:display "inline-flex" :alignItems "baseline"
                    :padding "0 0.75rem"
                    :color (if allow-next
                             (:button-primary style/colors)
                             (:border-light style/colors))
                    :cursor (when allow-next "pointer")}
            :onClick (when allow-next #(page-selected (inc page-number)))}
      "Next"
      (icons/icon {:style {:alignSelf "center" :paddingLeft "0.5rem"}} :angle-right)])))


(defn- rows-component [{:keys [rows-per-page per-page-options per-page-selected]}]
  [:div {:style {:display "inline-flex" :alignItems "baseline"}}
   "Display"
   (style/create-identity-select
    {:value rows-per-page
     :style {:width 60 :margin "0 0.75rem"}
     :onChange #(per-page-selected (-> % .-target .-value int))}
    per-page-options)
   "rows per page"])


(defn paginator [props]
  [:div {:style (merge {:border "1px solid #ebebeb" :padding "1em"}
                       (:style props))}
   (let [narrow? (= :narrow (-> props :style :layout))]
     [:div {:style {:fontSize 13 :lineHeight 1.5 :padding "0 48px"
                    :display "flex" :flexDirection (if narrow? "column" "row")
                    :alignItems (if narrow? "center" "baseline")}}
      (container narrow? (view-component props) "left")
      (container narrow? (page-component props) "center")
      (container narrow? (rows-component props) "right")])])
