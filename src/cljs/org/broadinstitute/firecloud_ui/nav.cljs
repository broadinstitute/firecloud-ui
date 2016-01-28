(ns org.broadinstitute.firecloud-ui.nav
  (:require
   clojure.string
   [org.broadinstitute.firecloud-ui.utils :as utils]))


(def ^:private delimiter "/")


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
  ([nav-context]
   (if (clojure.string/blank? (:remaining nav-context))
     (assoc nav-context :segment "")
     (let [remaining (:remaining nav-context)
           stop-index (utils/str-index-of remaining delimiter)
           stop-index (if (neg? stop-index) (count remaining) stop-index)
           segment (subs remaining 0 stop-index)]
       (assoc nav-context
              :segment (js/decodeURIComponent segment)
              :consumed (conj (:consumed nav-context) segment)
              :remaining (subs remaining (inc stop-index)))))))


(defn- id? [segment]
  (and (map? segment)
       (= #{:namespace :name} (set (keys segment)))))

(defn- process-segment [segment]
  (if (id? segment)
    (str (:namespace segment) ":" (:name segment))
    segment))

(defn create-hash [nav-context & segment-names]
  (->> segment-names
    (map process-segment)
    (map js/encodeURIComponent)
    (concat (reverse (:consumed nav-context)))
    (interpose delimiter)
    (apply str)))


(defn create-href [nav-context & segment-names]
  (str "#" (apply create-hash nav-context segment-names)))


(defn navigate [nav-context & segment-names]
  (->> segment-names
    (map process-segment)
    (apply create-hash nav-context)
    (set! (.. js/window -location -hash))))


(defn terminate [nav-context]
  (assoc nav-context :remaining ""))

(defn terminate-when [pred nav-context]
  (if pred (terminate nav-context) nav-context))

(defn back [nav-context]
  (set! (-> js/window .-location .-hash)
    (apply str (interpose delimiter (butlast (reverse (:consumed nav-context)))))))
