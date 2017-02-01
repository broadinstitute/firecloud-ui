(ns broadfcui.utils
  (:require-macros
   [broadfcui.utils :refer [log jslog cljslog pause]])
  (:require
    cljs.pprint
    [clojure.string :refer [join lower-case split]]
    [broadfcui.config :as config]
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
   advanced compilation and cause an error.
   DEPRECATED: this is what js-invoke is for"
  [obj method-name & args]
  (apply (.bind (aget obj (name method-name)) obj) args))


(defn ->json-string [x]
  (js/JSON.stringify (clj->js x)))


(defn parse-json-string
  ([x] (parse-json-string x false))
  ([x keywordize-keys?] (parse-json-string x keywordize-keys? true))
  ([x keywordize-keys? throw-on-error?]
   (if throw-on-error?
     (js->clj (js/JSON.parse x) :keywordize-keys keywordize-keys?)
     (try
       [(js->clj (js/JSON.parse x) :keywordize-keys keywordize-keys?) false]
       (catch js/Object e
         [nil e])))))


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


(defonce ^:private user-listeners (atom {}))
(defn add-user-listener [k on-change]
  (swap! user-listeners assoc k on-change))
(defn remove-user-listener [k]
  (swap! user-listeners dissoc k))


(defonce google-auth2-instance (atom nil))
(defn set-google-auth2-instance! [instance]
  (reset! google-auth2-instance instance)
  (-> instance
      (aget "currentUser")
      (js-invoke
       "listen" (fn [u]
                  (doseq [[_ on-change] @user-listeners]
                    (on-change u))))))

(defn get-access-token []
  (-> @google-auth2-instance
      (aget "currentUser") (js-invoke "get") (js-invoke "getAuthResponse") (aget "access_token")))


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

;; TODO - make this unnecessary
(def content-type=json {"Content-Type" "application/json"})

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
                                     :raw-response (.-responseText xhr)
                                     :get-parsed-response
                                     (fn [& [keywordize-keys?]]
                                       (parse-json-string (.-responseText xhr)
                                                          (if (some? keywordize-keys?) keywordize-keys? true)))})))]
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


(defonce server-down? (atom false))
(defonce maintenance-mode? (atom false))


(defn- check-maintenance-mode [status-code status-text]
  (or (= status-code 502)
      ;; status code 0 means maintenance mode--unless it's this net::ERR_CONNECTION_REFUSED thing
      ;; which has a completely empty response, so exclude that.
      (and (= status-code 0)
           (not (empty? status-text)))))

(defn- check-server-down [status-code]
  (or (= 501 status-code)
      (<= 503 status-code 599)))


(defn ajax-orch [path arg-map & {:keys [service-prefix] :or {service-prefix "/api"}}]
  (assert (= (subs path 0 1) "/") (str "Path must start with '/': " path))
  (let [on-done (:on-done arg-map)]
    (ajax (assoc
           arg-map :url (str (config/api-url-root) service-prefix path)
           :headers (merge {"Authorization" (str "Bearer " (get-access-token))}
                           (:headers arg-map))
           :on-done (fn [{:keys [status-code status-text raw-response] :as m}]
                      (when (and (not @server-down?)  (not @maintenance-mode?))
                        (cond
                          (check-maintenance-mode status-code status-text) (reset! maintenance-mode? true)
                          (check-server-down status-code) (reset! server-down? true)))
                      (on-done m))))))


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
    (-> vec
        (delete start)
        (insert end elem))))


(defn index-of [coll item]
  (first (keep-indexed (fn [i x] (when (= item x) i)) coll)))

(defn first-matching-index [pred coll]
  (first (keep-indexed (fn [i x] (when (pred x) i)) coll)))

(defn find-duplicates [coll]
  (for [[elem freq] (frequencies coll)
        :when (> freq 1)]
    elem))

(defn sort-match
  "Sort a collection to match the ordering of a given 'target' collection"
  [pattern coll]
  (sort-by #(index-of pattern %) coll))


(defn rand-subset [items]
  (take (rand-int (inc (count items))) (shuffle items)))

(defn _24-hours-from-now-ms []
  (+ (.now js/Date) (* 1000 60 60 24)))

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

(defn maybe-pluralize [number unit]
  (if (> number 1)
    (str number " " unit "s")
    (str number " " unit)))
