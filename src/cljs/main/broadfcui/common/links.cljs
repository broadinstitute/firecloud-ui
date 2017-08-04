(ns broadfcui.common.links
  (:require
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))

(defn create-internal [attributes & contents]
  [:a (utils/deep-merge {:href "javascript:;"
                         :style {:textDecoration "none" :color (:button-primary style/colors)}}
                        attributes)
   contents])

(defn create-external [attributes & contents]
  [:a (merge {:target "_blank"} attributes)
   contents
   icons/external-link-icon])
