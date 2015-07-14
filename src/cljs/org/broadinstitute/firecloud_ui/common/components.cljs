(ns org.broadinstitute.firecloud-ui.common.components
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.utils :as utils :refer [rlog jslog cljslog]]
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
     [:a {:style {:display "inline-block"
                  :backgroundColor (:color props)
                  :color "white" :fontWeight 500
                  :borderRadius 2 :padding "0.7em 1em"
                  :textDecoration "none"}
          :href "javascript:;"
          :onClick (fn [e] ((:onClick props) e))
          :onKeyDown (common/create-key-handler [:space :enter] (:onClick props))}
      (:text props)
      (when (= (:style props) :add)
        [:span {:style {:display "inline-block" :height "1em" :width "1em" :marginLeft "1em"
                        :position "relative"}}
         [:span {:style {:position "absolute" :top "-50%" :fontSize "200%" :fontWeight "normal"}}
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
       {:active-tab 0
        :active-component (:component (first (:items props)))})
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
                   :active? (= i (:active-tab @state))
                   :onClick (fn [e]
                              (swap! state assoc :active-tab i :active-component (:component tab))
                              (when-let [f (:onTabSelected tab)] (f e)))}])
           (:items props))
         [:div {:style {:clear "both"}}]]
        [:div {} (:active-component @state)]])
     }))
