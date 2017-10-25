(ns broadfcui.utils
  (:require-macros
   [broadfcui.utils :refer [log jslog cljslog pause restructure]])
  (:require
   [clojure.string :as string]
   cljs.pprint
   goog.net.cookies
   [broadfcui.config :as config]
   ))


(defn str-index-of
  ([s what] (.indexOf s what))
  ([s what start-index] (.indexOf s what start-index)))


(defn contains [s what]
  (<= 0 (str-index-of s what)))


(defn contains-ignore-case [s what]
  (contains (string/lower-case s) (string/lower-case what)))


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
       (catch js/Object e
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


(defonce use-live-data? (atom (let [value (local-storage-read ::use-live-data? true)]
                                (if (nil? value) true value))))
(add-watch
 use-live-data? :save-to-local-storage
 (fn [_ _ _ ns]
   (local-storage-write ::use-live-data? ns true)))


(defonce ^:private user-listeners (atom {}))
(defn add-user-listener [k on-change]
  (swap! user-listeners assoc k on-change))
(defn remove-user-listener [k]
  (swap! user-listeners dissoc k))


(defonce auth2-atom (atom nil))
(defn set-google-auth2-instance! [instance]
  (reset! auth2-atom instance)
  (-> instance
      (aget "currentUser")
      (js-invoke
       "listen" (fn [u]
                  (doseq [[_ on-change] @user-listeners]
                    (on-change u))))))

(defn get-access-token []
  (-> @auth2-atom
      (aget "currentUser") (js-invoke "get") (js-invoke "getAuthResponse") (aget "access_token")))

(defn get-user-email []
  (-> @auth2-atom
      (aget "currentUser") (js-invoke "get") (js-invoke "getBasicProfile") (js-invoke "getEmail")))


(defn get-cookie-domain []
  (if (= "local.broadinstitute.org" js/window.location.hostname)
    "local.broadinstitute.org"
    (string/join "." (rest (string/split js/window.location.hostname ".")))))

(defn delete-access-token-cookie []
  (.remove goog.net.cookies "FCtoken" "/" (get-cookie-domain)))

(defn set-access-token-cookie [token]
  (if token
    (.set goog.net.cookies "FCtoken" token -1 "/" (get-cookie-domain))
    (delete-access-token-cookie)))

(defn refresh-access-token [] (set-access-token-cookie (get-access-token)))

;; TODO - make this unnecessary
(def content-type=json {"Content-Type" "application/json"})

(defonce ^:private recent-ajax-urls (atom #{}))
(def ^:private double-call-threshold 500)

(defn ajax [arg-map]
  (let [url (:url arg-map)
        on-done (:on-done arg-map)
        method (if-let [method (:method arg-map)] (string/upper-case (name method)) "GET")
        headers (:headers arg-map)
        data (:data arg-map)
        with-credentials? (:with-credentials? arg-map)
        canned-response-params (when-not @use-live-data? (:canned-response arg-map))]
    (assert url (str "Missing url parameter: " arg-map))
    (assert on-done (str "Missing on-done callback: " arg-map))

    (when (config/debug?)
      (let [request (restructure url data)]
        (when (contains? @recent-ajax-urls request)
          (js/console.warn (str "WARNING: repeated ajax calls to " url
                                (when data (str " with payload " data)))))
        (swap! recent-ajax-urls conj request)
        (js/setTimeout #(swap! recent-ajax-urls disj request) double-call-threshold)))

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
      (and (zero? status-code)
           (seq status-text))))

(defn- check-server-down [status-code]
  (or (= 501 status-code)
      (<= 503 status-code 599)))

(defn get-exponential-backoff-interval [attempt]
  (* (.pow js/Math 2 attempt) 1000)) ;; backoff interval in millis


(defn ajax-orch [path arg-map & {:keys [service-prefix] :or {service-prefix "/api"}}]
  (assert (= (subs path 0 1) "/") (str "Path must start with '/': " path))
  (let [on-done (:on-done arg-map)]
    (ajax (assoc arg-map
            :url (str (config/api-url-root) service-prefix path)
            :headers (merge {"Authorization" (str "Bearer " (get-access-token))}
                            (:headers arg-map))
            :on-done (fn [{:keys [status-code status-text] :as m}]
                       (when (and (not @server-down?) (not @maintenance-mode?))
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
  (string/join ", " (map (fn [k] (str k "→" (get m k))) (keys m))))


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

(defn replace-top [coll x]
  (conj (pop coll) x))

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

(defn get-app-root-element []
  (.getElementById js/document "app"))


(defn log-methods [prefix defined-methods]
  (map-kv (fn [method-name method]
            [method-name
             (fn [& args]
               (log (str prefix " - " (name method-name)))
               (apply method args))])
          defined-methods))


(defn with-window-listeners [listeners-map defined-methods]
  (let [did-mount
        (fn [{:keys [locals] :as data}]
          (doseq [[event function] listeners-map]
            (let [func (partial function data)]
              (swap! locals assoc (str "WINDOWLISTENER " event) func)
              (.addEventListener js/window event func)))
          (when-let [defined-did-mount (:component-did-mount defined-methods)]
            (defined-did-mount data)))
        will-unmount
        (fn [{:keys [locals] :as data}]
          (doseq [[event _] listeners-map]
            (.removeEventListener js/window event (@locals (str "WINDOWLISTENER " event))))
          (when-let [defined-will-unmount (:component-will-unmount defined-methods)]
            (defined-will-unmount data)))]
    (assoc defined-methods
      :component-did-mount did-mount
      :component-will-unmount will-unmount)))


(defn track-initial-render [defined-methods]
  (let [will-mount
        (fn [{:keys [locals] :as data}]
          (swap! locals assoc :initial-render? true)
          (when-let [defined-will-mount (:component-will-mount defined-methods)]
            (defined-will-mount data)))
        did-mount
        (fn [{:keys [locals] :as data}]
          (swap! locals dissoc :initial-render?)
          (when-let [defined-did-mount (:component-did-mount defined-methods)]
            (defined-did-mount data)))]
    (assoc defined-methods
      :component-will-mount will-mount
      :component-did-mount did-mount)))
