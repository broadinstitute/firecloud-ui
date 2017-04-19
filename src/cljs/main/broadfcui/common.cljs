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
  (let [{:keys [lastSuccessDate lastFailureDate runningSubmissionsCount]}
        (:workspaceSubmissionStats workspace)]
    (cond (pos? runningSubmissionsCount) "Running"
          (and lastFailureDate
               (or (not lastSuccessDate)
                   (> (.parse js/Date lastFailureDate) (.parse js/Date lastSuccessDate)))) "Exception"
          :else "Complete")))

(defn gcs-object->download-url [bucket object]
  (str (config/api-url-root) "/cookie-authed/download/b/" bucket "/o/" object))

(defn gcs-object->google-url [bucket object]
  (str "https://www.googleapis.com/storage/v1/b/" bucket "/o/" (js/encodeURIComponent object) "?alt=media"))

(defn parse-gcs-uri [gcs-uri]
  (when (string? gcs-uri)
    (let [matcher (re-find #"^gs://([^/]+)/(.+)" gcs-uri)]
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

(def short-date-format
  {:month "short" :day "numeric" :year "numeric" :hour "numeric" :minute "numeric"})

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
    (let [[ns n] (clojure.string/split segment #":" 2)]
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
  (let [entity-type (:entityType entity)]
    (cond (= entity-type root-entity-type) 1
          ;; example: entity is 'sample_set', RET is 'sample', presumably using expression 'this.samples'
          (= entity-type (singular-type->set-type root-entity-type))
          (count (get-in entity [:attributes (keyword (set-type->membership-attribute entity-type)) :items]))
          ;; something nonsensical has been selected, submission will probably fail anyway:
          :else 1)))

(def PHI-warning
  [:div {:style {:display "flex" :marginBottom ".5rem" :alignItems "center" :justifyContent "space-around"
                 :padding "1rem" :backgroundColor (:background-light style/colors)}}
    (icons/icon {:style {:fontSize 22 :color (:exception-state style/colors) :marginRight "1rem"}}
                :alert)
    [:span {:style {:fontWeight 500}}
      "FireCloud is not intended to host personally identifiable information. Do not use any patient
       identifier, including name, social security number, or medical record number."]])

(react/defc FoundationTooltip
  {:component-did-mount
   (fn [{:keys [this]}]
     (.foundation (js/$ (react/find-dom-node this))))
   :render
   (fn [{:keys [props]}]
     (let [{:keys [position text tooltip style]} props]
       ;; empty string makes react attach a property with no value
       [:span {:data-tooltip "" :className (str "has-tip " position) :style style
               :title tooltip}
        text]))})

(defn question-icon-link [text link & [style]]
  [:a {:href link
       :target "_blank"
       :style (merge style/secondary-icon-style {:marginRight "0.4rem"} style)}
   [FoundationTooltip
    {:text (icons/icon {} :help)
     :style {:border "none" :cursor "pointer"}
     :tooltip text}]])

(react/defc FoundationDropdown
  {:close
   (fn [{:keys [locals]}]
     (.foundation (js/$ (:dropdown-element @locals)) "close"))
   :get-default-props
   (fn []
     {:icon-name :information})
   :component-will-mount
   (fn [{:keys [locals]}]
     (swap! locals assoc :dropdown-id (gensym "dropdown-")))
   :render
   (fn [{:keys [props locals]}]
     [:button {:className (str "button-reset " (:button-class props))
               :data-toggle (:dropdown-id @locals)
               :style (merge {:cursor "pointer" :padding "0 0.5rem"
                              :fontSize 16 :lineHeight "1rem"}
                             (:button-style props))}
      (:button-contents props)])
   :component-did-mount
   (fn [{:keys [this locals]}]
     (let [dropdown-container (.createElement js/document "div")]
       (swap! locals assoc :dropdown-container dropdown-container)
       (.insertBefore js/document.body dropdown-container (utils/get-app-root-element))
       (this :-render-dropdown)
       (.foundation (js/$ (:dropdown-element @locals)))))
   :component-will-receive-props
   (fn [{:keys [this]}]
     (this :-render-dropdown))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (react/unmount-component-at-node (:dropdown-container @locals))
     (.remove (:dropdown-container @locals)))
   :-render-dropdown
   (fn [{:keys [this props state locals]}]
     (let [{:keys [contents dropdown-class]} props
           {:keys [dropdown-container dropdown-id]} @locals]
       (react/render
        (react/create-element
         ;; empty string makes react attach a property with no value
         [:div {:className (str "dropdown-pane " dropdown-class) :id dropdown-id :data-dropdown ""
                :data-close-on-click (when (:close-on-click props) "true")
                :ref (this :-create-dropdown-ref-handler)
                :style (merge
                        {:whiteSpace "normal"}
                        (:style props)
                        (when (= (get-in props [:style :width]) :auto)
                          {:width (.-clientWidth (react/find-dom-node this))
                           :boxSizing "border-box" :minWidth 120}))}
          (when (:render-contents? @state)
            contents)])
        dropdown-container)))
   :-create-dropdown-ref-handler
   (fn [{:keys [this props state after-update locals]}]
     (utils/create-element-ref-handler
      {:store locals
       :key :dropdown-element
       :did-mount
       (fn [element]
         (let [element$ (js/$ element)
               button$ (js/$ (react/find-dom-node this))]
           (.on element$ "hide.zf.dropdown"
                (fn [_]
                  (swap! state dissoc :render-contents?)
                  (after-update #(this :-render-dropdown))))
           (.on button$
                "click"
                (fn [_]
                  (swap! state assoc :render-contents? true)
                  (after-update #(this :-render-dropdown))
                  (if (:close-on-click props)
                    (.on element$ "click.zf.dropdown"
                         (fn [_]
                           (.foundation element$ "close")))
                    (.on (js/$ "body")
                         "click.zf.dropdown"
                         (fn [e]
                           (when-not (or (.is button$ (.-target e))
                                         (pos? (.-length (.find button$ (.-target e))))
                                         (.is element$ (.-target e))
                                         (pos? (.-length (.find element$ (.-target e)))))
                             (.foundation element$ "close")
                             (.off (js/$ "body") "click.zf.dropdown")))))))))
       :will-unmount
       (fn [element]
         (.off (js/$ (react/find-dom-node this)) "click")
         (.off (js/$ element) "hide.zf.dropdown"))}))})

(defn render-icon-dropdown [{:keys [position icon-name icon-color] :as props}]
  [FoundationDropdown
   (merge {:dropdown-class position
           :button-contents (icons/icon {:style {:color icon-color}} icon-name)}
          props)])

(defn render-info-box [{:keys [text] :as props}]
  (render-icon-dropdown
   (merge {:contents text
           :icon-name :information :icon-color (:link-active style/colors)}
          props)))

(defn render-dropdown-menu [{:keys [label items width button-style]}]
  [FoundationDropdown
   {:button-contents label
    :button-class "float-right"
    :button-style (merge {:fontSize "unset" :lineHeight "unset" :padding 0 :textAlign "center"}
                         button-style)
    :close-on-click true
    :dropdown-class "bottom"
    :style {:boxShadow "0 3px 6px 0 rgba(0, 0, 0, 0.15)"
            :backgroundColor "#fff"
            :padding 0 :width width
            :border style/standard-line}
    :contents (let [DropdownItem
                    (react/create-class
                     {:render
                      (fn [{:keys [props state]}]
                        [:a {:style {:display "block"
                                     :color "#000" :textDecoration "none" :fontSize 14
                                     :padding "0.5rem 1.3rem 0.5rem 0.5rem"
                                     :backgroundColor (when (:hovering? @state) "#e8f5ff")}
                             :href (:href props)
                             :target (:target props)
                             :onMouseOver #(swap! state assoc :hovering? true)
                             :onMouseOut #(swap! state assoc :hovering? false)
                             :onClick (:dismiss props)}
                         (:text props)])})]
                [:div {}
                 (map (fn [item]
                        [DropdownItem (merge {:href "javascript:;" :target "_self"}
                                             item)])
                      items)])}])

(defn render-foundation-switch [{:keys [checked? on-change size]}]
  (let [id (gensym "switch-")]
    [:div {:className (str "switch " (or size "tiny")) :style {:marginBottom 0}}
     [:input {:className "switch-input" :type "checkbox"
              :id id
              :checked checked?
              :onChange #(on-change (aget % "target" "checked"))}]
     [:label {:className "switch-paddle"
              :htmlFor id}]]))
