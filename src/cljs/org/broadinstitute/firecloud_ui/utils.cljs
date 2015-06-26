(ns org.broadinstitute.firecloud-ui.utils)


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


(defn ajax [arg-map]
  (let [url (:url arg-map)
        on-done (:on-done arg-map)
        method (or (:method arg-map) "GET")
        data (:data arg-map)
        with-credentials? (:with-credentials? arg-map)
        canned-response-params (:canned-response arg-map)]
    (assert url (str "Missing url parameter: " arg-map))
    (assert on-done (str "Missing on-done callback: " arg-map))
    (let [xhr (if-not canned-response-params
                (js/XMLHttpRequest.)
                (let [xhr (js-obj)]
                  (doseq [[k v] (dissoc canned-response-params :delay-ms)]
                    (aset xhr (name k) v))
                  xhr))
          call-on-done (fn []
                         ((:on-done arg-map) {:xhr xhr
                                              :status-code (.-status xhr)
                                              :success? (and (>= (.-status xhr) 200) (< (.-status xhr) 300))}))]
      (when with-credentials?
        (set! (.-withCredentials xhr) true))
      (if canned-response-params
        (if-let [delay-ms (:delay-ms canned-response-params)]
          (js/setTimeout call-on-done delay-ms)
          (call-on-done))
        (do
          (.addEventListener xhr "loadend" call-on-done)
          (.open xhr method url)
          (if data
            (.send xhr data)
            (.send xhr)))))))


(defn ajax-wc [arg-map]
  (ajax (assoc arg-map :with-credentials? true)))


(def use-local-orchestration-server? false)
(def orchestration-url-root (if (and goog.DEBUG use-local-orchestration-server?)
                              "http://local.broadinstitute.org:8080"
                              ;; TODO(dmohs): Add real URL when it goes live.
                              "TODO"))


(defn ajax-orch [path arg-map]
  (assert (= (subs path 0 1) "/") (str "Path must start with '/': " path))
  (ajax-wc (assoc arg-map :url (str orchestration-url-root path))))

