(ns org.broadinstitute.firecloud-ui.utils
  (:require-macros
   [org.broadinstitute.firecloud-ui.utils :refer [log jslog cljslog pause]])
  (:require
   cljs.pprint
   [clojure.string :refer [join lower-case split]]
   [org.broadinstitute.firecloud-ui.config :as config]
   ))


(defn str-index-of
  ([s what] (.indexOf s what))
  ([s what start-index] (.indexOf s what start-index)))


(defn contains [s what]
  (<= 0 (str-index-of s what)))


(defn contains-ignore-case [s what]
  (contains (lower-case s) (lower-case what)))


(defn matches-filter-text [source filter-text]
  (let [lc-source (lower-case source)]
    (every? (fn [word] (contains lc-source word)) (split (lower-case filter-text) #"\s+"))))


(defn call-external-object-method
  "Call an external object's method by name, since a normal call will get renamed during
   advanced compilation and cause an error."
  [obj method-name & args]
  (apply (.bind (aget obj (name method-name)) obj) args))


(defn ->json-string [x]
  (js/JSON.stringify (clj->js x)))


(defn parse-json-string [x]
  (js->clj (js/JSON.parse x)))


(defn keywordize-keys [m]
      (into {} (map (fn [[k v]]
                        [(keyword k) (if (map? v) (keywordize-keys v) v)])
                    m)))

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


(defonce use-live-data? (atom (let [value (local-storage-read ::use-live-data? true)]
                                (if (nil? value) true value))))
(add-watch
 use-live-data? :save-to-local-storage
 (fn [k r os ns]
   (local-storage-write ::use-live-data? ns true)))

(def access-token (atom nil))

(defn get-cookie-domain []
  (if (= "local.broadinstitute.org" js/window.location.hostname)
    "local.broadinstitute.org"
    (join "." (rest (split js/window.location.hostname ".")))))

(defn delete-access-token-cookie []
  (.remove goog.net.cookies "FCtoken" "/" (get-cookie-domain)))

(defn set-access-token-cookie [token]
  (if token
    (.set goog.net.cookies "FCtoken" token -1 "/" (get-cookie-domain))
    (delete-access-token-cookie)))

(defn get-access-token-cookie []
  (.get goog.net.cookies "FCtoken"))

(defn ajax [arg-map]
  (let [url (:url arg-map)
        on-done (:on-done arg-map)
        method (if-let [method (:method arg-map)] (clojure.string/upper-case (name method)) "GET")
        headers (:headers arg-map)
        data (:data arg-map)
        with-credentials? (:with-credentials? arg-map)
        canned-response-params (when-not @use-live-data? (:canned-response arg-map))]
    (assert url (str "Missing url parameter: " arg-map))
    (assert on-done (str "Missing on-done callback: " arg-map))
    (let [xhr (if-not canned-response-params
                (js/XMLHttpRequest.)
                (let [xhr (js-obj)]
                  (doseq [[k v] (dissoc canned-response-params :delay-ms)]
                    (aset xhr (name k) v))
                  xhr))
          call-on-done (fn []
                         (on-done (let [status-code (.-status xhr)]
                                    {:xhr xhr
                                     :status-code status-code
                                     :success? (<= 200 status-code 299)
                                     :status-text (.-statusText xhr)
                                     :get-parsed-response #(parse-json-string
                                                            (.-responseText xhr))})))]
      (when with-credentials?
        (set! (.-withCredentials xhr) true))
      (if canned-response-params
        (do
          (jslog "Mocking AJAX Request:"
            (merge
              {:method method :url url}
              (when headers {:headers headers})
              (when data {:data data})))
          (if-let [delay-ms (:delay-ms canned-response-params)]
            (js/setTimeout call-on-done delay-ms)
            (call-on-done)))
        (do
          (.addEventListener xhr "loadend" call-on-done)
          (.open xhr method url)
          (doseq [[k v] headers]
            (.setRequestHeader xhr k v))
          (if data
            (.send xhr data)
            (.send xhr)))))))


(defonce auth-expiration-handler nil)


(defonce server-down? (atom false))
(defonce maintenance-mode? (atom false))
(defonce pending-calls (atom []))


(defn ajax-orch [path arg-map & {:keys [service-prefix ignore-auth-expiration?] :or {service-prefix "/api"}}]
  (assert (= (subs path 0 1) "/") (str "Path must start with '/': " path))
  (let [on-done (:on-done arg-map)]
    (ajax (assoc
           arg-map :url (str (config/api-url-root) service-prefix path)
           :headers (merge {"Authorization" (str "Bearer " @access-token)}
                           (:headers arg-map))
           :on-done (fn [{:keys [status-code] :as m}]
                      (when (and (not @server-down?)  (not @maintenance-mode?))
                        (cond
                          (contains #{0 502} status-code) (reset! maintenance-mode? true)
                          (contains? (set (range 500 600)) status-code) (reset! server-down? true)))
                      ;; Handle auth token expiration
                      (if (and (= status-code 401) (not ignore-auth-expiration?))
                        (do
                          (swap! pending-calls conj [path arg-map
                                                     :service-prefix service-prefix
                                                     :ignore-auth-expiration? true])
                          (auth-expiration-handler
                           (fn []
                             (dorun (map (fn [x] (apply ajax-orch x)) @pending-calls))
                             (reset! pending-calls []))))
                        (on-done m)))))))


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


(defn map-to-string [m]
  (join ", " (map (fn [k] (str k "→" (get m k))) (keys m))))


(defn distance [x1 y1 x2 y2]
  (let [dx (- x1 x2)
        dy (- y1 y2)]
    (js/Math.sqrt (+ (* dx dx) (* dy dy)))))


(defn insert [vec i elem]
  (apply conj (subvec vec 0 i) elem (subvec vec i)))

(defn delete [vec i]
  (apply conj (subvec vec 0 i) (subvec vec (inc i))))

(defn move [vec start end]
  (let [elem (nth vec start)]
    (insert (delete vec start) (if (> end start) (dec end) end) elem)))


(defn index-of [coll item]
  (first (keep-indexed (fn [i x] (when (= item x) i)) coll)))

(defn sort-match
  "Sort a collection to match the ordering of a given 'target' collection"
  [pattern coll]
  (sort-by #(index-of pattern %) coll))


(defn rand-subset [items]
  (take (rand-int (inc (count items))) (shuffle items)))

(defn rand-recent-time []
  (.format (.subtract (js/moment (js/Date.)) (rand-int 100000) "seconds")))
