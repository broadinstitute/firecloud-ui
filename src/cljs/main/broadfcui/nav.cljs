(ns broadfcui.nav
  (:require
   clojure.string
   [dmohs.react :as r]
   [broadfcui.utils :as u]))

(defonce paths (atom []))

(defn defpath [k m]
  (assert (contains? m :regex))
  (assert (contains? m :component))
  (assert (contains? m :make-props))
  (assert (contains? m :make-path))
  (swap! paths conj (assoc m :key k)))

(defn clear-paths []
  (reset! paths []))

(defn find-path-handler [window-hash]
  (let [cleaned (subs window-hash 1)
        cleaned (js/decodeURI cleaned)
        matching-handlers (filter
                           (complement nil?)
                           (map
                            (fn [p]
                              (when-let [matches (re-matches (:regex p) cleaned)]
                                (update p :make-props
                                        ;; First match is the entire string, so toss that one.
                                        (fn [f] #(apply f (rest matches))))))
                            @paths))]
    (assert (not (> (count matching-handlers) 1))
            (str "Multiple keys matched path: " (map :key matching-handlers)))
    (if (empty? matching-handlers)
      nil
      (first matching-handlers))))

(defn get-path [k & args]
  (let [handler (first (filter #(= k (:key %)) @paths))
        {:keys [make-path]} handler]
    (assert handler (str "No handler found for key " k ". Valid path keys are: " (map :key @paths)))
    (js/encodeURI (apply make-path args))))

(defn get-link [k & args]
  (str "#" (apply get-path k args)))

(defn go-to-path [k & args]
  (aset js/window "location" "hash" (apply get-path k args)))

(defn is-current-path? [k & args]
  (= (apply get-path k args) (subs (aget js/window "location" "hash") 1)))
