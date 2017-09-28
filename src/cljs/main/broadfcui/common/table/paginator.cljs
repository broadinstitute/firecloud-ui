(ns broadfcui.common.table.paginator
  (:require
   [dmohs.react :as react]
   [inflections.core :as inflections]
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


(react/defc Paginator
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [narrow?]} @state
           container (fn [child align]
                       (if narrow?
                         [:div {:style {:marginBottom "0.4rem"}} child]
                         [:div {:style {:width "33.333%" :textAlign align}} child]))]
       [:div {:ref "content-container"
              :style (merge {:fontSize 13 :lineHeight 1.5
                             :display "flex" :flexDirection (if narrow? "column" "row")
                             :alignItems (if narrow? "center" "baseline")}
                            (:style props))}
        (container (this :-render-view-component) "left")
        (container (this :-render-page-component) "center")
        (container (this :-render-rows-component) "right")]))
   :component-did-mount
   (fn [{:keys [props state refs]}]
     (let [check-size #(let [width (.-offsetWidth (@refs "content-container"))
                             narrow? (< width (:width-threshold props))
                             current-narrow? (:narrow? @state)]
                         (when (or (nil? current-narrow?) ;; to handle initialization
                                   (not= narrow? current-narrow?))
                           (swap! state assoc :narrow? narrow?)))]
       (check-size)
       (.addEventListener (@refs "content-container") "onresize" check-size)))
   :-render-view-component
   (fn [{:keys [props]}]
     (let [{:keys [page-number rows-per-page tab-count total-count]} props
           right-num (min tab-count (* page-number rows-per-page))
           left-num (if (zero? right-num) 0 (inc (* (dec page-number) rows-per-page)))]
       [:div {:style {:display "inline-flex"}}
        [:strong {:style {:marginRight "0.3rem"}} (str left-num " - " right-num)]
        (str " of " (inflections/pluralize tab-count " result")
             (when-not (= tab-count total-count)
               (str " (filtered from " total-count " total)")))]))
   :-render-page-component
   (fn [{:keys [props]}]
     (let [{:keys [tab-count rows-per-page page-number page-selected]} props
           num-pages (js/Math.ceil (/ tab-count rows-per-page))
           allow-prev (> page-number 1)
           allow-next (< page-number num-pages)]
       (style/create-unselectable
        :div {:style {:display "inline-flex" :alignItems "baseline"}}
        [:div {:data-test-id "prev-page"
               :style {:display "inline-flex" :alignItems "baseline"
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
                  [:div {:data-test-id (str "page-" n)
                         :style {:textAlign "center"
                                 ;; FIXME: find a better way to make this div square
                                 :paddingTop 5 :display "inline-block" :width 29 :height 24
                                 :backgroundColor (when selected? (:button-primary style/colors))
                                 :color (if selected? "white" (:button-primary style/colors))
                                 :borderRadius (when selected? "100%")
                                 :cursor (when-not selected? "pointer")}
                         :onClick (when-not selected? #(page-selected n))}
                   n]))
              (create-page-range page-number num-pages))]
        [:div {:data-test-id "next-page"
               :style {:display "inline-flex" :alignItems "baseline"
                       :padding "0 0.75rem"
                       :color (if allow-next
                                (:button-primary style/colors)
                                (:border-light style/colors))
                       :cursor (when allow-next "pointer")}
               :onClick (when allow-next #(page-selected (inc page-number)))}
         "Next"
         (icons/icon {:style {:alignSelf "center" :paddingLeft "0.5rem"}} :angle-right)])))
   :-render-rows-component
   (fn [{:keys [props]}]
     (let [{:keys [rows-per-page per-page-options per-page-selected]} props]
       [:div {:style {:display "inline-flex" :alignItems "baseline"}}
        (style/create-identity-select
         {:data-test-id "per-page"
          :value rows-per-page
          :style {:width 60 :marginRight "0.75rem"}
          :onChange #(per-page-selected (-> % .-target .-value int))}
         per-page-options)
        "per page"]))})
