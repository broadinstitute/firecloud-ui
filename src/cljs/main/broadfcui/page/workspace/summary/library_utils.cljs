(ns broadfcui.page.workspace.summary.library-utils
  (:require
    [broadfcui.utils :as utils]
    ))

(defn unpack-attribute-list [value]
  (if (map? value)
    (clojure.string/join ", " (:items value))
    value))


(defn get-related-id+label-props [library-schema property]
  (map #(keyword (get-in library-schema [:properties property %]))
       [:relatedID :relatedLabel]))

(defn get-related-id+label [attributes library-schema property]
  (map #(get attributes (keyword (get-in library-schema [:properties property %])))
       [:relatedID :relatedLabel]))

(defn validate-required [attributes questions required-attributes]
  (let [required-props (->> questions
                            (map keyword)
                            (filter (partial contains? required-attributes))
                            set)
        missing-props (clojure.set/difference required-props (-> attributes keys set))]
    (when-not (empty? missing-props)
      {:error "Please provide all required attributes"
       :invalid missing-props})))