(ns broadfcui.common
  (:require
   [dmohs.react :as react]
   [broadfcui.utils :as utils]
   [broadfcui.config :as config]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
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
  (let [last-success (get-in workspace [:workspaceSubmissionStats :lastSuccessDate])
        last-failure (get-in workspace [:workspaceSubmissionStats :lastFailureDate])
        count-running (get-in workspace [:workspaceSubmissionStats :runningSubmissionsCount])]
    (cond (pos? count-running) "Running"
          (and last-failure
               (or (not last-success)
                   (> (.parse js/Date last-failure) (.parse js/Date last-success)))) "Exception"
          :else "Complete")))

(defn gcs-object->download-url [bucket object]
  (str (config/api-url-root) "/cookie-authed/download/b/" bucket "/o/" object))

(defn gcs-object->google-url [bucket object]
  (str "https://www.googleapis.com/storage/v1/b/" bucket "/o/" (js/encodeURIComponent object) "?alt=media"))

(defn parse-gcs-uri [gcs-uri]
  (when (string? gcs-uri)
    (let [matcher (re-find #"gs://([^/]+)/(.+)" gcs-uri)]
      (when (= 3 (count matcher)) ;; first match will be the whole thing
        {:bucket-name (matcher 1)
         :object (matcher 2)}))))

(defn gcs-uri->download-url [gcs-uri]
  (when-let [parsed (parse-gcs-uri gcs-uri)]
    (gcs-object->download-url (:bucket-name parsed) (:object parsed))))

(defn gcs-uri->google-url [gcs-uri]
  (when-let [parsed (parse-gcs-uri gcs-uri)]
    (gcs-object->google-url (:bucket-name parsed) (:object parsed))))

(def default-date-format
  {:month "long" :day "numeric" :year "numeric" :hour "numeric" :minute "numeric"})

(defn format-date [date & [format]]
  (-> date js/Date.
      (.toLocaleString (.-language js/navigator)
                       (clj->js (or format default-date-format)))))

(defn format-filesize [bytes]
  (letfn [(loop [b n]
            (if (< b 1000)
              (str (.toFixed (js/parseFloat b) 2) " " (nth ["B" "KB" "MB" "GB" "TB" "PB" "EB" "ZB" "YB"] n))
              (loop (/ b 1000) (inc n))))]
    (loop bytes 0)))

(defn format-price [amount]
  (if (< amount 0.01)
    "< $0.01"
    (str "$" (.toFixed (js/parseFloat amount) 2))))

(defn parse-profile [unparsed-profile]
  (let [unparsed-values (get unparsed-profile "keyValuePairs")]
    (into {} (map (fn [m] [(keyword (m "key")) (m "value")]) unparsed-values))))

(defn row->workspace-id [row]
  (select-keys row [:namespace :name]))

(defn workspace-id->string [workspace-id] 
  (str (:namespace workspace-id) "/" (:name workspace-id)))

(defn get-id-from-nav-segment [segment]
  (when-not (clojure.string/blank? segment)
    (let [[ns n] (clojure.string/split segment #":")]
      {:namespace ns :name n})))

;; GAWB-666 Globally show Queued and Cromwell (active) counts
(defn queue-status-counts [{:strs [workflowCountsByStatus estimatedQueueTimeMS workflowsBeforeNextUserWorkflow]}]
  {:queue-time (or estimatedQueueTimeMS 0)
   :queue-position (or workflowsBeforeNextUserWorkflow 0)
   :queued (apply + (map workflowCountsByStatus ["Queued" "Launching"]))
   :active (apply + (map workflowCountsByStatus ["Submitted" "Running" "Aborting"]))})

(defn attribute-list? [attr-value]
  (and (map? attr-value)
       (= (set (keys attr-value)) #{:itemsType :items})))

(defn attribute-values [attribute-list]
  (:items attribute-list))

(def root-entity-types ["participant" "sample" "pair" "participant_set" "sample_set" "pair_set"])
(def singular-type->set-type {"participant" "participant_set"
                              "sample" "sample_set"
                              "pair" "pair_set"})

(def set-type->membership-attribute
  {"participant_set" "participants"
   "sample_set" "samples"
   "pair_set" "pairs"})

; The list of all access levels in the system, inclusive of roles that aren't directly assignable
; Note that if you add an access level, you will want to add it in acl_editor.cljs as well
(def ^:private access-levels
  {"NO ACCESS" 0 "READER" 1 "WRITER" 2 "OWNER" 3 "PROJECT_OWNER" 4})

(defn access-greater-than? [level1 level2]
  (> (access-levels level1) (access-levels level2)))

(defn access-equal-to? [level1 level2]
  (= level1 level2))

(defn access-greater-than-equal-to? [level1 level2]
  (or (access-equal-to? level1 level2) (access-greater-than? level1 level2)))

(defn count-workflows [entity root-entity-type]
  (let [entity-type (entity "entityType")]
    (cond (= entity-type root-entity-type) 1
          ;; example: entity is 'sample_set', RET is 'sample', presumably using expression 'this.samples'
          (= entity-type (singular-type->set-type root-entity-type))
          (count (get-in entity ["attributes" (set-type->membership-attribute entity-type)]))
          ;; something nonsensical has been selected, submission will probably fail anyway:
          :else 1)))

(def PHI-warning
  [:div {:style {:display "flex" :marginBottom ".5em" :marginLeft ".3em" :alignItems "center"}}
    (icons/icon {:style {:fontSize 28 :color (:exception-state style/colors) :marginRight ".26em"}}
                :alert)
    [:span {:style {:fontWeight "bold" :fontSize "98%"}}
      "FireCloud is not intended to host personally identifiable information. Do not use any patient identifier, including name, social security number, or medical record number."]])
