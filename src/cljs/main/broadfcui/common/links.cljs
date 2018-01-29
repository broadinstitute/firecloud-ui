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

(defn create-download [label url filename]
  [:a {:href url
       :download filename}
   label
   icons/download-icon])

(defn create-download-from-object [label object filename]
  (let [payload-blob (js/Blob. (js/Array. object) {:type "text/plain"})
        payload-object-url (.createObjectURL js/URL payload-blob)]
    (create-download label payload-object-url filename)))

(defn create-external [attributes & contents]
  [:a (merge {:target "_blank"} attributes)
   contents
   icons/external-link-icon])
