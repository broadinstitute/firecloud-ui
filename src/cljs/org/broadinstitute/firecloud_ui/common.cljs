(ns org.broadinstitute.firecloud-ui.common
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))


(def keymap
  {:backspace 8 :tab 9 :enter 13 :shift 16 :ctrl 17 :alt 18 :capslock 20 :esc 27 :space 32
   :pgup 33 :pgdown 34 :end 35 :home 67 :left 37 :up 38 :right 39 :down 40 :insert 45 :del 46})

(defn create-key-handler
  ([keys func] (create-key-handler keys (constantly true) func))
  ([keys modifier func] (fn [e]
                          (when (modifier e)
                            (let [keycode (.-keyCode e)]
                              (when (some #(= keycode (% keymap)) keys)
                                (func e)))))))

(defn get-text [refs & ids]
  (if (= 1 (count ids))
    (-> (react/find-dom-node (@refs (first ids))) .-value clojure.string/trim)
    (map
      (fn [id]
        (-> (react/find-dom-node (@refs id)) .-value clojure.string/trim))
      ids)))

(defn clear-both [] [:div {:style {:clear "both"}}])

;; Smooth step from https://en.wikipedia.org/wiki/Smoothstep
(defn- smooth-step [start end point]
  (let [x (/ (- point start) (- end start))]
    (* x x (- 3 (* 2 x)))))

(defn- smoother-step [start end point]
  (let [x (/ (- point start) (- end start))]
    (* x x x (+ (* x (- (* x 6) 15)) 10))))

(defn- animate [start-time end-time start-x start-y end-x end-y]
  (let [now (js/Date.now)]
    (if (> now end-time)
      (.scrollTo js/window end-x end-y)
      (let [point (smooth-step start-time end-time (js/Date.now))]
        (.scrollTo js/window
          (+ start-x (* point (- end-x start-x)))
          (+ start-y (* point (- end-y start-y))))
        (js/setTimeout #(animate start-time end-time start-x start-y end-x end-y) 10)))))

(defn scroll-to
  ([x y] (.scrollTo js/window x y))
  ([x y duration]
   (assert (<= duration 400) "Duration too long (> 400ms)")
   (if (zero? duration)
     (scroll-to x y)
     (let [start-time (js/Date.now)]
       (animate start-time (+ start-time duration) (.-scrollX js/window) (.-scrollY js/window) x y)))))

(defn scroll-to-top
  ([] (scroll-to-top 0))
  ([duration] (scroll-to 0 0 duration)))

(defn scroll-to-center
  ([elem] (scroll-to-center elem 0))
  ([elem duration]
   (let [elem-center-x (+ (.-offsetLeft elem) (/ (.-offsetWidth elem) 2))
         elem-center-y (+ (.-offsetTop elem) (/ (.-offsetHeight elem) 2))]
     (scroll-to
       (- elem-center-x (/ (.-innerWidth js/window) 2))
       (- elem-center-y (/ (.-innerHeight js/window) 2))
       duration))))


(def ^:private user-select-keys ["userSelect" "webkitTouchCallout" "webkitUserSelect"
                                 "mozUserSelect" "khtmlUserSelect" "msUserSelect"])

(defn disable-text-selection []
  (let [state (into {} (map (juxt identity #(aget (-> js/document .-body .-style) %)) user-select-keys))]
    (doseq [k user-select-keys]
      (aset (-> js/document .-body .-style) k "none"))
    state))

(defn restore-text-selection [state]
  (doseq [k user-select-keys]
    (aset (-> js/document .-body .-style) k (state k))))

(defn focus-and-select [dom-node]
  (.focus dom-node)
  (when (= "text" (.-type dom-node))
    (.select dom-node)))


(defn compute-status [workspace]
  (let [last-success (get-in workspace ["workspaceSubmissionStats" "lastSuccessDate"])
        last-failure (get-in workspace ["workspaceSubmissionStats" "lastFailureDate"])
        count-running (get-in workspace ["workspaceSubmissionStats" "runningSubmissionsCount"])]
    (cond (pos? count-running) "Running"
          (and last-failure
               (or (not last-success)
                   (.isAfter (js/moment last-failure) (js/moment last-success)))) "Exception"
          :else "Complete")))

(defn gcs-object->download-url [bucket object]
  (str "https://console.cloud.google.com/m/cloudstorage/b/" bucket "/o/" object))

(defn gcs-uri->download-url [gcs-uri]
  (let [matcher (re-find #"gs://([^/]+)/(.+)" gcs-uri)]
    (when (= 3 (count matcher)) ;; first match will be the whole thing
      (gcs-object->download-url (matcher 1) (matcher 2)))))

(defn parse-gcs-uri [gcs-uri]
  (let [matcher (re-find #"gs://([^/]+)/(.+)" gcs-uri)]
    (when (= 3 (count matcher)) ;; first match will be the whole thing
      {:bucket-name (matcher 1)
       :object (matcher 2)})))

(defn format-date [date & [format]]
  (-> date js/moment (.format (or format "LLL"))))

(defn format-filesize [bytes]
  (letfn [(loop [b n]
            (if (< b 1000)
              (str (.toFixed (js/parseFloat b) 2) " " (nth ["B" "KB" "MB" "GB" "TB" "PB" "EB" "ZB" "YB"] n))
              (loop (/ b 1000) (inc n))))]
    (loop bytes 0)))

(defn parse-profile [unparsed-profile]
  (let [unparsed-values (get unparsed-profile "keyValuePairs")]
    (into {} (map (fn [m] [(keyword (m "key")) (m "value")]) unparsed-values))))

(defn get-id-from-nav-segment [segment]
  (when-not (clojure.string/blank? segment)
    (let [[ns n] (clojure.string/split segment #":")]
      {:namespace ns :name n})))
