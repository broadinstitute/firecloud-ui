(ns broadfcui.page.test-login
  (:require
   [dmohs.react :as react]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.foundation-tooltip :as tooltip]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.input :as input]
   [broadfcui.common.style :as style]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   [broadfcui.utils.user :as user]
   ))

(react/defc Page
  {:render
   (fn [{:keys [this refs]}]
    [:div {}
      [:div {}
        "Token:"
        [input/TextField {:ref "force-token"
                          :autoFocus true
                          :type "password"
                          :data-test-id "force-token"
                          :style {:width "85%" :margin "0 1rem"}}]
        [buttons/Button {:text "Force Sign-In" :onClick
        (fn [e]
          (.preventDefault e)
          (let [[token & fails] (input/get-and-validate refs "force-token")
                [target & target-fails] (input/get-and-validate refs "force-token-redirect")]
            (.forceSignedIn js/window token #(aset js/window "location" "hash" target))))}]]
      [:div {:style {:margin-top "1rem"}}
        "Redirect to:"
        [input/TextField {:ref "force-token-redirect"
                          :data-test-id "force-token-redirect"
                          :defaultValue "#methods"
                          :style {:width "33%" :margin "0 1rem"}}]
        [tooltip/FoundationTooltip {:text (icons/render-icon {:style style/secondary-icon-style} :help)
                                    :tooltip (str
                                      "Redirect to this url hash after logging in. To include query parameters"
                                      " in the redirect, reload this #test-login page with those query params,"
                                      " then force sign-in. The query params will be preserved upon redirect.")}]]])})

(defn add-nav-paths []
  (nav/defpath
   :test-login
   {:public? true
    :component Page
    :regex #"test-login"
    :make-props (fn [] {})
    :make-path (fn [] "test-login")}))
