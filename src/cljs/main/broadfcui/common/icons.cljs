(ns broadfcui.common.icons
  (:require
    [dmohs.react :as react]
    [broadfcui.common.style :as style]
    [broadfcui.utils :as utils]
    ))

;; From https://design.google.com/icons/
(def ^:private icon-keys
  {:angle-left "keyboard_arrow_left"
   :angle-right "keyboard_arrow_right"
   :settings "settings"
   :lock "lock"
   :unlock "lock_open"
   :edit "mode_edit"
   :clone "content_copy"
   :add "add"
   :delete "delete"
   :remove "remove_circle"
   :done "done"
   :done-circle "check_circle"
   :cancel "cancel"
   :warning-triangle "warning"
   :error "error"
   :search "search"
   :share "share"
   :library "import_contacts"
   :publish "publish"
   :catalog "local_offer"
   :reorder "reorder"
   :close "close"
   :reset "replay"
   :new-window "open_in_new"})

(defn icon [attributes key]
  [:span (utils/deep-merge
           {:className "material-icons"}
           attributes)
   (icon-keys key)])

(react/defc CompleteIcon
  {:get-default-props
   (fn []
     {:size 36})
   :render
   (fn [{:keys [props]}]
     [:span {:style {:display "inline-block" :position "relative" :verticalAlign "middle"
                     :width (:size props) :height (:size props)}}
      (style/center {}
        (icon {:style {:color "white" :fontSize (:size props) :marginTop 4}} :done-circle))])})

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
     {:size 36})
   :render
   (fn [{:keys [props]}]
     [:span {:style {:display "inline-block" :position "relative" :verticalAlign "middle"
                     :height (:size props) :width (:size props)}}
      (style/center {} (icon {:style {:color "white" :fontSize (:size props) :marginTop 4}} :warning-triangle))])})
