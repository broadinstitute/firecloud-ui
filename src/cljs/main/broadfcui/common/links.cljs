(ns broadfcui.common.links
  (:require
   [dmohs.react :as react]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))

(defn create-internal [attributes & contents]
  [:a (utils/deep-merge {:href "javascript:;"
                         :style {:textDecoration "none" :color (:button-primary style/colors)}}
                        attributes)
   contents])

(defn create-download
  ([label url]
   (create-download label url nil)) ; nil :download is fine with React
  ([label url filename]
   [:a {:href url
        :download filename}
    label
    icons/download-icon]))

(defn create-download-from-object [label object filename]
  [(react/create-class
    {:render
     (fn [{:keys [props locals]}]
       (let [{:keys [label object filename]} props
             payload-blob (js/Blob. (js/Array. object) {:type "text/plain"})
             payload-object-url (js/URL.createObjectURL payload-blob)]
         (swap! locals assoc :objectUrl payload-object-url)
         (create-download label payload-object-url filename)))
     :component-will-unmount
     (fn [{:keys [locals]}]
       (js/URL.revokeObjectURL (:objectUrl @locals)))}) {:label label :object object :filename filename}])

(defn create-external [attributes & contents]
  [:a (merge {:target "_blank"} attributes)
   contents
   icons/external-link-icon])
