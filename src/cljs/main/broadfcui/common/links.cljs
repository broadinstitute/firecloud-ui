(ns broadfcui.common.links
  (:require
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))

(defn create-internal [{:keys [text] :as attributes}]
  [:a (utils/deep-merge {:href "javascript:;"
                         :style {:textDecoration "none" :color (:button-primary style/colors)}}
                        (dissoc attributes :text))
   text])

(defn create-external [{:keys [text] :as attributes}]
  [:a (merge {:target "_blank"} (dissoc attributes :text))
   text
   icons/external-link-icon])
