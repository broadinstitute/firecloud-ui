(ns org.broadinstitute.firecloud-ui.page.workspace.summary.library-utils
  (:require
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

(defn unpack-attribute-list [value]
  (if (map? value)
    (clojure.string/join ", " (:items value))
    (str value)))

(defn get-related-id-keyword [library-schema property-key]
  (keyword (get-in library-schema [:properties property-key :relatedID])))

(defn get-related-id [attributes library-schema property-key default]
  (get attributes (get-related-id-keyword library-schema property-key) default))
