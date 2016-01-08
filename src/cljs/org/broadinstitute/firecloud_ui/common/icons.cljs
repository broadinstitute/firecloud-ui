(ns org.broadinstitute.firecloud-ui.common.icons
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

(def ^:private icon-keys {:activity ""
                          :angle-left ""
                          :angle-right ""
                          :document ""
                          :gear ""
                          :information ""
                          :locked ""
                          :pencil ""
                          :plus ""
                          :remove ""
                          :search ""
                          :share ""
                          :status-done ""
                          :status-warning ""
                          :status-warning-triangle ""
                          :trash-can ""
                          :view-mode-list ""
                          :view-mode-tiles ""
                          :x ""})

(defn font-icon [props key]
  [:span (utils/deep-merge {:style {:fontFamily "fontIcons"}} props) (key icon-keys)])

(defn icon-text [key] (key icon-keys))

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
                     :backgroundColor "#fff" :borderRadius "100%"}}
      (style/center {}
        (font-icon {:style {:color (:color props) :fontSize (int (* 0.5 (:size props)))}}
          :status-done))])})

(react/defc RunningIcon
  {:get-default-props
   (fn []
     {:color (:success-green style/colors)
      :size 24})
   :render
   (fn [{:keys [props]}]
     (let [hamburger-height (int (/ (:size props) 6))
           spacer-height (int (/ (- (:size props) 4 (* 3 hamburger-height)) 2))
           hamburger (fn [color] [:div {:style {:height hamburger-height
                                                :width (:size props)
                                                :borderRadius hamburger-height
                                                :backgroundColor color}}])
           spacer [:div {:style {:height spacer-height}}]]
       [:span {:style {:display "inline-block" :position "relative" :verticalAlign "middle"
                       :height (:size props) :width (:size props)}}
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
     [:span {:style {:display "inline-block" :position "relative" :verticalAlign "middle"
                     :height (:size props) :width (:size props)}}
      (style/center {}
        (font-icon {:style {:color "#fff" :fontSize (:size props)}} :status-warning))])})
