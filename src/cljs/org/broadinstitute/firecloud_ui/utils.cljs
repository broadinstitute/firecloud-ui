(ns org.broadinstitute.firecloud-ui.utils
  (:require-macros
   [org.broadinstitute.firecloud-ui.utils :refer [log jslog cljslog]])
  (:require
   cljs.pprint
   [clojure.string :refer [join lower-case split]]
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
                         (let [status-code (.-status xhr)
                               get-parsed-response #(parse-json-string (.-responseText xhr))]
                              (if (= status-code 401)
                                (let [us-xhr (js/XMLHttpRequest.)]
                                     (set! (.-withCredentials us-xhr) true)
                                     (.addEventListener us-xhr "loadend"
                                       (fn []
                                           (let [us-status (.-status us-xhr)
                                                 parsed-us-response (parse-json-string (.-responseText us-xhr))
                                                 user-info (keywordize-keys parsed-us-response)]
                                                (cond
                                                  (and (= us-status 200) (not (:ldap (:enabled (:userInfo user-info)))))
                                                  (on-done {:xhr us-xhr
                                                            :status-code 403
                                                            :success? false
                                                            :status-text "Access Disabled"
                                                            :get-parsed-response parsed-us-response})
                                                  ;TODO: Fix this with a real log-out once the login bug is fixed and logout is implemented.
                                                  (= us-status 401) (set! (-> js/window .-location) "/")
                                                  :else (on-done {:xhr us-xhr
                                                                  :status-code us-status
                                                                  :success? false
                                                                  :status-text (.-statusText us-xhr)
                                                                  :get-parsed-response parsed-us-response})))))
                                     (.open us-xhr "GET" "/service/register")
                                     (.setRequestHeader us-xhr "Authorization" (str "Bearer " @access-token))
                                     (.setRequestHeader us-xhr "Content-Type" "application/json" )
                                     (.setRequestHeader us-xhr "Accept" "application/json" )
                                     (.send us-xhr))
                              (on-done {:xhr xhr
                                       :status-code status-code
                                       :success? (and (>= status-code 200)
                                                      (< status-code 300))
                                       :status-text (.-statusText xhr)
                                       :get-parsed-response get-parsed-response}))))]
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


(defn set-access-token-cookie [token]
      (.set goog.net.cookies "FCtoken" token 300))


(defn get-access-token-cookie []
      (.get goog.net.cookies "FCtoken"))


(defn ajax-orch [path arg-map & {:keys [service-prefix] :or {service-prefix "/service/api"}}]
  (assert (= (subs path 0 1) "/") (str "Path must start with '/': " path))
  (ajax (assoc
         arg-map :url (str service-prefix path)
         :headers (merge {"Authorization" (str "Bearer " @access-token)}
                         (:headers arg-map)))))


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

(defn rand-subset [items]
  (take (rand-int (inc (count items))) (shuffle items)))

(defn rand-recent-time []
  (.format (.subtract (js/moment (js/Date.)) (rand-int 100000) "seconds")))
