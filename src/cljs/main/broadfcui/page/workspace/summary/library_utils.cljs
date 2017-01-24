(ns broadfcui.page.workspace.summary.library-utils
  (:require
    [broadfcui.utils :as utils]
    ))

(defn unpack-attribute-list [value]
  (if (map? value)
    (clojure.string/join ", " (:items value))
    (str value)))


(defn get-related-id+label-props [library-schema property]
  (map #(keyword (get-in library-schema [:properties property %]))
       [:relatedID :relatedLabel]))

(defn get-related-id+label [attributes library-schema property]
  (map #(get attributes (keyword (get-in library-schema [:properties property %])))
       [:relatedID :relatedLabel]))
