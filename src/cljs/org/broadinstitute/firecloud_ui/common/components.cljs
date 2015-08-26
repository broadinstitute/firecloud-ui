(ns org.broadinstitute.firecloud-ui.common.components
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


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
     [:a {:title (:title-text props)
          :style {:display "inline-block"
                  :backgroundColor (:color props)
                  :color "white" :fontWeight 500
                  :borderRadius 2 :padding (if (:icon props) "0.7em" "0.7em 1em")
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


(react/defc FilterButtons
  (let [Button
        (react/create-class
          {:render
           (fn [{:keys [props]}]
             [:div {:style {:float "left"
                            :backgroundColor (if (:active? props)
                                               (:button-blue style/colors)
                                               (:background-gray style/colors))
                            :color (when (:active? props) "white")
                            :marginLeft "1em" :padding "1ex" :width "16ex"
                            :border (str "1px solid " (:line-gray style/colors))
                            :borderRadius "2em"
                            :cursor "pointer"}
                    :onClick (fn [e] ((:onClick props) e))}
              (:text props)])})]
    {:render
     (fn [{:keys [props]}]
       [:div {:style {:display "inline-block" :marginLeft "-1em"}}
        (map (fn [button] [Button {:text (:text button)
                                   :active? (:active? button)
                                   :onClick (:onClick button)}])
          (:buttons props))
        [:div {:style {:clear "both"}}]])}))


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
     (fn []
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


(react/defc Dialog
  {:get-default-props
   (fn []
     {:blocking? true})
   :render
   (fn [{:keys [props state]}]
     (let [content (:content props)
           anchored? (not (nil? (:get-anchor-dom-node props)))]
       (assert (react/valid-element? content)
               (subs (str "Not a react element: " content) 0 200))
       (when (or (not anchored?) (:position @state))
         [:div {:style {:backgroundColor (if (:blocking? props)
                                           "rgba(110, 110, 110, 0.4)"
                                           "rgba(210, 210, 210, 0.4)")
                        :position "absolute" :zIndex 9999
                        :top 0 :left 0 :right 0 :height (.. js/document -body -offsetHeight)}
                :onKeyDown (common/create-key-handler [:esc] #((:dismiss-self props)))
                :onClick (when-not (:blocking? props) #((:dismiss-self props)))}
          [:div {:style (if anchored?
                          {:position "absolute"
                           :top (get-in @state [:position :top])
                           :left (get-in @state [:position :left])}
                          {:transform "translate(-50%, 0px)"
                           :position "relative" :marginBottom 60
                           :top 60 :left "50%" :width (:width props)})
                 :onClick (when-not (:blocking? props) #(.stopPropagation %))}
           content]])))
   :component-did-mount
   (fn [{:keys [this props state]}]
     (when-let [get-dom-node (:get-anchor-dom-node props)]
       (swap! state assoc :position {:top (.. (get-dom-node) -offsetTop)
                                     :left (.. (get-dom-node) -offsetLeft)}))
     (set! (.-onKeyDownHandler this)
           (common/create-key-handler [:esc] #((:dismiss-self props))))
     (.addEventListener js/window "keydown" (.-onKeyDownHandler this)))
   :component-will-unmount
   (fn [{:keys [this]}]
     (.removeEventListener js/window "keydown" (.-onKeyDownHandler this)))})


(react/defc Blocker
  {:render
   (fn [{:keys [props]}]
     (when (:banner props)
       [:div {:style {:backgroundColor "rgba(82, 129, 197, 0.4)"
                      :position "fixed" :top 0 :bottom 0 :right 0 :left 0 :zIndex 9999}}
        [:div {:style {:position "absolute" :top "50%" :left "50%"
                       :transform "translate(-50%, -50%)"
                       :backgroundColor "#fff" :padding "2em"}}
         [Spinner {:text (:banner props)}]]]))})


(react/defc CompleteIcon
  {:get-default-props
   (fn []
     {:color (:success-green style/colors)
      :size 24})
   :render
   (fn [{:keys [props]}]
     [:span {:style {:display "inline-block" :position "relative" :verticalAlign "middle"
                     :width (int (* 1.27 (:size props)))
                     :height (int (* 1.27 (:size props)))
                     :backgroundColor "fff" :borderRadius "100%"}}
      (style/center {}
        (icons/font-icon {:style {:color (:color props) :fontSize (int (* 0.5 (:size props)))}}
          :status-done))])})

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
       [:span {:style {:display "inline-block" :position "relative" :verticalAlign :middle}}
        (style/center {}
          [:div {}
           (hamburger "white")
           spacer
           (hamburger (:color props))
           spacer
           (hamburger (:color props))])]))})

(react/defc ExceptionIcon
  {:get-default-props
   (fn []
     {:size 24})
   :render
   (fn [{:keys [props]}]
     [:span {:style {:display "inline-block" :position "relative" :verticalAlign "middle"}}
      (style/center {}
        (icons/font-icon {:style {:color "#fff" :fontSize (:size props)}} :status-warning))])})
