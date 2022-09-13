(ns broadfcui.common.flex-utils
  (:require
   [broadfcui.utils :as utils]
   ))


(defn box [attrs & children]
  [:div (utils/deep-merge {:style {:display "flex"}} attrs)
   children])

(defn strut [size]
  [:div {:style {:flexGrow 0 :flexShrink 0 :flexBasis size}}])

(def spring
  [:div {:style {:flex "1 1 auto"}}])
