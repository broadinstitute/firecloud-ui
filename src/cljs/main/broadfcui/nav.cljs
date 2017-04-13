(ns broadfcui.nav
  (:require
   clojure.string
   [dmohs.react :as r]
   [broadfcui.utils :as u]))

(defonce all-path-handlers (atom {}))

(defn defpath [k handler]
  (assert (contains? handler :regex))
  (assert (contains? handler :component))
  (assert (contains? handler :make-props))
  (assert (contains? handler :make-path))
  (assert (not (contains? @all-path-handlers k)) (str "Key " k " already defined"))
  (swap! all-path-handlers assoc k handler))

(defn clear-paths []
  (reset! all-path-handlers {}))

(defn find-path-handler [window-hash]
  (let [cleaned (js/decodeURI (subs window-hash 1))
        matching-handlers (filter
                           (complement nil?)
                           (map
                            (fn [[k handler]]
                              (when-let [matches (re-matches (:regex handler) cleaned)]
                                (let [make-props (:make-props handler)]
                                  (assoc handler
                                         :key k
                                         ;; First match is the entire string, so toss that one.
                                         :make-props #(apply make-props (rest matches))))))
                            @all-path-handlers))]
    (assert (not (> (count matching-handlers) 1))
            (str "Multiple keys matched path: " (map :key matching-handlers)))
    (first (not-empty matching-handlers))))

(defn get-path [k & args]
  (let [handler (get @all-path-handlers k)
        {:keys [make-path]} handler]
    (assert handler
            (str "No handler found for key " k ". Valid path keys are: " (keys @all-path-handlers)))
    (js/encodeURI (apply make-path args))))

(defn get-link [k & args]
  (str "#" (apply get-path k args)))

(defn go-to-path [k & args]
  (aset js/window "location" "hash" (apply get-path k args)))

(defn is-current-path? [k & args]
  (= (apply get-path k args) (subs (aget js/window "location" "hash") 1)))
