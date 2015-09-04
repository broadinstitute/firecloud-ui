(ns org.broadinstitute.firecloud-ui.utils
  (:require
    [clojure.string :refer [join lower-case split]]
    ))


(defn rlog [& args]
  (let [arr (array)]
    (doseq [x args] (.push arr x))
    (js/console.log.apply js/console arr))
  (last args))


(defn jslog [& args]
  (apply rlog (map clj->js args))
  (last args))


(defn cljslog [& args]
  (apply rlog (map pr-str args))
  (last args))


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


;;
;; TODO(dmohs): Move this stuff back into session namespace.
;;


(defonce current-user (atom nil))


(defonce on-log-out-atom (atom nil))


(defn get-current-user []
  @current-user)


(defn set-current-user [user]
  (reset! current-user user))


(defn log-out []
  (-> js/gapi
    (aget "auth2")
    (call-external-object-method :getAuthInstance)
    (call-external-object-method :signOut)
    (call-external-object-method
      :then (fn [] (reset! current-user nil) (@on-log-out-atom) (.reload (.-location js/window))))))


(defn on-log-out [callback]
  (reset! on-log-out-atom callback))


;;
;; TODO(dmohs): Move this stuff into separate namespace (endpoints?)
;;


(def use-live-data? true)
(when-not use-live-data? (assert goog.DEBUG "Mock data in use but DEBUG is false."))


(defn ajax [arg-map]
  (let [url (:url arg-map)
        on-done (:on-done arg-map)
        method (if-let [method (:method arg-map)] (clojure.string/upper-case (name method)) "GET")
        headers (:headers arg-map)
        data (:data arg-map)
        with-credentials? (:with-credentials? arg-map)
        canned-response-params (when-not use-live-data? (:canned-response arg-map))]
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
                           (on-done {:xhr xhr
                                     :status-code status-code
                                     :success? (and (>= status-code 200)
                                                    (< status-code 300))
                                     :status-text (.-statusText xhr)
                                     :get-parsed-response get-parsed-response})))]
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


(defn ajax-orch [path arg-map]
  (assert (= (subs path 0 1) "/") (str "Path must start with '/': " path))
  (let [access-token (-> (get-current-user)
                         (call-external-object-method :getAuthResponse)
                         (aget "access_token"))]
    (ajax (assoc
           arg-map :url (str "/api" path)
           :headers (merge {"Authorization" (str "Bearer " access-token)}
                           (:headers arg-map))))))


(defn call-ajax-orch [path arg-map]
  (ajax-orch path (assoc arg-map
                    :on-done (fn [{:keys [success? xhr] :as map}]
                               (if success?
                                 ((:on-success arg-map) (merge map {:parsed-response (parse-json-string (.-responseText xhr))}))
                                 ((:on-failure arg-map) (merge map {:status-text (.-statusText xhr)}))))
                    :canned-response {:status 200 :delay-ms (rand-int 2000)
                                      :responseText (if-let [mock-data (:mock-data arg-map)]
                                                      (->json-string mock-data))})))


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
  (take (rand-int (count items)) (shuffle items)))

(defn rand-recent-time []
  (.format (.subtract (js/moment (js/Date.)) (rand-int 100000) "seconds")))
