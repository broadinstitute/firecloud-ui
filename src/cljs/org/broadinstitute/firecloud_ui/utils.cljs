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


(defn- create-canned-response [params-map]
  (let [xhr (js-obj)]
    (doseq [[k v] params-map]
      (aset xhr (name k) v))
    xhr))


(defn ajax [arg-map]
  (assert (:url arg-map) (str "Missing url parameter: " arg-map))
  (assert (:on-done arg-map) (str "Missing on-done parameter: " arg-map))
  (let [call-on-done (fn [xhr]
                       ((:on-done arg-map) {:xhr xhr}))
        canned-response-params (:canned-response arg-map)
        delay-ms (:delay-ms canned-response-params)
        canned-response-params (dissoc canned-response-params :delay-ms)
        xhr (if canned-response-params
              (create-canned-response canned-response-params)
              (js/XMLHttpRequest.))
        method (or (:method arg-map) "GET")]
    (if canned-response-params
      (if delay-ms
        (js/setTimeout #(call-on-done xhr) delay-ms)
        (call-on-done xhr))
      (do
        (.addEventListener xhr "loadend" #(call-on-done xhr))
        (.open xhr method (:url arg-map))
        (if-let [data (:data arg-map)]
          (.send xhr data)
          (.send xhr))))))
