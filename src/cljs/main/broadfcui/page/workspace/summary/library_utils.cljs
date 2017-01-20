(ns broadfcui.page.workspace.summary.library-utils
  (:require
    [broadfcui.utils :as utils]
    ))

(defn unpack-attribute-list [value]
  (if (map? value)
    (clojure.string/join ", " (:items value))
    (str value)))


(defn get-related-id-keyword [library-schema property-key]
  (keyword (get-in library-schema [:properties property-key :relatedID])))

(defn get-related-label-keyword [library-schema property-key]
  (keyword (get-in library-schema [:properties property-key :relatedLabel])))

(defn get-related-value [attributes library-schema property-key id?]
  (if id?
    (get attributes (get-related-id-keyword library-schema property-key) nil)
    (get attributes (get-related-label-keyword library-schema property-key) nil)))
