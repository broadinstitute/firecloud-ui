(ns broadfcui.common.flex-utils
  (:require
    [broadfcui.utils :as utils]
    ))


(defn flex-box [attrs & children]
  [:div (utils/deep-merge {:style {:display "flex"}} attrs)
   children])

(defn flex-strut [size]
  [:div {:style {:flexGrow 0 :flexShrink 0 :flexBasis size}}])

(def flex-spacer
  [:div {:style {:flex "1 1 auto"}}])
