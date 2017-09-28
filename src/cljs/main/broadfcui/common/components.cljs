(ns broadfcui.common.components
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.codemirror :refer [CodeMirror]]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.config :as config]
   [broadfcui.utils :as utils]
   ))


(declare push-error)
(declare create-error-message)

(react/defc Spinner
  {:render
   (fn [{:keys [props]}]
     [:span {:style (merge {:margin "1em" :whiteSpace "nowrap" :display "inline-block"} (:style props))
             :data-test-id "spinner"}
      (icons/icon {:className "fa-pulse fa-lg fa-fw" :style {:marginRight "0.5rem"}} :spinner)
      [:span {:data-test-id "spinner-text"} (:text props)]])})


(react/defc AnimatedEllipsis
  {:get-initial-state
   (fn []
     {:dot-count 0})
   :render
   (fn [{:keys [state]}]
     [:span {:style {:marginLeft 5}}
      (repeat (:dot-count @state) "•")])
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :-cycle this))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (js/clearTimeout (:-cycle @locals)))
   :-cycle
   (fn [{:keys [this state locals]}]
     (swap! state update :dot-count #(mod (inc %) 4))
     (swap! locals assoc :-cycle (js/setTimeout #(react/call :-cycle this) 600)))})


(react/defc Checkbox
  {:checked?
   (fn [{:keys [refs]}]
     (.-checked (@refs "check")))
   :get-default-props
   (fn []
     {:initial-checked? false
      :disabled? false})
   :render
   (fn [{:keys [props]}]
     (let [{:keys [disabled? data-test-id]} props]
       [:label {:style {:cursor (when-not disabled? "pointer")
                        :color (when disabled? (:text-light style/colors))}
                :title (when disabled? (:disabled-text props))
                :onClick (when disabled?
                           #(push-error
                             (or (:disabled-text props) "This option is not available.")))}
        [:input {:type "checkbox" :ref "check"
                 :defaultChecked (:initial-checked? props)
                 :disabled disabled? :data-test-id data-test-id
                 :style {:cursor (when-not disabled? "pointer")}}]
        [:span {:style {:marginLeft "0.5ex"}} (:label props)]]))})


;; TODO: find out if :position "absolute" would work everywhere, or possibly get rid of Blocker entirely
(defn render-blocker [text & [fixed?]]
  [:div {:style {:backgroundColor "rgba(210, 210, 210, 0.4)"
                 :position (if fixed? "fixed" "absolute") :top 0 :bottom 0 :right 0 :left 0 :zIndex 9999
                 :display "flex" :justifyContent "center" :alignItems "center"}}
   [:div {:style {:backgroundColor "#fff" :padding "2em"}}
    [Spinner {:text text}]]])

(react/defc Blocker
  {:render
   (fn [{:keys [props]}]
     (when-let [text (:banner props)]
       (render-blocker text)))})

(react/defc DelayedBlocker
  {:show
   (fn [{:keys [props state]}]
     (swap! state assoc :show-requested? true)
     (js/setTimeout #(when (:show-requested? @state)
                       (swap! state assoc :showing? true))
                    (:delay-time-ms props)))
   :hide
   (fn [{:keys [state]}]
     (swap! state dissoc :showing? :show-requested?))
   :get-default-props
   (fn []
     {:delay-time-ms 200})
   :render
   (fn [{:keys [props state]}]
     (when (:showing? @state)
       (render-blocker (:banner props))))})


(react/defc StatusLabel
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:display "flex" :background (:color props) :color "#fff"
                    :padding "15px 20px" :marginBottom "2em" :lineHeight "36px"
                    :alignItems "center" :justifyContent "center"}}
      (:icon props)
      [:span {:style {:marginLeft "1em" :fontSize "125%" :fontWeight 400
                      :verticalAlign "middle"}
              :data-test-id "submission-status"}
       (:text props)]])})


(react/defc EntityDetails
  {:get-fields
   (fn [{:keys [refs]}]
     {"methodVersion" (int (common/get-text refs "snapshotId"))})
   :clear-redacted-snapshot
   (fn [{:keys [state]}]
     (swap! state dissoc :redacted-snapshot))
   :get-initial-state
   (fn [{:keys [props]}]
     (when (:redacted? props) {:redacted-snapshot (get-in props [:entity :snapshotId])}))
   :render
   (fn [{:keys [props state this]}]
     [:div {} (when-let [wdl-parse-error (:wdl-parse-error props)] (style/create-server-error-message wdl-parse-error))
      (let [{:keys [entity redacted?]} props
            config? (contains? entity :method)]
        [:div {:style {:backgroundColor (:background-light style/colors)
                       :borderRadius 8 :border style/standard-line
                       :padding "1rem"}}
         (this :render-details entity)
         (when-not redacted?
           [:div {:style {:paddingTop "0.5rem"}}
            [:span {:style {:fontWeight 500 :marginRight "1rem"}} (if config? "Referenced Method:" "WDL:")]
            (links/create-internal {:onClick #(swap! state update :payload-expanded not)}
                                   (if (:payload-expanded @state) "Collapse" "Expand"))
            (when (:payload-expanded @state)
              (if config?
                [:div {:style {:margin "0.5rem 0 0 1rem"}}
                 (this :render-details (:method entity))
                 [:div {:style {:fontWeight 500 :marginTop "1rem"}} "WDL:"]
                 [CodeMirror {:text (get-in entity [:method :payload])}]]
                [CodeMirror {:text (:payload entity)}]))])])])
   :render-details
   (fn [{:keys [props refs state]} entity]
     (let [{:keys [editing? redacted?]} props
           {:keys [redacted-snapshot]} @state
           make-field
           (fn [key label & {:keys [dropdown? wrap? render]}]
             [:div {:style {:display "flex" :alignItems "baseline" :paddingBottom "0.25rem"}}
              [:div {:style {:flex "0 0 100px" :fontWeight 500} } (str label ":")]
              [:div {:style {:flex "1 1 auto" :overflow "hidden" :textOverflow "ellipsis"
                             :whiteSpace (when-not wrap? "nowrap")}}
               (if (and editing? dropdown?)
                 (style/create-identity-select-name {:ref key
                                                     :data-test-id "edit-method-config-snapshot-id-select"
                                                     :style {:width 120}
                                                     :defaultValue (if redacted-snapshot -1 (key entity))
                                                     :onChange (when-let [f (:onSnapshotIdChange props)]
                                                                 #(f (int (common/get-text refs "snapshotId"))))}
                                                    (:snapshots props)
                                                    redacted-snapshot)
                 (let [rendered ((or render identity) (key entity))]
                   [:span {:title rendered :data-test-id (str "method-label-" label)} rendered]))]])]
       [:div {}
        [:div {:style {:display "flex"}}
         [:div {:style {:flex "1 1 50%" :paddingRight "0.5rem"}}
          (when redacted?
            [:div {:style {:fontWeight 500 :paddingBottom "0.25rem"} :data-test-id "snapshot-redacted-title"}
             (icons/icon {:style {:color (:warning-state style/colors)}} :warning) " Snapshot Redacted"])
          (make-field :namespace "Namespace")
          (make-field :name "Name")
          (make-field :snapshotId "Snapshot ID" :dropdown? true)
          (make-field :entityType "Entity Type")]
         (when-not redacted?
           [:div {:style {:flex "1 1 50%" :overflow "hidden"}}
            (make-field :createDate "Created" :render common/format-date)
            (make-field :managers "Owners" :render (partial clojure.string/join ", ") :wrap? true)
            (make-field :synopsis "Synopsis")])]
        (when-not redacted?
          [:div {:style {:fontWeight 500 :padding "0.5rem 0 0.3rem 0"}}
           "Documentation:"
           (if (string/blank? (:documentation entity))
             [:div {:style {:fontStyle "italic" :fontSize "90%"}} "No documentation provided"]
             [:div {:style {:fontSize "90%"}} (:documentation entity)])])]))})


(react/defc StackTraceViewer
  {:render
   (fn [{:keys [props state]}]
     (if (:expanded? @state)
       [:div {:style {:overflowX "auto"}}
        [:div {} "Stack Trace:"]
        (map
         (fn [line]
           (let [[class method file num]
                 (map line ["className" "methodName" "fileName" "lineNumber"])]
             [:div {:style {:marginLeft "1em" :whiteSpace "nowrap"}}
              (str "at " class "." method " (" file ":" num ")")]))
         (:lines props))
        (links/create-internal {:onClick #(swap! state assoc :expanded? false)} "Hide Stack Trace")]
       [:div {} (links/create-internal {:onClick #(swap! state assoc :expanded? true)} "Show Stack Trace")]))})


(declare CauseViewer)


(react/defc CauseViewer
  {:render
   (fn [{:keys [props state]}]
     (if (:expanded? @state)
       (let [[source causes stack-trace message]
             (map props ["source" "causes" "stackTrace" "message"])]
         [:div {:style {:marginLeft "1em"}}
          [:div {} "Message: " message]
          (when source [:div {} "Source: " source])
          (when (seq causes)
            [:div {}
             [:div {} (str "Cause" (when (> (count causes) 1) "s") ":")]
             (map (fn [cause] [CauseViewer cause]) causes)])
          (when (seq stack-trace)
            [StackTraceViewer {:lines stack-trace}])
          (links/create-internal {:onClick #(swap! state assoc :expanded? false)} "Hide Cause")])
       [:div {} (links/create-internal {:onClick #(swap! state assoc :expanded? true)} "Show Cause")]))})

(react/defc ErrorViewer
  {:render
   (fn [{:keys [props state]}]
     (when-let [error (:error props)]
       (let [[source timestamp status-code code causes stack-trace message]
             (map error ["source" "timestamp" "statusCode" "code" "causes" "stackTrace" "message"])
             ;; method redact is responding with "code" for 401.  TODO: standardize and remove this extra logic
             status-code (or status-code code)]
         (if-let [expected-msg (get-in props [:expect status-code])]
           (style/create-flexbox {}
                                 [:span {:style {:paddingRight "0.5rem"}}
                                  (icons/icon {:style {:color (:exception-state style/colors)}}
                                              :warning)]
                                 (str "Error: " expected-msg))
           [:div {:style {:textAlign "initial"}}
            (style/create-flexbox {:style {:marginBottom "0.25em"}}
                                  [:span {:style {:paddingRight "0.5rem"}}
                                   (icons/icon {:style {:color (:exception-state style/colors)}}
                                               :warning)]
                                  (str "Error: " message))
            (if (:expanded? @state)
              [:div {}
               (links/create-internal {:onClick #(swap! state assoc :expanded? false)}
                                      (icons/icon {:className "fa-fw"} :disclosure-opened)
                                      "Hide Details")
               [:div {:style {:overflowX "auto" :paddingLeft icons/fw-icon-width}}
                [:div {} (str "Code: " status-code)]
                (when timestamp [:div {} "Occurred: "
                                 (common/format-date timestamp
                                                     (assoc common/default-date-format
                                                       :timeZoneName "short"))])
                (when source [:div {} "Source: " source])
                (when (seq causes)
                  (let [num-shown 4
                        num-hidden (- (count causes) num-shown)]
                    [:div {}
                     [:div {} (str "Cause" (when (> (count causes) 1) "s") ":")]
                     (map (fn [cause] [CauseViewer cause]) (take num-shown causes))
                     (when (pos? num-hidden)
                       [:div {} (str num-hidden " not shown")])]))
                (when (seq stack-trace)
                  [StackTraceViewer {:lines stack-trace}])]]
              [:div {}
               (links/create-internal {:onClick #(swap! state assoc :expanded? true)}
                                      (icons/icon {:className "fa-fw"} :disclosure-closed)
                                      "Show Details")])]))))})


(react/defc Breadcrumbs
  {:render
   (fn [{:keys [props]}]
     (let [sep (icons/icon {:style {:color (:text-lightest style/colors) :padding "0 0.5rem"}} :angle-right)
           crumbs (filter some? (:crumbs props))]
       (case (count crumbs)
         0 [:div {}]
         1 [:div {:style {:fontWeight 500 :fontSize "1.2em"}} (:text (first crumbs))]
         [:div {:style {:display "flex" :alignItems "baseline" :flexWrap "wrap"}}
          (interpose
           sep
           (map
            (fn [{:keys [text onClick href] :as link-props}]
              [:span {:style {:whiteSpace "pre"}}
               (if (or onClick href)
                 (links/create-internal (dissoc link-props :text) text)
                 text)])
            (butlast crumbs)))
          sep
          [:span {:style {:fontWeight "bold" :fontSize "1.2em"}} (:text (last crumbs))]])))})


(react/defc TextFilter
  {:set-text
   (fn [{:keys [refs]} text]
     (set! (.-value (@refs "filter-field")) text))
   :render
   (fn [{:keys [props this]}]
     (let [{:keys [initial-text placeholder width data-test-id]} props]
       [:div {:style {:display "inline-flex" :width width}}
        (style/create-search-field
         {:ref "filter-field" :autoSave "true" :results 5 :auto-focus "true"
          :data-test-id (str data-test-id "-input")
          :placeholder (or placeholder "Filter") :defaultValue initial-text
          :style {:flex "1 0 auto" :borderRadius "3px 0 0 3px" :marginBottom 0}
          :onKeyDown (common/create-key-handler [:enter] #(react/call :apply-filter this))})
        [buttons/Button {:icon :search :onClick #(react/call :apply-filter this)
                 :data-test-id (str data-test-id "-button")
                 :style {:flex "0 0 auto" :borderRadius "0 3px 3px 0"}}]]))
   :apply-filter
   (fn [{:keys [props refs]}]
     ((:on-filter props) (common/get-text refs "filter-field")))
   :component-did-mount
   (fn [{:keys [refs this]}]
     (.addEventListener (@refs "filter-field") "search"
                        #(when (empty? (.. % -currentTarget -value))
                           (react/call :apply-filter this))))})

(def Bloodhound (aget js/window "webpack-deps" "Bloodhound"))
(def ^:private whitespace-tokenizer (aget Bloodhound "tokenizers" "whitespace"))

(defn create-bloodhound-engine [{:keys [remote local datum-tokenizer query-tokenizer]}]
  (Bloodhound. (clj->js {:datumTokenizer (or datum-tokenizer whitespace-tokenizer)
                         :queryTokenizer (or query-tokenizer whitespace-tokenizer)
                         :remote remote
                         :local local})))

(react/defc Typeahead
  {:get-text
   (fn [{:keys [refs]}]
     (common/get-text refs "field"))
   :access-text-field
   (fn [{:keys [refs]}]
     (@refs "field"))
   :get-default-props
   (fn []
     {:empty-message "No results to display."
      :behavior {:highlight true
                 :hint true
                 :minLength 3}
      :typeahead-events ["typeahead:select" "typeahead:change"]})
   :render
   (fn [{:keys [props]}]
     (let [{:keys [disabled field-attributes]} props]
       (style/create-search-field (merge {:ref "field" :className "typeahead" :disabled disabled}
                                         field-attributes))))
   :component-did-mount
   (fn [{:keys [props refs]}]
     (when (not (:disabled props))
       (let [{:keys [engine render-display behavior empty-message render-suggestion on-select typeahead-events]} props]
         (.typeahead (js/$ (@refs "field"))
                     (clj->js behavior)
                     (clj->js
                      {:source (or engine (create-bloodhound-engine (select-keys props [:remote :local :datum-tokenizer :query-tokenizer])))
                       :display render-display
                       :templates {:empty (str "<div style='padding: 0.5em'>" empty-message "</div>")
                                   :suggestion render-suggestion}}))
         (doseq [item typeahead-events]
           (.on (js/$ (@refs "field")) item on-select)))
       (.addEventListener (@refs "field") "search"
                          (fn []
                            (.typeahead (js/$ (@refs "field")) "close")
                            #(when (and (empty? (.. % -currentTarget -value)) (:on-clear props))
                               ((:on-clear props)))))))
   :component-will-unmount
   (fn [{:keys [refs]}]
     (.typeahead (js/$ (@refs "field")) "destroy"))})


(react/defc AutocompleteFilter
  {:apply-filter
   (fn [{:keys [props refs]}]
     ((:on-filter props) (common/get-text refs "typeahead")))
   :get-default-props
   (fn []
     {:typeaheadDisplay identity
      :typeahead-events ["typeahead:select" "typeahead:change"]})
   :render
   (fn [{:keys [props this]}]
     (let [{:keys [field-attributes width property]} props]
       [:div {:style {:display "inline-flex" :width width}}
        [Typeahead {:ref "typeahead"
                    :field-attributes (utils/deep-merge
                                       {:autoSave "true" :results 5 :auto-focus "true"
                                        :placeholder "Filter"
                                        :style {:flex "1 0 auto" :width width :borderRadius 3 :marginBottom 0}
                                        :onKeyDown (common/create-key-handler [:enter] #(react/call :apply-filter this))
                                        :data-test-id property}
                                       field-attributes)
                    :behavior {:hint false :minLength 3}
                    :remote (:bloodhoundInfo props)
                    :render-display (:typeaheadDisplay props)
                    :empty-message "<small>Unable to find any matches to the current query</small>"
                    :typeahead-events (:typeahead-events props)
                    :disabled (:disabled props)
                    :render-suggestion (:typeaheadSuggestionTemplate props)
                    :on-select (fn [_ suggestion]
                                 ((:on-filter props) ((:typeaheadDisplay props) suggestion)))}]]))
   :component-did-mount
   (fn [{:keys [refs this]}]
     (.addEventListener (react/call :access-text-field (@refs "typeahead")) "search"
                        #(when (empty? (.. % -currentTarget -value))
                           (react/call :apply-filter this))))})


(react/defc ScrollFader
  {:scroll-to
   (fn [{:keys [refs]} position]
     (set! (.-scrollTop (@refs "scroller")) position))
   :get-default-props
   (fn []
     {:vertical 50
      :blur 36
      :spread -45
      :alpha 0.25})
   :render
   (fn [{:keys [props this]}]
     [:div {:style (merge {:position "relative"} (:outer-style props))}
      [:div {:ref "scroller"
             :style (merge {:overflowY "auto"} (:inner-style props))
             :onScroll #(this :-update-edges)}
       [:div {:ref "content-container"}
        (:content props)]]
      (this :-build-shadow true)
      (this :-build-shadow false)])
   :component-did-mount
   (fn [{:keys [refs this]}]
     (this :-update-edges)
     (.addEventListener (@refs "content-container") "onresize"
                        #(this :-update-edges)))
   :-build-shadow
   (fn [{:keys [props state]} top?]
     (let [{:keys [vertical blur spread alpha]} props]
       [:div {:style {:position "absolute" :top 0 :left 0 :right 0 :bottom 0
                      :pointerEvents "none"
                      :WebkitBoxShadow (str "inset 0 "
                                            (if top? vertical (- vertical)) "px "
                                            blur "px "
                                            spread "px "
                                            "rgba(0,0,0," alpha ")")
                      :opacity (if ((if top? :more-above? :more-below?) @state) 1 0)
                      :transition "opacity 0.5s linear"}}]))
   :-update-edges
   (fn [{:keys [state refs]}]
     (let [scroll-top (.-scrollTop (@refs "scroller"))
           scroll-height (.-scrollHeight (@refs "scroller"))
           inner-height (.-offsetHeight (@refs "scroller"))]
       (swap! state assoc
              :more-above? (> scroll-top 0)
              :more-below? (< (+ scroll-top inner-height) scroll-height))))})

;; Deprecated. If you are touching code that uses this, please migrate to use
;; org.broadinstitute.uicomps.modal.OKCancelForm
(react/defc OKCancelForm
  {:get-default-props
   (fn []
     {:show-cancel? true
      :show-close? true})
   :render
   (fn [{:keys [props]}]
     (let [{:keys [header content ok-button show-cancel? cancel-text show-close? data-test-id]} props
           cancel-text (or cancel-text "Cancel")]
       [:div {}
        [:div {:style {:borderBottom style/standard-line
                       :padding "20px 48px 18px"
                       :fontSize "137%" :fontWeight 400 :lineHeight 1}
               :data-test-id data-test-id}
         header
         (when show-close? (buttons/x-button modal/pop-modal))]
        [:div {:style {:padding "22px 48px 40px" :backgroundColor (:background-light style/colors)}}
         content
         (when (or show-cancel? ok-button)
           [:div {:style {:marginTop (if ok-button 40 25) :textAlign "center"}}
            (when show-cancel?
              [:a {:className "cancel"
                   :style {:marginRight (when ok-button 27) :marginTop 2
                           :display "inline-block"
                           :fontSize "106%" :fontWeight 500 :textDecoration "none"
                           :color (:button-primary style/colors)}
                   :href "javascript:;"
                   :data-test-id "cancel-button"
                   :onClick modal/pop-modal
                   :onKeyDown (common/create-key-handler [:space :enter] modal/pop-modal)}
               cancel-text])
            (when ok-button
              (cond (string? ok-button) [buttons/Button {:text ok-button :ref "ok-button" :class-name "ok-button" :data-test-id "ok-button" :onClick modal/pop-modal}]
                    (fn? ok-button) [buttons/Button {:text "OK" :ref "ok-button" :class-name "ok-button" :data-test-id "ok-button" :onClick ok-button}]
                    (map? ok-button) [buttons/Button (merge {:text "OK" :ref "ok-button" :class-name "ok-button" :data-test-id "ok-button"} ok-button)]
                    :else ok-button))])]]))
   :component-did-mount
   (fn [{:keys [props refs]}]
     (when-let [get-first (:get-first-element-dom-node props)]
       (common/focus-and-select (get-first))
       (when-let [get-last (or (:get-last-element-dom-node props)
                               #(react/find-dom-node (@refs "ok-button")))]
         (.addEventListener
          (get-first) "keydown"
          (common/create-key-handler [:tab] #(.-shiftKey %)
                                     (fn [e] (.preventDefault e)
                                       (when (:cycle-focus? props)
                                         (.focus (get-last))))))
         (.addEventListener
          (get-last)
          "keydown"
          (common/create-key-handler [:tab] #(not (.-shiftKey %))
                                     (fn [e] (.preventDefault e)
                                       (when (:cycle-focus? props)
                                         (.focus (get-first)))))))))})

(defn no-billing-projects-message []
  [:div {:style {:textAlign "center"}}
   "You must have a billing project associated with your account to create a new workspace."
   (links/create-external {:href (config/billing-guide-url)
                           :style {:display "block"}}
                          "Learn how to create a billing project.")])

;; Deprecated. If you are touching code that uses this, please migrate to use broadfcui.components.modals
(defn push-ok-cancel-modal [props]
  (modal/push-modal [OKCancelForm props]))

;; Deprecated. If you are touching code that uses this, please migrate to use broadfcui.components.modals
(defn push-message [{:keys [header message]}]
  (push-ok-cancel-modal
   {:header (or header "Message")
    :data-test-id "push-message"
    :content [:div {:style {:maxWidth 500}} message]
    :show-cancel? false :ok-button "OK"}))

;; Deprecated. If you are touching code that uses this, please migrate to use broadfcui.components.modals
(defn push-error [content]
  (push-ok-cancel-modal
   {:header [:div {:style {:display "inline-flex" :alignItems "center"} :data-test-id "error-modal"}
             (icons/icon {:style {:color (:exception-state style/colors)
                                  :marginRight "0.5em"}} :error)
             "Error"]
    :data-test-id "push-error"
    :content [:div {:style {:maxWidth "50vw"} :data-test-id "error-text"} content]
    :show-cancel? false :ok-button "OK"}))

;; Deprecated. If you are touching code that uses this, please migrate to use broadfcui.components.modals
(defn push-error-response [error-response]
  (push-error [ErrorViewer {:error error-response}]))

;; Deprecated. If you are touching code that uses this, please migrate to use broadfcui.components.modals
(defn push-confirm [{:keys [header text on-confirm]}]
  (push-ok-cancel-modal
   {:header (or header "Confirm")
    :content [:div {:style {:maxWidth 500}} text]
    :ok-button on-confirm}))

(defn renderable? [thing]
  (or (react/valid-element? thing)
      (string? thing)
      (and (vector? thing)
           (pos? (count thing))
           (keyword? (first thing))
           (or (and (= 1 (count thing)))
               (let [[_ attr & rest] thing]
                 (and (map? attr)
                      (every? renderable? rest)))))))

(defn create-error-message [thing]
  (when (renderable? thing)
    #(push-error thing)))


(react/defc TagAutocomplete
  {:get-tags
   (fn [{:keys [refs]}]
     (or (-> (@refs "input-element") js/$ (.select2 "val") js->clj)
         []))
   :set-tags
   (fn [{:keys [refs]} tags]
     (-> (@refs "input-element") js/$ (.val tags) (.trigger "change")))
   :get-default-props
   (fn []
     {:show-counts? true
      :allow-new? true
      :allow-clear? false
      :minimum-input-length 3})
   :render
   (fn [{:keys [props]}]
     (style/create-identity-select {:ref "input-element"
                                    :defaultValue (:tags props)
                                    :multiple true
                                    :data-test-id (:data-test-id props)}
                                   (or (:data props) (:tags props))))
   :component-did-mount
   (fn [{:keys [props refs this]}]
     (let [{:keys [data allow-new? minimum-input-length placeholder allow-clear? maximum-selection-length language]} props
           component (js/$ (@refs "input-element"))
           data-source (if data
                         {:data data}
                         {:ajax {:url (str (config/api-url-root) "/api/workspaces/tags")
                                 :dataType "json"
                                 :type "GET"
                                 :headers {:Authorization (str "Bearer " (utils/get-access-token))}
                                 :data (fn [params]
                                         (clj->js {:q (aget params "term")}))
                                 :processResults (this :-process-results)}})]
       (.select2
        component
        (clj->js (merge
                  data-source
                  {:placeholder placeholder
                   :allowClear allow-clear?
                   :templateResult (this :-template-result)
                   :templateSelection (fn [x]
                                        (when x
                                          (or (aget x "tag") (aget x "text"))))
                   :tags allow-new?
                   :maximumSelectionLength (or maximum-selection-length 0)
                   :minimumInputLength minimum-input-length
                   :language (merge {:inputTooShort #(str "Enter at least " minimum-input-length " characters to search")}
                                    language)})))
       (.on component "change" #(this :-on-change))))
   :component-will-unmount
   (fn [{:keys [refs]}]
     (.select2 (js/$ (@refs "input-element")) "destroy"))
   ;; React can't re-render since select2 clobbers the DOM...
   :should-component-update
   (constantly false)
   ;; ...but we do need to patch in new client-side options
   :component-will-receive-props
   (fn [{:keys [refs props next-props this]}]
     (let [new-data (:data next-props)]
       (when (not= (:data props) new-data)
         (let [selected (this :get-tags)
               selected-list (if (sequential? selected) selected [selected])
               new-data (distinct (concat selected-list new-data))
               new-options (map (fn [item]
                                  (let [encoded (utils/encode item)]
                                    (str "<option value=\"" encoded "\">" encoded "</option>")))
                                new-data)]
           (.. (js/$ (@refs "input-element"))
               (html (string/join new-options))
               (val (clj->js selected))
               (change))))))
   :-on-change
   (fn [{:keys [props this]}]
     (when-let [f (:on-change props)]
       (f (this :get-tags)))) ; currently selected tags
   :-process-results
   (fn [_]
     (fn [data]
       (clj->js {:results (map (fn [res]
                                 (let [tag (res "tag")]
                                   (merge {"id" tag
                                           ;; text is needed to check equality with the typed input
                                           "text" tag}
                                          res)))
                               (js->clj data))})))
   :-template-result
   (fn [{:keys [props]}]
     (fn [res]
       (if (.-loading res)
         "Loading..."
         (let [show-counts? (:show-counts? props)
               ;; Insert zero-width space after underscore to allow line wrapping
               tag-text (.createTextNode js/document (or (.-tag res) (.-text res)))
               element (.createElement js/document "div")]
           (when show-counts?
             (react/render (react/create-element (style/render-count (or (.-count res) 0))) element))
           (.appendChild element tag-text)
           element))))})

;; Declared because it calls itself recursively.
(declare Tree)

(defn- is-branch-value? [value]
  (and (vector? value) (not-empty value)))

(defn- is-leaf-node? [node]
  (not-any? #(is-branch-value? (get node %)) (keys node)))

(defn- map-node [node f]
  (map
   (fn [key]
     (let [value (node key)]
       (f key value (not (is-branch-value? value)))))
   (keys node)))

(react/defc Tree
  ":start-collapsed? (optional [false]) - Start with branches collapsed
  :label (optional) - Label into which whole tree can be collapsed, must display inline
  :highlight-ends? (optional) - Highlight the ends of the tree as :warning-state
  :data - Vector of maps to display in tree, any value can be a nested vector of maps.
  NOTE: no current support for keys leading directly to nested maps."
  {:get-initial-state
   (fn [{:keys [props]}]
     {:collapsed? (or (:start-collapsed? props) false)})
   :render
   (fn [{:keys [props state]}]
     (let [body
           [:div {:hidden (and (:collapsed? @state) (:label props))
                  :style {:marginLeft (if (:label props) "0.5rem" 0)}}
            (map (fn [node]
                   [:ul {:style {:margin "0.2rem" :padding "0.5rem"
                                 :backgroundColor (if (and (:highlight-ends? props)
                                                           (is-leaf-node? node))
                                                    (:warning-state style/colors)
                                                    "rgba(0,0,0,0.1)")
                                 :borderRadius 8}}
                    (map-node
                     node
                     (fn [key value leaf?]
                       [:li {:style {:listStyle "none" :padding "0.1rem"}}
                        (if leaf?
                          [:span {} [:strong {} key ": "] value]
                          [Tree {:data value
                                 :start-collapsed? (:start-collapsed? props)
                                 :highlight-ends? (:highlight-ends? props)
                                 :label [:strong {} key ":"]}])]))])
                 (:data props))]]
       (if (:label props)
         [:span {}
          (:label props) " "
          (links/create-internal {:onClick #(swap! state update :collapsed? not)}
                                 (icons/icon {} (if (:collapsed? @state) :expand :collapse)))
          body]
         body)))})
