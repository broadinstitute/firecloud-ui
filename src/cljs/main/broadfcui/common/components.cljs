(ns broadfcui.common.components
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.config :as config]
   [broadfcui.utils :as utils]
   [broadfcui.utils.user :as user]
   ))


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
     (this :-cycle))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (js/clearTimeout (:-cycle @locals)))
   :-cycle
   (fn [{:keys [this state locals]}]
     (swap! state update :dot-count #(mod (inc %) 4))
     (swap! locals assoc :-cycle (js/setTimeout #(this :-cycle) 600)))})


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
       (blocker (:banner props))))})


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
                                  (icons/render-icon {:style {:color (:state-exception style/colors)}}
                                                     :warning)]
                                 (str "Error: " expected-msg))
           [:div {:style {:textAlign "initial"}}
            (style/create-flexbox {:style {:marginBottom "0.25em"}}
                                  [:span {:style {:paddingRight "0.5rem"}}
                                   (icons/render-icon {:style {:color (:state-exception style/colors)}}
                                                      :warning)]
                                  (str "Error: " message))
            (if (:expanded? @state)
              [:div {}
               (links/create-internal {:onClick #(swap! state assoc :expanded? false)}
                                      (icons/render-icon {:className "fa-fw"} :disclosure-opened)
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
                                      (icons/render-icon {:className "fa-fw"} :disclosure-closed)
                                      "Show Details")])]))))})


(react/defc Breadcrumbs
  {:render
   (fn [{:keys [props]}]
     (let [sep (icons/render-icon {:style {:color (:text-lightest style/colors) :padding "0 0.5rem"}} :angle-right)
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
              :more-above? (pos? scroll-top)
              :more-below? (< (+ scroll-top inner-height) scroll-height))))})


(defn no-billing-projects-message []
  [:div {:data-test-id "no-billing-projects-message" :style {:textAlign "center"}}
   "You must have a billing project associated with your account to create a new workspace."
   (links/create-external {:href (config/billing-project-guide-url)
                           :style {:display "block"}}
                          "Learn how to create a billing project.")])


;; NOTE: TagAutocomplete currently fires :on-change on any update, due to the logic in
;; :component-will-receive-props.
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
                                 :headers (user/get-bearer-token-header)
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
  :highlight-ends? (optional) - Highlight the ends of the tree as :state-warning
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
                                                    (:state-warning style/colors)
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
                                 (icons/render-icon {} (if (:collapsed? @state) :expand :collapse)))
          body]
         body)))})
