(ns broadfcui.common.icons
  (:require
    [dmohs.react :as react]
    [broadfcui.common.style :as style]
    [broadfcui.utils :as utils]
    ))

;; From https://design.google.com/icons/
(def ^:private icon-keys
  {:angle-left "fa-angle-left"
   :angle-right "fa-angle-right"
   :settings "fa-cog"
   :lock "fa-lock"
   :unlock "fa-unlock"
   :edit "fa-pencil"
   :clone "fa-clone"
   :add "fa-plus"
   :delete "fa-trash"
   :remove "fa-minus-circle"
   :done "fa-check"
   :done-circle "fa-check-circle"
   :cancel "fa-times-circle"
   :warning-triangle "fa-exclamation-triangle"
   :error "fa-exclamation-circle"
   :alert "fa-hand-paper-o"
   :search "fa-search"
   :share "fa-share-alt"
   :library "fa-book"
   :publish "fa-upload"
   :catalog "fa-tag"
   :reorder "fa-reorder"
   :close "fa-close"
   :reset "fa-undo"
   :new-window "fa-external-link"})

(defn icon [attributes key]
  [:span (utils/deep-merge
          {:className (str "fa " (icon-keys key))}
           attributes)])

(react/defc CompleteIcon
  {:get-default-props
   (fn []
     {:size 32})
   :render
   (fn [{:keys [props]}]
     (icon {:style {:color "white" :fontSize (:size props)}} :done-circle))})

(react/defc RunningIcon
  {:get-default-props
   (fn []
     {:color (:success-state style/colors)
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
     {:size 28})
   :render
   (fn [{:keys [props]}]
     (icon {:style {:color "white" :fontSize (:size props)}} :warning-triangle))})
