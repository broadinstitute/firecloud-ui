(ns broadfcui.header
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.style :as style]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
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
  (let [auth2 @utils/auth2-atom]
    (common/render-dropdown-menu
     {:label [:div {:style {:borderRadius 2 :display "inline-block"
                            :backgroundColor (:background-light style/colors)
                            :color "#000" :textDecoration "none"
                            :padding "0.5rem" :border style/standard-line}
                    :data-test-id "account-dropdown"}
              [:span {:data-test-id "account-dropdown-email"} (-> auth2 (.-currentUser) (.get) (.getBasicProfile) (.getEmail))]
              [:div {:style {:display "inline-block" :marginLeft "1em" :fontSize 8}} "▼"]]
      :width :auto
      :button-style {:height 32}
      :items [{:href (nav/get-link :profile) :text "Profile"}
              {:href (nav/get-link :groups) :text "Groups"}
              {:href (nav/get-link :billing) :text "Billing"}
              {:href (nav/get-link :notifications) :text "Notifications"}
              {:text "Sign Out" :dismiss #(.signOut auth2) :data-test-id "sign-out"}]})))
