(ns broadfcui.utils
  (:require-macros
   [broadfcui.utils :refer [log jslog cljslog pause restructure multi-swap! generate-build-timestamp]])
  (:require
   [clojure.string :as string]
   cljs.pprint
   goog.net.cookies
   ))


(defn str-index-of
  ([s what] (.indexOf s what))
  ([s what start-index] (.indexOf s what start-index)))


(defn contains [s what]
  (<= 0 (str-index-of s what)))


(defn dequote [s]
  (if (and (string/starts-with? s "\"") (string/ends-with? s "\""))
    (subs s 1 (- (count s) 1))
    s))


(defn ->json-string [x]
  (js/JSON.stringify (clj->js x)))


(defn encode [text]
  ;; character replacements modeled after Lucene's SimpleHTMLEncoder.
  (string/escape text {\" "&quot;" \& "&amp;" \< "&lt;", \> "&gt;", \\ "&#x27;" \/ "&#x2F;"}))


(defn parse-json-string
  ([x] (parse-json-string x false))
  ([x keywordize-keys?] (parse-json-string x keywordize-keys? true))
  ([x keywordize-keys? throw-on-error?]
   (if throw-on-error?
     (js->clj (js/JSON.parse x) :keywordize-keys keywordize-keys?)
     (try
       [(js->clj (js/JSON.parse x) :keywordize-keys keywordize-keys?) false]
       (catch :default e ; match js/Error and js/Object
         [nil e])))))


(defn local-storage-write
  ([k v] (local-storage-write k v false))
  ([k v stringify?]
   (assert (keyword? k))
   (let [value (if stringify? (js/JSON.stringify v) v)]
     (.setItem window.localStorage (subs (str k) 1) value))))


(defn local-storage-read
  ([k] (local-storage-read k false))
  ([k parse?]
   (assert (keyword? k))
   (let [value (.getItem window.localStorage (subs (str k) 1))]
     (if parse? (js/JSON.parse value) value))))


(defn local-storage-remove [k]
  (assert (keyword? k))
  (.removeItem window.localStorage (subs (str k) 1)))


(defn deep-merge [& maps]
  (doseq [x maps] (assert (or (nil? x) (map? x)) (str "not a map: " x)))
  (apply
   merge-with
   (fn [x1 x2] (if (and (map? x1) (map? x2)) (deep-merge x1 x2) x2))
   maps))


(defn generate-form-data
  "Create a blob of multipart/form-data from the provided map."
  [params]
  (let [form-data (js/FormData.)]
    (doseq [[k v] params]
      (.append form-data (name k) v))
    form-data))


(defn insert [vec i elem]
  (apply conj (subvec vec 0 i) elem (subvec vec i)))

(defn delete [vec i]
  (apply conj (subvec vec 0 i) (subvec vec (inc i))))

(defn move [vec start end]
  (let [elem (nth vec start)]
    (-> vec
        (delete start)
        (insert end elem))))


(defn index-of [coll item]
  (first (keep-indexed (fn [i x] (when (= item x) i)) coll)))

(defn first-matching [pred coll]
  (first (filter pred coll)))

(defn first-matching-index [pred coll]
  (first (keep-indexed (fn [i x] (when (pred x) i)) coll)))

(defn seq-contains? [coll item]
  (contains? (set coll) item))

(defn find-duplicates [coll]
  (for [[elem freq] (frequencies coll)
        :when (> freq 1)]
    elem))

(defn sort-match
  "Sort a collection to match the ordering of a given 'target' collection"
  [pattern coll]
  (sort-by #(index-of pattern %) coll))

(defn changes [keys coll1 coll2]
  (map (fn [key] (not= (key coll1) (key coll2))) keys))

(defn any-change [keys coll1 coll2]
  (some identity (changes keys coll1 coll2)))

(defn replace-top [coll x]
  (conj (pop coll) x))

(defn _24-hours-from-now-ms []
  (+ (.now js/Date) (* 1000 60 60 24)))

(defn _30-days-from-date-ms [date]
  (+ date (* 1000 60 60 24 30)))

(defn index-by [key m]
  (into {} (map (juxt key identity) m)))


(defn filter-keys [pred m]
  (into (empty m)
        (filter (comp pred key) m)))

(defn filter-values [pred m]
  (into (empty m)
        (filter (comp pred val) m)))

(defn filter-kv [pred m]
  (into (empty m)
        (filter (fn [[k v]] (pred k v)) m)))

(defn map-keys [f m]
  (into (empty m)
        (map (fn [[k v]] [(f k) v]) m)))

(defn map-values [f m]
  (into (empty m)
        (map (fn [[k v]] [k (f v)]) m)))

(defn map-kv [f m]
  (into (empty m)
        (map (fn [[k v]] (f k v)) m)))

(defn get-app-root-element []
  (.getElementById js/document "app"))

(def build-timestamp (generate-build-timestamp))
