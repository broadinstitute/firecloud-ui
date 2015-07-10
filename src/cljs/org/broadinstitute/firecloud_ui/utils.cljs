;; Create a namespace for these utilities
;; require imports
(ns org.broadinstitute.firecloud-ui.utils
  (:require
    [clojure.string]
    ))


;;wrapper for calling external functions
(defn call-external-object-method [obj method-name & args]
  "Call an external object's method by name, since a normal call will get renamed during
   advanced compilation and cause an error."
  (apply (.bind (aget obj (name method-name)) obj) args))




;; FLAG whether to use live-data or not
(def use-live-data? true)



;; AJAX helper routine for I/O
(defn ajax [arg-map]
  ;; let-based assignment block
  (let [url (:url arg-map)
        on-done (:on-done arg-map)
        method (if-let [method (:method arg-map)] (clojure.string/upper-case (name method)) "GET")
        headers (:headers arg-map)
        data (:data arg-map)
        with-credentials? (:with-credentials? arg-map)
        canned-response-params (when (and goog.DEBUG (not use-live-data?))
                                 (:canned-response arg-map))]
    (assert url (str "Missing url parameter: " arg-map))
    (assert on-done (str "Missing on-done callback: " arg-map))
    ;; let-based asignment block
    (let [xhr (if-not canned-response-params
                (js/XMLHttpRequest.)
                (let [xhr (js-obj)]
                  (doseq [[k v] (dissoc canned-response-params :delay-ms)]
                    (aset xhr (name k) v))
                  xhr))
          call-on-done (fn []
                         ((:on-done arg-map) {:xhr xhr
                                              :status-code (.-status xhr)
                                              :success? (and (>= (.-status xhr) 200)
                                                             (< (.-status xhr) 300))}))]
      (when with-credentials?
        (set! (.-withCredentials xhr) true))
      (if canned-response-params
        (if-let [delay-ms (:delay-ms canned-response-params)]
          (js/setTimeout call-on-done delay-ms)
          (call-on-done))
        (do
          (.addEventListener xhr "loadend" call-on-done)
          (.open xhr method url)
          (doseq [[k v] headers]
            (.setRequestHeader xhr k v))
          (if data
            (.send xhr data)
            (.send xhr)))))))



;; add with-credentials to the given map
(defn ajax-wc [arg-map]
  (ajax (assoc arg-map :with-credentials? true)))


;; flag to set usage of local orchestration server
(def use-local-orchestration-server? false)



;; returns URL for orchestration server depending on return from  use-local-orchestration-server?)
(def orchestration-url-root (if (and goog.DEBUG use-local-orchestration-server?)
                              "http://local.broadinstitute.org:8080"
                              "https://firecloud-ci.broadinstitute.org/api"))


;; setup a URL for an ajax call
(defn ajax-orch [path arg-map]
  (assert (= (subs path 0 1) "/") (str "Path must start with '/': " path))
  ;; The ajax-wc function adds a with-credentials? key/value pair
  (ajax-wc (assoc arg-map :url (str orchestration-url-root path))))



;; wrapper/utility for converting JavaScript values to JSON strings
(defn ->json-string [x]
  (js/JSON.stringify (clj->js x)))




;; wrapper/utility for converting JSON strings to JavaScript values
(defn parse-json-string [x]
  (js->clj (js/JSON.parse x)))

