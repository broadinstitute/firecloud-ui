(ns org.broadinstitute.firecloud-ui.nav
  (:require
   clojure.string
   [org.broadinstitute.firecloud-ui.utils :as utils]))


(defn get-hash-value []
  (let [hash (-> js/window .-location .-hash)]
    (if (= (subs hash 0 1) "#") (subs hash 1) hash)))


(defn create-nav-context
  "Returns a new nav-context from the browser's location hash."
  []
  (let [hash (get-hash-value)]
    {:hash hash :consumed (list) :remaining hash}))


(defn parse-segment
  "Returns a new nav-context with :segment set to the parsed segment."
  ([nav-context] (parse-segment nav-context "/"))
  ([nav-context delimeter]
   (if (clojure.string/blank? (:remaining nav-context))
     (assoc nav-context :segment "")
     (let [remaining (:remaining nav-context)
           stop-index (utils/str-index-of remaining delimeter)
           stop-index (if (neg? stop-index) (count remaining) stop-index)
           segment (subs remaining 0 stop-index)]
       (assoc nav-context
              :segment (js/decodeURIComponent segment)
              :consumed (conj (:consumed nav-context) (str segment delimeter))
              :remaining (subs remaining (inc stop-index)))))))


(defn navigate [nav-context segment-name]
  (set! (-> js/window .-location .-hash)
        (str (apply str (reverse (:consumed nav-context))) (js/encodeURIComponent segment-name))))

(defn back [nav-context]
  (set! (-> js/window .-location .-hash)
    (str (apply str (butlast (reverse (:consumed nav-context)))))))
