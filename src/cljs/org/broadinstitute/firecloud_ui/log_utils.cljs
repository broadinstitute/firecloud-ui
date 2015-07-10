;; Create a namespace for these utilities
;; require imports
(ns org.broadinstitute.firecloud-ui.log-utils
  (:require
    [clojure.string]
    ))

(defn rlog 
  "raw loggging utility"
  [& args]
  (let [arr (array)]
    (doseq [x args] (.push arr x))
    (js/console.log.apply js/console arr))
  (last args))

(defn jslog 
  "pretty-print logging wrapper"
  [& args]
  (apply rlog (map clj->js args)))

(defn cljslog 
  "raw string logging wrapper"
  [& args]
  (apply rlog (map pr-str args)))
