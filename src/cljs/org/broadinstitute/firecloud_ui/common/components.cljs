(ns org.broadinstitute.firecloud-ui.common.components
  (:require
    [clojure.string :refer [blank?]]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    ))


(react/defc Spinner
  {:render
   (fn [{:keys [props]}]
     [:span {:style {:margin "1em" :whiteSpace "nowrap"}}
      [:img {:src "assets/spinner.gif"
             :style {:height "1.5em" :verticalAlign "middle" :marginRight "1ex"}}]
      (:text props)])})


(react/defc Button
  {:get-default-props
   (fn []
     {:color (:button-blue style/colors)})
   :render
   (fn [{:keys [props]}]
     (let [disabled? (:disabled? props)]
       [:a {:style {:display "inline-block"
                    :backgroundColor (:color props)
                    :WebkitFilter (when disabled? "grayscale()")
                    :cursor (when disabled? "default")
                    :color "white" :fontWeight 500
                    :borderRadius 2 :padding (if (:icon props) "0.7em" "0.7em 1em")
                    :fontFamily (when (:icon props) "fontIcons")
                    :fontSize (when (:icon props) "80%")
                    :textDecoration "none"}
            :href (or (:href props) "javascript:;")
            :onClick (if disabled?
                       #(js/alert (if (string? disabled?) disabled? "This action is disabled"))
                       (when-let [on-click (:onClick props)] #(on-click %)))
            :onKeyDown (when-not disabled? (common/create-key-handler [:space :enter] (:onClick props)))}
        (or (:text props) (icons/icon-text (:icon props)))
        (when (= (:style props) :add)
          [:span {:style {:display "inline-block" :height "1em" :width "1em" :marginLeft "1em"
                          :position "relative"}}
           [:span {:style {:position "absolute" :top "-55%" :fontSize "200%" :fontWeight "normal"}}
            "+"]])]))})


(react/defc TabBar
  (let [Tab (react/create-class
              {:get-initial-state
               (fn []
                 {:hovering? false})
               :render
               (fn [{:keys [props state]}]
                 [:div {:style {:float "left" :padding "1em 2em"
                                :borderLeft (when (zero? (:index props))
                                              (str "1px solid " (:line-gray style/colors)))
                                :borderRight (str "1px solid " (:line-gray style/colors))
                                :backgroundColor (when (:active? props) "white")
                                :cursor "pointer"
                                :position "relative"}
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
                         :borderTop (str "1px solid " (:line-gray style/colors))
                         :borderBottom (str "1px solid " (:line-gray style/colors))
                         :padding "0 1.5em"}}
           (map-indexed
             (fn [i tab]
               [Tab {:index i :text (:text tab)
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
      [Button {:icon :x :onClick #((:dismiss props))}]])})


(react/defc Blocker
  {:render
   (fn [{:keys [props]}]
     (when (:banner props)
       [:div {:style {:backgroundColor "rgba(210, 210, 210, 0.4)"
                      :position "fixed" :top 0 :bottom 0 :right 0 :left 0 :zIndex 9999}}
        [:div {:style {:position "absolute" :top "50%" :left "50%"
                       :transform "translate(-50%, -50%)"
                       :backgroundColor "#fff" :padding "2em"}}
         [Spinner {:text (:banner props)}]]]))})


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
                      :border (when-not heavy? (str "1px solid " (:line-gray style/colors)))
                      :borderRadius (when heavy? 4)}
              :onClick (if disabled?
                         #(js/alert (if (string? disabled?) disabled? "This action is disabled"))
                         (:onClick props))}
        (icons/font-icon {:style {:verticalAlign "middle" :fontSize "135%"}} (:icon props))
        [:span {:style {:verticalAlign "middle" :marginLeft "1em"}} (:text props)]]))})

(react/defc EntityDetails
  {:render
   (fn [{:keys [props state this]}]
     (let [entity (:entity props)
           make-field
           (fn [entity key label & [render]]
             [:div {}
              [:span {:style {:fontWeight 500 :width 100 :display "inline-block" :paddingBottom "0.3em"}} label]
              [:span {} ((or render identity) (entity key))]])
           config? (contains? entity "method")]
       [:div {:style {:backgroundColor (:background-gray style/colors)
                      :borderRadius 8 :border (str "1px solid " (:line-gray style/colors))
                      :padding "1em"}}
        (react/call :render-details this make-field entity)
        [:div {:style {:paddingTop "0.5em"}}
         [:span {:style {:fontWeight 500 :marginRight "1em"}} (if config? "Referenced Method:" "Payload:")]
         (style/create-link {:text (if (:payload-expanded @state) "Collapse" "Expand")
                             :onClick #(swap! state assoc :payload-expanded (not (:payload-expanded @state)))})]
        (when (:payload-expanded @state)
          (if config?
            [:div {:style {:margin "0.5em 0 0 1em"}}
             (react/call :render-details this make-field (entity "method"))
             [:div {:style {:fontWeight 500 :marginTop "1em"}} "Payload:"]
             [:pre {:style {:fontSize "90%"}} (get-in entity ["method" "payload"])]]
            [:pre {:style {:fontSize "90%"}} (entity "payload")]))]))
   :render-details
   (fn [{:keys []} make-field entity]
     [:div {}
      [:div {:style {:float "left"}}
       (make-field entity "namespace" "Namespace: ")
       (make-field entity "name" "Name: ")
       (make-field entity "snapshotId" "Snapshot ID: ")]
      [:div {:style {:float "left" :marginLeft "5em"}}
       (make-field entity "createDate" "Created: " common/format-date)
       (make-field entity "entityType" "Entity Type: ")
       (make-field entity "synopsis" "Synopsis: ")]
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
         (style/create-link {:text "Show Cause" :onClick #(swap! state assoc :expanded? true)})))})

(react/defc ErrorViewer
  {:render
   (fn [{:keys [props]}]
     (when-let [error (:error props)]
       (let [[source status-code causes stack-trace message]
             (map error ["source" "statusCode" "causes" "stackTrace" "message"])]
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
            (when source [:div {} "Source: " source])
            (when (seq causes)
              [:div {}
               [:div {} (str "Cause" (when (> (count causes) 1) "s") ":")]
               (map (fn [cause] [CauseViewer cause]) causes)])
            (when (seq stack-trace)
              [StackTraceViewer {:lines stack-trace}])]))))})


(react/defc Breadcrumbs
  {:render
   (fn [{:keys [props]}]
     (let [sep [:span {} " " (icons/font-icon {:style {:fontSize "50%"}} :angle-right) " "]
           crumbs (:crumbs props)]
       (case (count crumbs)
         0 [:div {}]
         1 [:div {} (:text (first crumbs))]
         [:div {}
          (interpose sep
            (map-indexed
              (fn [index {:keys [text onClick href] :as link-props}]
                [:span {:style {:fontSize "60%" :verticalAlign "middle" :whiteSpace "pre"}}
                 (if (or onClick href)
                   (style/create-link link-props)
                   text)])
              (butlast crumbs)))
          sep
          [:span {:style {:fontSize "60%" :verticalAlign "middle"}}
           (:text (last crumbs))]])))})
