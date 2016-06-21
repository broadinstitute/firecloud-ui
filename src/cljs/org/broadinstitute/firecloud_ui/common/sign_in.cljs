(ns org.broadinstitute.firecloud-ui.common.sign-in
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.components :as comps]
   [org.broadinstitute.firecloud-ui.common.modal :as modal]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.config :as config]
   [org.broadinstitute.firecloud-ui.utils :as u]
   ))


(def handler-fn-name (name ::handle-auth-message))


(react/defc Button
  {:render
   (fn []
     [comps/Button
      {:text "Sign In"
       :onClick (fn [e]
                  (.. js/window
                      (open
                       (str (config/api-url-root) "/login?callback="
                            (js/encodeURIComponent (.. js/window -location -origin)))
                       "Authentication"
                       "menubar=no,toolbar=no,width=500,height=500")))}])
   :component-did-mount
   (fn [{:keys [props]}]
     (aset js/window handler-fn-name
           (fn [message] ((:on-login props) message))))
   :component-will-unmount
   (fn []
     (js-delete js/window handler-fn-name))})


(def ^:private showing-expired-dialog? false)


(react/defc ExpiredDialog
  {:render
   (fn [{:keys [state]}]
     (let [{:keys [success?]} @state]
       [:div {:style {:position "relative"}}
        [:div {:style {:position "absolute" :top 0 :right 0
                       :cursor "pointer" :fontSize "150%" :lineHeight "1ex" :padding 6}
               :onClick modal/pop-modal}
         "×"]
        [:div {:style {:borderBottom style/standard-line
                       :padding "20px 48px 18px"
                       :fontSize "137%" :fontWeight 400 :lineHeight 1}}
         "Session Expired"]
        [:div {:style {:padding "22px 48px 40px" :backgroundColor (:background-gray style/colors)}}
         (if success?
           [:div {:style {:color (:success-green style/colors)}} "Sign-in complete."
            [:div {:style {:marginTop 20 :textAlign "center"}}
             [comps/Button {:text "Close" :onClick modal/pop-modal}]]]
           [:div {}
            "Your session has expired. Please sign-in again to continue."
            [:div {:style {:marginTop 20 :textAlign "center"}}
             [Button {:on-login (fn [message]
                                  (let [token (get message "access_token")]
                                    (reset! u/access-token token)
                                    (u/set-access-token-cookie token)
                                    (swap! state assoc :success? true)))}]]])]]))
   :component-will-mount
   (fn []
     (set! showing-expired-dialog? true))
   :component-will-unmount
   (fn []
     (set! showing-expired-dialog? false))})


(defn show-expired-dialog []
  (when-not showing-expired-dialog?
    (modal/push-modal [ExpiredDialog])))
