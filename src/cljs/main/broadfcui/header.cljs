(ns broadfcui.header
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.style :as style]
   [broadfcui.components.foundation-dropdown :as dropdown]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   [broadfcui.utils.user :as user]
   ))

(react/defc- TopNavBarLink
  {:render
   (fn [{:keys [props state]}]
     [:a {:href (:href props)
          :style {:padding "1em" :textDecoration "none"
                  :fontWeight (when (:selected props) "bold")
                  :color (if (:hovering? @state) (:button-primary style/colors) "black")}
          :data-test-id (:data-test-id props)
          :onMouseOver #(swap! state assoc :hovering? true)
          :onMouseOut #(swap! state assoc :hovering? false)}
      (:name props)])})

(react/defc TopNavBar
  {:render
   (fn [{:keys [props]}]
     [:div {}
      (style/render-text-logo)
      [:div {:style {:display "inline-block" :paddingLeft "1em" :fontSize 18 :height 38
                     :verticalAlign "baseline"}}
       (map (fn [item]
              [TopNavBarLink {:name (:label item) :href (nav/get-link (:nav-key item))
                              :selected ((:is-selected? item))
                              :data-test-id (:data-test-id item)}])
            (:items props))]])})

(defn create-account-dropdown []
  (let [sign-out-item {:text "Sign Out" :dismiss #(.signOut @user/auth2-atom) :data-test-id "sign-out"}]
    (dropdown/render-dropdown-menu
     {:label [:div {:style {:borderRadius 2 :display "inline-block"
                            :backgroundColor (:background-light style/colors)
                            :color "#000" :textDecoration "none"
                            :padding "0.5rem" :border style/standard-line}
                    :data-test-id "account-dropdown"}
              [:span {:data-test-id "account-dropdown-email"} (user/get-email)]
              [:div {:style {:display "inline-block" :marginLeft "1em" :fontSize 8}} "▼"]]
      :width :auto
      :button-style {:height 32}
      :items (if common/has-return?
               [sign-out-item]
               [{:href (nav/get-link :profile) :text "Profile"}
                {:href (nav/get-link :groups) :text "Groups"}
                {:href (nav/get-link :notifications) :text "Notifications"}
                sign-out-item])})))
