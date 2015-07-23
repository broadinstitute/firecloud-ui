(ns org.broadinstitute.firecloud-ui.common.components
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]))


(react/defc Spinner
  {:render
   (fn [{:keys [props]}]
     [:span {:style {:margin "1em"}}
      [:img {:src "assets/spinner.gif"
             :style {:height "1.5em" :verticalAlign "middle" :marginRight "1ex"}}]
      (:text props)])})


(react/defc Button
  {:get-default-props
   (fn []
     {:color (:button-blue style/colors)})
   :render
   (fn [{:keys [props]}]
     [:a {:style {:display "inline-block"
                  :backgroundColor (:color props)
                  :color "white" :fontWeight 500
                  :borderRadius 2 :padding "0.7em 1em"
                  :fontFamily (when (:icon props) "fontIcons")
                  :fontSize (when (:icon props) "80%")
                  :textDecoration "none"}
          :href "javascript:;"
          :onClick (fn [e] ((:onClick props) e))
          :onKeyDown (common/create-key-handler [:space :enter] (:onClick props))}
      (or (:text props) (icons/icon-text (:icon props)))
      (when (= (:style props) :add)
        [:span {:style {:display "inline-block" :height "1em" :width "1em" :marginLeft "1em"
                        :position "relative"}}
         [:span {:style {:position "absolute" :top "-55%" :fontSize "200%" :fontWeight "normal"}}
          "+"]])])})


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
                        :onMouseOver (fn [e] (swap! state assoc :hovering? true))
                        :onMouseOut (fn [e] (swap! state assoc :hovering? false))
                        :onClick (fn [e] ((:onClick props) e))}
                  (:text props)
                  (when (or (:active? props) (:hovering? @state))
                    [:div {:style {:position "absolute" :top "-0.5ex" :left 0
                                   :width "100%" :height "0.5ex"
                                   :backgroundColor (:button-blue style/colors)}}])
                  (when (:active? props)
                    [:div {:style {:position "absolute" :bottom -1 :left 0 :width "100%" :height 2
                                   :backgroundColor "white"}}])])})]
    {:get-initial-state
     (fn [{:keys [props]}]
       {:active-tab-index 0})
     :render
     (fn [{:keys [props state]}]
       [:div {}
        [:div {:style {:backgroundColor (:background-gray style/colors)
                       :borderTop (str "1px solid " (:line-gray style/colors))
                       :borderBottom (str "1px solid " (:line-gray style/colors))
                       :padding "0 1.5em"}}
         (map-indexed
           (fn [i tab]
             [Tab {:index i :text (:text tab)
                   :active? (= i (:active-tab-index @state))
                   :onClick (fn [e]
                              (swap! state assoc :active-tab-index i)
                              (when-let [f (:onTabSelected tab)] (f e)))}])
           (:items props))
         [:div {:style {:clear "both"}}]]
        [:div {} (:component (nth (:items props) (:active-tab-index @state)))]])}))


(react/defc CompleteIcon
  {:get-default-props
   (fn []
     {:color (:success-green style/colors)
      :size 24})
   :render
   (fn [{:keys [props]}]
     (style/center {:style {:width (int (* 1.27 (:size props)))
                            :height (int (* 1.27 (:size props)))
                            :backgroundColor "fff" :borderRadius "100%"}}
       (style/center {}
         (icons/font-icon {:style {:color (:color props) :fontSize (int (* 0.5 (:size props)))}} :status-done))))})

(react/defc RunningIcon
  {:get-default-props
   (fn []
     {:color (:success-green style/colors)
      :size 24})
   :render
   (fn [{:keys [props]}]
     (let [hamburger-width (int (+ (:size props) 2))
           hamburger-height (int (/ hamburger-width 6))
           spacer-height (int (/ (- (:size props) 4 (* 3 hamburger-height)) 2))
           hamburger (fn [color] [:div {:style {:height hamburger-height
                                                :width hamburger-width
                                                :borderRadius hamburger-height
                                                :backgroundColor color}}])
           spacer [:div {:style {:height spacer-height}}]]
       (style/center {}
         [:div {}
          (hamburger "white")
          spacer
          (hamburger (:color props))
          spacer
          (hamburger (:color props))])))})

(react/defc ExceptionIcon
  {:get-default-props
   (fn []
     {:size 24})
   :render
   (fn [{:keys [props]}]
     (style/center {}
      (icons/font-icon {:style {:color "#fff" :fontSize (:size props)}} :status-warning)))})
