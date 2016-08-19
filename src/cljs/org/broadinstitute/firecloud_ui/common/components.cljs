(ns org.broadinstitute.firecloud-ui.common.components
  (:require
    [clojure.string :refer [blank?]]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.codemirror :refer [CodeMirror]]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(react/defc Spinner
  {:render
   (fn [{:keys [props]}]
     [:span {:style {:margin "1em" :whiteSpace "nowrap"}}
      [:img {:src "assets/spinner.gif"
             :style {:height (or (:height props) "1.5em")
                     :verticalAlign "middle" :marginRight "1ex"}}]
      (:text props)])})


(react/defc Button
  {:get-default-props
   (fn []
     {:color (:button-blue style/colors)})
   :render
   (fn [{:keys [props]}]
     (let [{:keys [color icon href disabled? onClick text style class-name]} props]
       [:a {:className (or class-name "button")
            :style {:display "inline-block"
                    :backgroundColor color
                    :WebkitFilter (when disabled? "grayscale()")
                    :cursor (when disabled? "default")
                    :color "white" :fontWeight 500
                    :borderRadius 2 :padding (if icon "0.7em" "0.7em 1em")
                    :fontFamily (when icon "fontIcons")
                    :fontSize (when icon "80%")
                    :textDecoration "none"}
            :href (or href "javascript:;")
            :onClick (if disabled?
                       #(js/alert (if (string? disabled?) disabled? "This action is disabled"))
                       onClick)
            :onKeyDown (when (and onClick (not disabled?))
                         (common/create-key-handler [:space :enter] onClick))}
        (or text (icons/icon-text icon))
        (when (= style :add)
          [:span {:style {:display "inline-block" :height "1em" :width "1em" :marginLeft "1em"
                          :position "relative"}}
           [:span {:style {:position "absolute" :top "-55%" :fontSize "200%" :fontWeight "normal"}}
            "+"]])]))})


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
                        :color (when disabled? (:text-gray style/colors))}
                :title (when disabled? (:disabled-text props))
                :onClick (when disabled? #(js/alert (or (:disabled-text props)
                                                        "This option is not available.")))}
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
                 [:a {:style {:float "left" :padding "1em 2em"
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
                                   :backgroundColor (:button-blue style/colors)}}])
                  (when (:active? props)
                    [:div {:style {:position "absolute" :bottom -1 :left 0 :width "100%" :height 2
                                   :backgroundColor "white"}}])])})]
    {:render
     (fn [{:keys [this props]}]
       (let [{:keys [selected-index items]} props]
         [:div {}
          [:div {:style {:backgroundColor (:background-gray style/colors)
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
           (common/clear-both)]
          (let [active-item (nth items selected-index)]
            (:content active-item))]))}))


(react/defc XButton
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:position "absolute" :top 4 :right 4}}
      [Button {:icon :x
               :onClick (:dismiss props)
               :id (:id props)}]])})


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
     [:div {:style {:background (:color props) :color "#fff"
                    :padding 20 :borderRadius 5 :textAlign "center"}}
      (:icon props)
      [:span {:style {:marginLeft "1.5ex" :fontSize "125%" :fontWeight 400
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
                      :WebkitFilter (when disabled? "grayscale()")
                      :marginTop (when (= margin :top) "1em")
                      :marginBottom (when (= margin :bottom) "1em")
                      :padding "0.7em 0" :textAlign "center"
                      :cursor (if disabled? "default" "pointer")
                      :backgroundColor (if heavy? color "transparent")
                      :color (if heavy? "#fff" color)
                      :border (when-not heavy? style/standard-line)
                      :borderRadius (when heavy? 4)}
              :onClick (if disabled?
                         #(js/alert (if (string? disabled?) disabled? "This action is disabled"))
                         (:onClick props))}
        (icons/font-icon {:style {:verticalAlign "middle" :fontSize "135%"}} (:icon props))
        [:span {:style {:verticalAlign "middle" :marginLeft "1em"}} (:text props)]]))})

(react/defc EntityDetails
  {:get-fields
   (fn [{:keys [refs]}]
     {"methodVersion" (int (common/get-text refs "snapshotId"))})
   :render
   (fn [{:keys [props refs state this]}]
     (let [entity (:entity props)
           editing? (:editing? props)
           make-field
           (fn [entity key label dropdown? & [render]]
             [:div {}
              [:span {:style {:fontWeight 500 :width 100 :display "inline-block" :paddingBottom "0.3em"}} label]
              (if (and editing? dropdown?)
                (style/create-identity-select {:ref key
                                               :style {:width "100px"}
                                               :defaultValue (entity key)
                                               :onChange (when-let [f (:onSnapshotIdChange props)]
                                                           #(f (int (common/get-text refs "snapshotId"))))}
                                              (:snapshots props))
                [:span {} ((or render identity) (entity key))])])
           config? (contains? entity "method")]
       [:div {:style {:backgroundColor (:background-gray style/colors)
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
            [CodeMirror {:text (entity "payload")}]))]))
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
           [:div {}
            [:span {:style {:paddingRight "1ex"}}
             (icons/font-icon {:style {:color (:exception-red style/colors)}}
               :status-warning-triangle)]
            (str "Error: " expected-msg)]
           [:div {:style {:textAlign "initial"}}
            [:div {}
             [:span {:style {:paddingRight "1ex"}}
              (icons/font-icon {:style {:color (:exception-red style/colors)}}
                :status-warning-triangle)]
             (str "Error " status-code ": " message)]
            (when timestamp [:div {} "Occurred: " (-> timestamp js/moment (.format "LLL Z"))])
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
  {:render
   (fn [{:keys [props]}]
     (let [sep [:span {} " " (icons/font-icon {:style {:fontSize "50%"}} :angle-right) " "]
           crumbs (filter some? (:crumbs props))]
       (case (count crumbs)
         0 [:div {}]
         1 [:div {} (:text (first crumbs))]
         [:div {}
          (interpose sep
            (map
              (fn [{:keys [text onClick href] :as link-props}]
                [:span {:style {:fontSize "60%" :verticalAlign "middle" :whiteSpace "pre"}}
                 (if (or onClick href)
                   (style/create-link link-props)
                   text)])
              (butlast crumbs)))
          sep
          [:span {:style {:verticalAlign "middle"}}
           (:text (last crumbs))]])))})


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
                       :backgroundColor (:background-gray style/colors)
                       :margin (if left "0 3px" "3px 0")
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
