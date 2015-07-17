(ns org.broadinstitute.firecloud-ui.utils
  (:require
    [clojure.string]
    ))

(def getType
  (fn [thing]
    (cond
      (coll? thing) "coll"
      (list? thing) "list"
      (vector? thing) "vector"
      (set? thing) "set"
      (map? thing) "map"
      (seq? thing) "seq"
      )
    )
  )



(defn rlog [& args]
  (let [arr (array)]
    (doseq [x args] (.push arr x))
    (js/console.log.apply js/console arr))
  (last args))


(defn jslog [& args]
  (apply rlog (map clj->js args)))


(defn cljslog [& args]
  (apply rlog (map pr-str args)))


(defn call-external-object-method [obj method-name & args]
  "Call an external object's method by name, since a normal call will get renamed during
   advanced compilation and cause an error."
  (apply (.bind (aget obj (name method-name)) obj) args))


(def use-live-data? false)


(defn ajax [arg-map]
  (let
    ;;setter/getter statements ; setting local values from what was passed in
       [url (:url arg-map)
        on-done (:on-done arg-map)
        method (if-let [method (:method arg-map)] (clojure.string/upper-case (name method)) "GET")
        headers (:headers arg-map)
        data (:data arg-map)
        with-credentials? (:with-credentials? arg-map)
        canned-response-params (when (and goog.DEBUG (not use-live-data?))
                                 (:canned-response arg-map))]
    ;;critical assertions for url and on-done method
    ;;a URL is absolutely necessary ; an on-done method is particularly necessary for AJAX calls
    (assert url (str "Missing url parameter: " arg-map))
    (assert on-done (str "Missing on-done callback: " arg-map))
    ;;with setting/getting done, prepare for AJAX/ACAX



    (let [xhr (if-not canned-response-params
                ;; if there isn't a canned response, then make an AJAX/XMLHttpRequest for subsequent usage below
                (js/XMLHttpRequest.)
                ;; if there IS a canned response, then
                ;;return the canned response as a javascript object map, but remove the delay
                (let [xhr (js-obj)]
                  (doseq [  [k v] (dissoc canned-response-params :delay-ms)]
                    (aset xhr (name k) v))
                  xhr))
          ;; set up a call-back function to be called when done
          call-on-done (fn []
                         ;; the call-back function is passed in via the arg-map
                         ((:on-done arg-map) {:xhr xhr
                                              :status-code (.-status xhr)
                                              :success? (and (>= (.-status xhr) 200)
                                                             (< (.-status xhr) 300))}))]
      ;; apply the credentials to the request before AJAX/ACAX
      (when with-credentials?
        (set! (.-withCredentials xhr) true))
      (if canned-response-params
        ;; if a canned-response-params(which is conditionally set in the let above via goog.DEBUG and not using live data) via a passed in key-value in the arg-map
        ;; then call the call-on-done using a timeout ; otherwise, simply use the call-on-done
        (if-let [delay-ms (:delay-ms canned-response-params)]
          (js/setTimeout call-on-done delay-ms)
          (call-on-done))
        ;;  perform AJAX (ACAX??? for 'Asynchronous Clojurescript And Xml')
        (do
          (.addEventListener xhr "loadend" call-on-done)
          (.open xhr method url)
          (doseq [[k v] headers]
            (.setRequestHeader xhr k v))
          (if data
            (.send xhr data)
            (.send xhr)))))))


(defn ajax-orch [path arg-map]
  ;; verify that that path starts with "/"
  (assert (= (subs path 0 1) "/") (str "Path must start with '/': " path))
  ;; add to the provided arg-map the URL then invoke AJAX with the
  ;; augmented argmap and the path prefixed with "/api"
  (ajax (assoc arg-map :url (str "/api" path))))


(defn ->json-string [x]
  (js/JSON.stringify (clj->js x)))


(defn parse-json-string [x]
  (js->clj (js/JSON.parse x)))


(defn deep-merge [& maps]
  (doseq [x maps] (assert (or (nil? x) (map? x)) (str "not a map: " x)))
  (apply
    merge-with
    (fn [x1 x2] (if (and (map? x1) (map? x2)) (deep-merge x1 x2) x2))
    maps))

