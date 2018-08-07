(ns broadfcui.common.icons
  (:require
   [dmohs.react :as react]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))

;; From http://fontawesome.io/icons/
(def icon-keys
  {:add "fa-plus-circle"
   :add-new "fa-plus"
   :alert "fa-exclamation"
   :angle-left "fa-angle-left"
   :angle-right "fa-angle-right"
   :bell "fa-bell-o"
   :cancel "fa-times-circle"
   :catalog "fa-tag"
   :clone "fa-clone"
   :close "fa-close"
   :collapse "fa-minus-square-o"
   :comment "fa-comment"
   :delete "fa-trash"
   :disclosure-closed "fa-caret-right"
   :disclosure-opened "fa-caret-down"
   :done "fa-check"
   :done-circle "fa-check-circle"
   :download "fa-download"
   :edit "fa-pencil"
   :email "fa-envelope-o"
   :error "fa-exclamation-circle"
   :expand "fa-plus-square-o"
   :external-link "fa-external-link"
   :help "fa-question-circle"
   :information "fa-info-circle"
   :library "fa-book"
   :link "fa-link"
   :lock "fa-lock"
   :move-left "fa-angle-double-left"
   :move-right "fa-angle-double-right"
   :new-window "fa-external-link"
   :pause "fa-pause"
   :play "fa-play"
   :public "fa-users"
   :publish "fa-upload"
   :refresh "fa-refresh"
   :remove "fa-minus-circle"
   :reorder "fa-reorder"
   :reset "fa-undo"
   :resize "fa-bars"
   :search "fa-search"
   :settings "fa-cog"
   :share "fa-share-alt"
   :shield "fa-shield"
   :show "fa-eye"
   :sort-asc "fa-sort-amount-asc"
   :sort-desc "fa-sort-amount-desc"
   :spinner "fa-spinner"
   :unknown "fa-question"
   :unlock "fa-unlock"
   :warning "fa-exclamation-triangle"
   :zoom-fit "fa-crosshairs"
   :zoom-in "fa-search-plus"
   :zoom-out "fa-search-minus"
   })

(defn render-icon [attributes key]
  [:span (assoc attributes :className (str (icon-keys key) " fa " (:className attributes)))])

(def fw-icon-width "1.28571429rem")

(defn render-inline-icon [icon]
  (render-icon {:style {:paddingLeft "0.25rem" :fontSize "80%"}} icon))

(def external-link-icon
  (render-inline-icon :external-link))

(def download-icon
  (render-inline-icon :download))

(defn certified-icon
  ([] (certified-icon {}))
  ([attributes]
   [:span (assoc attributes :className (str "fa-stack " (:className attributes)))
    [:i {:className "fa fa-certificate fa-stack-2x" :style {:color (:state-success style/colors)}}]
    [:i {:className "fa fa-check fa-stack-1x" :style {:color "white"}}]]))

(react/defc CompleteIcon
  {:get-default-props
   (fn []
     {:size 32})
   :render
   (fn [{:keys [props]}]
     (render-icon {:style {:color "white" :fontSize (:size props)}} :done-circle))})

(react/defc RunningIcon
  {:get-default-props
   (fn []
     {:color (:state-success style/colors)
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
     {:size 28
      :color "white"})
   :render
   (fn [{:keys [props]}]
     (render-icon {:style {:color (:color props) :fontSize (:size props)}} :warning))})

(react/defc UnknownIcon
  {:get-default-props
   (fn []
     {:size 28
      :color "white"})
   :render
   (fn [{:keys [props]}]
     (render-icon {:style {:color (:color props) :fontSize (:size props)}} :unknown))})
