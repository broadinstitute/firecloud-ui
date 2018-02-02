(ns broadfcui.test-utils
  (:require [clojure.string :as string]))


(defn text->test-id [text suffix]
  (-> text
      string/lower-case
      (string/replace-all #"\s+" "-")
      (string/replace-all #"[^a-z\-]" "")
      (str "-" suffix)))
