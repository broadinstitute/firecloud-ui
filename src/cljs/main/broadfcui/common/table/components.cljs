(ns broadfcui.common.table.components
  (:require
    [dmohs.react :as react]
    [broadfcui.common.style :as style]
    [broadfcui.utils :as utils]
    ))


(defn- flex-params [width]
  (if (= width :auto)
    {:flexBasis "auto" :flexGrow 1 :flexShrink 1}
    {:flexBasis width :flexGrow 0 :flexShrink 0}))


(defn table-header [{:keys [columns style]}]
  [:div {:style (merge {:display "flex"} (:header-row style))}
   (map
    (fn [{:keys [width header]}]
      [:div {:style (flex-params width)}
       header])
    columns)])


(defn table-body [{:keys [rows columns style]}]
  [:div {}
   (map-indexed
    (fn [index row]
      [:div {:style (merge {:display "flex"} ((:row style) (utils/restructure index row)))}
       (map
        (fn [{:keys [width render]}]
          [:div {:style (flex-params width)}
           (render row)])
        columns)])
    rows)])
