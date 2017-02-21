(ns broadfcui.common.components
  (:require
    [clojure.string :refer [blank?]]
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.codemirror :refer [CodeMirror]]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.config :as config]
    [broadfcui.utils :as utils]
    ))


(declare push-error-text)
(declare create-error-message)

(react/defc Spinner
  {:render
   (fn [{:keys [props]}]
     [:span {:style {:margin "1em" :whiteSpace "nowrap"}}
      (icons/icon {:className "fa-pulse fa-lg fa-fw" :style {:marginRight "0.5rem"}} :spinner)
      (:text props)])})


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


(react/defc Button
  {:get-default-props
   (fn []
     {:color (:button-primary style/colors)})
   :render
   (fn [{:keys [props]}]
     (let [{:keys [color icon href disabled? onClick text style class-name]} props]
       [:a {:className (or class-name "button")
            :style (merge
                    {:display "inline-flex" :alignItems "center" :justifyContent "center"
                     :backgroundColor (if disabled? (:disabled-state style/colors) color)
                     :cursor (when disabled? "default")
                     :color "white" :fontWeight 500
                     :minHeight 19 :minWidth 19
                     :borderRadius 2 :padding (if text "0.7em 1em" "0.4em")
                     :textDecoration "none"}
                    (if (map? style) style {}))
            :href (or href "javascript:;")
            :onClick (if disabled? (create-error-message disabled?) onClick)
            :onKeyDown (when (and onClick (not disabled?))
                         (common/create-key-handler [:space :enter] onClick))}
        text
        (some->> icon (icons/icon {:style (if text
                                            {:fontSize 20 :margin "-0.5em -0.3em -0.5em 0.5em"}
                                            {:fontSize 18})}))]))})


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
     (let [disabled? (:disabled? props)]
       [:label {:style {:cursor (when-not disabled? "pointer")
                        :color (when disabled? (:text-light style/colors))}
                :title (when disabled? (:disabled-text props))
                :onClick (when disabled?
                           #(push-error-text
                             (or (:disabled-text props) "This option is not available.")))}
        [:input {:type "checkbox" :ref "check"
                 :defaultChecked (:initial-checked? props)
                 :disabled disabled?
                 :style {:cursor (when-not disabled? "pointer")}}]
        [:span {:style {:marginLeft "0.5ex"}} (:label props)]]))})


(react/defc TabBar
  (let [Tab (react/create-class
              {:get-initial-state
               (fn []
                 {:hovering? false})
               :render
               (fn [{:keys [props state]}]
                 [:a {:style {:flex "0 0 auto" :padding "1em 2em"
                              :borderLeft (when (zero? (:index props)) style/standard-line)
                              :borderRight style/standard-line
                              :backgroundColor (when (:active? props) "white")
                              :cursor "pointer" :textDecoration "none" :color "black"
                              :position "relative"}
                      :href (:href props)
                      :onMouseOver #(swap! state assoc :hovering? true)
                      :onMouseOut #(swap! state assoc :hovering? false)
                      :onClick #((:onClick props) %)}
                  (:text props)
                  (when (or (:active? props) (:hovering? @state))
                    [:div {:style {:position "absolute" :top "-0.5ex" :left 0
                                   :width "100%" :height "0.5ex"
                                   :backgroundColor (:button-primary style/colors)}}])
                  (when (:active? props)
                    [:div {:style {:position "absolute" :bottom -1 :left 0 :width "100%" :height 2
                                   :backgroundColor "white"}}])])})]
    {:render
     (fn [{:keys [props]}]
       (let [{:keys [selected-index items toolbar-right]} props]
         [:div {}
          [:div {:style {:display "flex"
                         :backgroundColor (:background-light style/colors)
                         :borderTop style/standard-line
                         :borderBottom style/standard-line
                         :padding "0 1.5em"}}
           (map-indexed
             (fn [i tab]
               [Tab {:index i :text (:text tab) :href (:href tab)
                     :active? (= i selected-index)
                     :onClick (fn [e]
                                (let [k (if (= i selected-index) :onTabRefreshed :onTabSelected)
                                      f (tab k)]
                                  (when f (f e))))}])
             items)
           [:div {:style {:flexGrow 1}}]
           [:div {:style {:alignSelf "center"}}
            toolbar-right]]
          (let [active-item (nth items selected-index)]
            (:content active-item))]))}))


(react/defc XButton
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:float "right" :marginRight "-28px" :marginTop "-1px"}}
      [:a {:style {:color (:text-light style/colors)}
           :href "javascript:;"
           :onClick (:dismiss props)
           :id (:id props)}
       (icons/icon {:style {:fontSize "80%"}} :close)]])})


;; TODO: find out if :position "absolute" would work everywhere, or possibly get rid of Blocker entirely
(defn- blocker [text position]
  [:div {:style {:backgroundColor "rgba(210, 210, 210, 0.4)"
                 :position position :top 0 :bottom 0 :right 0 :left 0 :zIndex 9999
                 :display "flex" :justifyContent "center" :alignItems "center"}}
   [:div {:style {:backgroundColor "#fff" :padding "2em"}}
    [Spinner {:text text}]]])

(react/defc Blocker
  {:render
   (fn [{:keys [props]}]
     (when-let [text (:banner props)]
       (blocker text "fixed")))})

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
       (blocker (:banner props) "absolute")))})


(react/defc StatusLabel
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:display "flex" :background (:color props) :color "#fff"
                    :padding "15px 20px" :marginBottom "2em" :lineHeight "36px"
                    :alignItems "center" :justifyContent "center"}}
      (:icon props)
      [:span {:style {:marginLeft "1em" :fontSize "125%" :fontWeight 400
                      :verticalAlign "middle"}}
       (:text props)]])})

(react/defc SidebarButton
  {:get-default-props
   (fn []
     {:style :heavy})
   :render
   (fn [{:keys [props]}]
     (let [heavy? (= :heavy (:style props))
           disabled? (:disabled? props)
           margin (:margin props)
           color (cond (keyword? (:color props)) (get style/colors (:color props))
                       :else (:color props))]
       [:div {:style {:fontSize "106%"
                      :marginTop (when (= margin :top) "1em")
                      :marginBottom (when (= margin :bottom) "1em")
                      :padding "0.7em 0"
                      :cursor (if disabled? "default" "pointer")
                      :display "flex" :flexWrap "nowrap" :alignItems "center"
                      :backgroundColor (if disabled? (:disabled-state style/colors) (if heavy? color "transparent"))
                      :color (if heavy? "#fff" color)
                      :border (when-not heavy? style/standard-line)
                      :borderRadius 5}
              :onClick (if disabled? (create-error-message disabled?) (:onClick props))}
        (icons/icon {:style {:padding "0 20px" :borderRight style/standard-line} :className "fa-fw"} (:icon props))
        [:div {:style {:textAlign "center" :margin "auto"}}
         (:text props)]]))})

(react/defc EntityDetails
  {:get-fields
   (fn [{:keys [refs]}]
     {"methodVersion" (int (common/get-text refs "snapshotId"))})
   :render
   (fn [{:keys [props refs state this]}]
     [:div {} (when-let [wdl-parse-error (:wdl-parse-error props)] (style/create-server-error-message wdl-parse-error))
      (let [entity (:entity props)
            editing? (:editing? props)
            make-field
            (fn [entity key label dropdown? & [render]]
              [:div {}
               [:span {:style {:fontWeight 500 :width 100 :display "inline-block" :paddingBottom "0.3em"}} label]
               (if (and editing? dropdown?)
                 (style/create-identity-select {:ref key
                                                :style {:width 100}
                                                :defaultValue (entity key)
                                                :onChange (when-let [f (:onSnapshotIdChange props)]
                                                            #(f (int (common/get-text refs "snapshotId"))))}
                                               (:snapshots props))
                 [:span {} ((or render identity) (entity key))])])
            config? (contains? entity "method")]
       [:div {:style {:backgroundColor (:background-light style/colors)
                      :borderRadius 8 :border style/standard-line
                      :padding "1em"}}
        (react/call :render-details this make-field entity)
        [:div {:style {:paddingTop "0.5em"}}
         [:span {:style {:fontWeight 500 :marginRight "1em"}} (if config? "Referenced Method:" "WDL:")]
         (style/create-link {:text (if (:payload-expanded @state) "Collapse" "Expand")
                             :onClick #(swap! state assoc :payload-expanded (not (:payload-expanded @state)))})]
        (when (:payload-expanded @state)
          (if config?
            [:div {:style {:margin "0.5em 0 0 1em"}}
             (react/call :render-details this make-field (entity "method"))
             [:div {:style {:fontWeight 500 :marginTop "1em"}} "WDL:"]
             [CodeMirror {:text (get-in entity ["method" "payload"])}]]
            [CodeMirror {:text (entity "payload")}]))])])
   :render-details
   (fn [{:keys []} make-field entity]
     [:div {}
      [:div {:style {:float "left" :marginRight "5em"}}
       (make-field entity "namespace" "Namespace: " false)
       (make-field entity "name" "Name: " false)
       (make-field entity "snapshotId" "Snapshot ID: " true)]
      [:div {:style {:float "left"}}
       (make-field entity "createDate" "Created: " false common/format-date)
       (make-field entity "entityType" "Entity Type: " false)
       (make-field entity "managers" "Owners: " false (partial clojure.string/join ", "))
       (make-field entity "synopsis" "Synopsis: " false)]
      (common/clear-both)
      [:div {:style {:fontWeight 500 :padding "0.5em 0 0.3em 0"}}
       "Documentation:"]
      (if (blank? (entity "documentation"))
        [:div {:style {:fontStyle "italic" :fontSize "90%"}} "No documentation provided"]
        [:div {:style {:fontSize "90%"}} (entity "documentation")])])})


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
          (style/create-link {:text "Hide Stack Trace" :onClick #(swap! state assoc :expanded? false)})]
         [:div {} (style/create-link {:text "Show Stack Trace" :onClick #(swap! state assoc :expanded? true)})]))})


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
               (style/create-link {:text "Hide Cause" :onClick #(swap! state assoc :expanded? false)})])
         [:div {} (style/create-link {:text "Show Cause" :onClick #(swap! state assoc :expanded? true)})]))})

(react/defc ErrorViewer
  {:render
   (fn [{:keys [props]}]
     (when-let [error (:error props)]
       (let [[source timestamp status-code code causes stack-trace message]
             (map error ["source" "timestamp" "statusCode" "code" "causes" "stackTrace" "message"])
             ;; method redact is responding with "code" for 401.  TODO: standardize and remove this extra logic
             status-code (or status-code code)]
         (if-let [expected-msg (get-in props [:expect status-code])]
           (style/create-flexbox {}
                                 [:span {:style {:paddingRight "1ex"}}
                                  (icons/icon {:style {:color (:exception-state style/colors)}}
                                              :warning-triangle)]
                                 (str "Error: " expected-msg))
           [:div {:style {:textAlign "initial"}}
            (style/create-flexbox {:style {:marginBottom "0.25em"}}
                                  [:span {:style {:paddingRight "1ex"}}
                                   (icons/icon {:style {:color (:exception-state style/colors)}}
                                               :warning-triangle)]
                                  (str "Error " status-code ": " message))
            (when timestamp [:div {} "Occurred: "
                             (common/format-date timestamp
                                                 (assoc common/default-date-format
                                                   :timeZoneName "short"))])
            (when source [:div {} "Source: " source])
            (when (seq causes)
              (let [num-hidden (- (count causes) 4)]
                [:div {}
                 [:div {} (str "Cause" (when (> (count causes) 1) "s") ":")]
                 (map (fn [cause] [CauseViewer cause]) (take 4 causes))
                 (when (pos? num-hidden)
                   [:div {} (str num-hidden " not shown")])]))
            (when (seq stack-trace)
              [StackTraceViewer {:lines stack-trace}])]))))})


(react/defc Breadcrumbs
  {:get-default-props
   (fn []
     {:scale-factor "100%"})
   :render
   (fn [{:keys [props]}]
     (let [sep (icons/icon {:style {:color (:text-lightest style/colors) :padding "0 0.5rem"
                                    :fontSize (:scale-factor props)}} :angle-right)
           crumbs (filter some? (:crumbs props))]
       (case (count crumbs)
         0 [:div {}]
         1 [:div {} (:text (first crumbs))]
         [:div {:style {:display "flex" :alignItems "baseline" :flexWrap "wrap"}}
          (interpose sep
            (map
              (fn [{:keys [text onClick href] :as link-props}]
                [:span {:style {:fontSize (:scale-factor props) :whiteSpace "pre"}}
                 (if (or onClick href)
                   (style/create-link link-props)
                   text)])
              (butlast crumbs)))
          sep
          (:text (last crumbs))])))})


(react/defc SplitPane
  {:get-default-props
   (fn []
     {:overflow-left "auto"})
   :get-initial-state
   (fn [{:keys [props]}]
     {:slider-position (or (:initial-slider-position props) 100)})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [left right top bottom]} props
           grab-bar [:div {:style {:flex "0 0 2px" :borderRadius 1 :backgroundColor "#d0d0d0"}}]]
       (assert (or (and left right) (and top bottom)) "Either specify left/right or top/bottom for SplitPane")
       [:div {:style {:display "flex" :flexDirection (if left "row" "column")}}
        [:div {:style {:flexGrow 0 :flexShrink 0 :flexBasis (:slider-position @state) :overflow (:overflow-left props)}}
         (or left top)]
        [:div {:style {:flex "0 0 10px"
                       :display "flex" :flexDirection (if left "column" "row") :justifyContent "center"
                       :backgroundColor (:background-light style/colors)
                       :cursor (if left "ew-resize" "ns-resize")}
               :onMouseDown (fn [e]
                              (swap! state assoc
                                     :dragging? true
                                     :mouse-pos (if left (.-clientX e) (.-clientY e))
                                     :text-selection (common/disable-text-selection)))}
         [:div {:style {:flex "0 0 10px" :padding 1
                        :display "flex" :flexDirection (if left "row" "column") :justifyContent "space-between"}}
          grab-bar grab-bar grab-bar]]
        [:div {:style {:flex "1 0 0" :overflow "auto"}}
         (or right bottom)]]))
   :component-did-mount
   (fn [{:keys [props state locals]}]
     (let [{:keys [left]} props
           on-mouse-up #(when (:dragging? @state)
                         (common/restore-text-selection (:text-selection @state))
                         (swap! state dissoc :dragging? :text-selection))
           on-mouse-move (fn [e]
                           (when (:dragging? @state)
                             (let [start-pos (:mouse-pos @state)
                                   pos (if left (.-clientX e) (.-clientY e))]
                               (when-not (= start-pos pos)
                                 (swap! state assoc
                                        :slider-position (+ (:slider-position @state) (- pos start-pos))
                                        :mouse-pos pos)))))]
       (swap! locals assoc :on-mouse-up on-mouse-up :on-mouse-move on-mouse-move)
       (.addEventListener js/window "mouseup" on-mouse-up)
       (.addEventListener js/window "mousemove" on-mouse-move)))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (.removeEventListener js/window "mouseup" (:on-mouse-up @locals))
     (.removeEventListener js/window "mousemove" (:on-mouse-move @locals)))})


(react/defc TextFilter
  {:render
   (fn [{:keys [props this]}]
     (let [{:keys [initial-text placeholder width]} props]
       [:div {:style {:display "inline-flex" :width width}}
        (style/create-search-field
          {:ref "filter-field" :autoSave "true" :results 5 :autofocus "true"
           :placeholder (or placeholder "Filter") :defaultValue initial-text
           :style {:flex "1 0 auto" :borderRadius "3px 0 0 3px" :marginBottom 0}
           :onKeyDown (common/create-key-handler [:enter] #(react/call :apply-filter this))})
        [Button {:icon :search :onClick #(react/call :apply-filter this)
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
     (style/create-search-field (merge {:ref "field" :className "typeahead"}
                                       (:field-attributes props))))
   :component-did-mount
   (fn [{:keys [props refs]}]
     (let [{:keys [remote render-display behavior empty-message render-suggestion on-select typeahead-events]} props
           whitespace-tokenizer (aget Bloodhound "tokenizers" "whitespace")]
       (.typeahead (js/$ (@refs "field"))
                   (clj->js behavior)
                   (clj->js
                    {:source (Bloodhound. (clj->js {:datumTokenizer whitespace-tokenizer
                                                    :queryTokenizer whitespace-tokenizer
                                                    :remote remote}))
                     :display render-display
                     :templates {:empty (str "<div style='padding: 0.5em'>" empty-message "</div>")
                                 :suggestion render-suggestion}}))
       (doseq [item typeahead-events]
         (.bind (js/$ (@refs "field")) item on-select)))
     (.addEventListener (@refs "field") "search"
                        #(when (and (empty? (.. % -currentTarget -value))
                                    (:on-clear props))
                           ((:on-clear props)))))})


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
     (let [{:keys [field-attributes width]} props]
       [:div {:style {:display "inline-flex" :width width}}
        [Typeahead {:ref "typeahead"
                    :field-attributes (utils/deep-merge
                                       {:autoSave "true" :results 5 :autofocus "true"
                                        :placeholder "Filter"
                                        :style {:flex "1 0 auto" :width width :borderRadius 3 :marginBottom 0}
                                        :onKeyDown (common/create-key-handler [:enter] #(react/call :apply-filter this))}
                                       field-attributes)
                    :behavior {:hint false :minLength 3}
                    :remote (:bloodhoundInfo props)
                    :render-display (:typeaheadDisplay props)
                    :empty-message "<small>Unable to find any matches to the current query</small>"
                    :typeahead-events (:typeahead-events props)
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
             :onScroll #(react/call :update-edges this)}
       [:div {:ref "content-container"}
        (:content props)]]
      (react/call :build-shadow this true)
      (react/call :build-shadow this false)])
   :component-did-mount
   (fn [{:keys [refs this]}]
     (react/call :update-edges this)
     ;; this listener also fires upon being added
     (.addEventListener (@refs "content-container") "onresize"
                        #(react/call :update-edges this)))
   :build-shadow
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
   :update-edges
   (fn [{:keys [state refs]}]
     (let [scroll-top (.-scrollTop (@refs "scroller"))
           scroll-height (.-scrollHeight (@refs "scroller"))
           inner-height (.-offsetHeight (@refs "scroller"))]
       (swap! state assoc
              :more-above? (> scroll-top 0)
              :more-below? (< (+ scroll-top inner-height) scroll-height))))})


(react/defc OKCancelForm
  {:get-default-props
   (fn []
     {:show-cancel? true
      :show-close? true})
   :render
   (fn [{:keys [props]}]
     (let [{:keys [header content ok-button show-cancel? cancel-text show-close?]} props
           cancel-text (or cancel-text "Cancel")]
       [:div {}
        [:div {:style {:borderBottom style/standard-line
                       :padding "20px 48px 18px"
                       :fontSize "137%" :fontWeight 400 :lineHeight 1}}
         header
         (when show-close? [XButton {:dismiss modal/pop-modal}])]
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
                   :onClick modal/pop-modal
                   :onKeyDown (common/create-key-handler [:space :enter] modal/pop-modal)}
               cancel-text])
            (when ok-button
            (cond (string? ok-button) [Button {:text ok-button :ref "ok-button" :class-name "ok-button" :onClick modal/pop-modal}]
              (fn? ok-button) [Button {:text "OK" :ref "ok-button" :class-name "ok-button" :onClick ok-button}]
              (map? ok-button) [Button (merge {:ref "ok-button" :class-name "ok-button"} ok-button)]
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

(react/defc NoBillingProjectsMessage
  {:render
  (fn [{:keys [props]}]
    [:div {:style {:textAlign "center"}} (str "You must have a billing project associated with your account to create a new workspace. ")
      [:a {:target "_blank" :href (str (config/billing-guide-url))} "Learn how to create a billing project."]])})

(defn push-ok-cancel-modal [props]
  (modal/push-modal [OKCancelForm props]))

(defn push-message [{:keys [header message]}]
  (push-ok-cancel-modal
    {:header (or header "Message")
     :content [:div {:style {:maxWidth 500}} message]
     :show-cancel? false :ok-button "OK"}))

(defn- push-error [content]
  (push-ok-cancel-modal
   {:header [:div {:style {:display "inline-flex" :alignItems "center"}}
             (icons/icon {:style {:color (:exception-state style/colors)
                                  :marginRight "0.5em"}} :error)
             "Error"]
    :content [:div {:style {:maxWidth "50vw"}} content]
    :show-cancel? false :ok-button "OK"}))

(defn push-error-text [error-text]
  (push-error error-text))

(defn push-error-response [error-response]
  (push-error [ErrorViewer {:error error-response}]))

(defn push-confirm [{:keys [header text on-confirm]}]
  (push-ok-cancel-modal
    {:header (or header "Confirm")
     :content [:div {:style {:maxWidth 500}} text]
     :ok-button on-confirm}))

(defn create-error-message [disabled?]
  (when (string? disabled?)
    #(push-error-text disabled?)))
