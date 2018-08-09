(ns broadfcui.utils.ajax
  (:require
   [clojure.string :as string]
   [broadfcui.config :as config]
   [broadfcui.utils :as utils]
   ))


(defonce get-bearer-token-header (atom nil))


;; TODO - make this unnecessary
(def content-type=json {"Content-Type" "application/json"})

(defonce ^:private recent-ajax-calls (atom #{}))
(def ^:private double-call-threshold 2000)

(defn call [{:keys [url on-done method headers data with-credentials?] :as arg-map}]
  (let [method (if method (string/upper-case (name method)) "GET")]
    (assert url (str "Missing url parameter: " arg-map))
    (assert on-done (str "Missing on-done callback: " arg-map))
    (when (config/debug?)
      (let [request (utils/restructure method url data)]
        (when (contains? @recent-ajax-calls request)
          (js/console.warn (str "WARNING: repeated ajax calls to " method " " url
                                (when data (str " with payload " data)))))
        (swap! recent-ajax-calls conj request)
        (js/setTimeout #(swap! recent-ajax-calls disj request) double-call-threshold)))

    (let [xhr (js/XMLHttpRequest.)
          call-on-done (fn []
                         (on-done (let [status-code (.-status xhr)]
                                    {:xhr xhr
                                     :status-code status-code
                                     :success? (<= 200 status-code 299)
                                     :status-text (.-statusText xhr)
                                     :raw-response (.-responseText xhr)
                                     :get-parsed-response
                                     (fn [& [keywordize-keys? throw-on-error?]]
                                       (utils/parse-json-string
                                        (.-responseText xhr)
                                        (if (some? keywordize-keys?) keywordize-keys? true)
                                        (if (some? throw-on-error?) throw-on-error? true)))})))]
      (when with-credentials?
        (set! (.-withCredentials xhr) true))
      (.addEventListener xhr "loadend" call-on-done)
      (.open xhr method url)
      (doseq [[k v] headers]
        (.setRequestHeader xhr k v))
      (if data
        (.send xhr data)
        (.send xhr)))))


(defn get-google-bucket-file [filename on-done]
  (call
   {:url (config/google-bucket-url filename)
    :on-done (fn [{:keys [raw-response]}]
               ;; Fails gracefully if file is missing or malformed
               (some->> (utils/parse-json-string raw-response true false)
                        first
                        on-done))}))


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

(defn- update-health [status-code status-text]
  (let [maintenance? (check-maintenance-mode status-code status-text)
        down? (check-server-down status-code)]
    (cond
      maintenance? (reset! maintenance-mode? true)
      down? (reset! server-down? true)
      :else (do
              (reset! maintenance-mode? false)
              (reset! server-down? false)))))

(defn get-exponential-backoff-interval [attempt]
  (* (.pow js/Math 2 attempt) 1000))                        ;; backoff interval in millis

(defn- call-service [url-root path arg-map maybe-nil-service-prefix]
  (let [service-prefix (or maybe-nil-service-prefix "/api")]
    (assert (= (subs path 0 1) "/") (str "Path must start with '/': " path))
    (let [on-done (:on-done arg-map)]
      (call (assoc arg-map
              :url (str url-root service-prefix path)
              :headers (merge (@get-bearer-token-header)
                              (:headers arg-map))
              :on-done (fn [{:keys [status-code status-text] :as m}]
                         (update-health status-code status-text)
                         (on-done m)))))))

(defn call-orch [path arg-map & {:keys [service-prefix]}]
  (call-service (config/api-url-root) path arg-map service-prefix))

(defn call-leo [path arg-map & {:keys [service-prefix]}]
  (call-service (config/leonardo-url-root) path arg-map service-prefix))

(defn call-sam [path arg-map & {:keys [service-prefix]}]
  (call-service (config/sam-url-root) path arg-map service-prefix))

(defn call-martha [data arg-map]
  (let [on-done (:on-done arg-map)]
    (call (assoc arg-map
            :url (config/martha-url)
            :method "POST"
            :data data
            :on-done (fn [{:keys [status-code status-text] :as m}]
                       (on-done m))))))

(defn call-bond [path arg-map & {:keys [service-prefix] :or {service-prefix "/api"}}]
  (assert (= (subs path 0 1) "/") (str "Path must start with '/': " path))
  (let [on-done (:on-done arg-map)]
    (call (assoc arg-map
            :url (str (config/bond-url) service-prefix path)
            :headers (merge (@get-bearer-token-header)
                            (:headers arg-map))
            :on-done (fn [{:keys [status-code status-text] :as m}]
                       (update-health status-code status-text)
                       (on-done m))))))
