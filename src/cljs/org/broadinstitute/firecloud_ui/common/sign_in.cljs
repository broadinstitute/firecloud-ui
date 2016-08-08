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
(def flow-start-location-hash "#sign-in")


(react/defc Button
  {:render
   (fn []
     [comps/Button
      {:text "Sign In"
       :onClick (fn [e]
                  ;; Note: window.open must fire as a direct result of this click to prevent
                  ;; browsers from automatically blocking the pop-up.
                  (.. js/window
                      (open
                       (str (.. js/window -location -origin) "/" flow-start-location-hash)
                       "Authentication"
                       "menubar=no,toolbar=no,width=500,height=500")))}])
   :component-did-mount
   (fn [{:keys [props]}]
     (aset js/window handler-fn-name
           (fn [message] ((:on-login props) message))))
   :component-will-unmount
   (fn []
     (js-delete js/window handler-fn-name))})


(def ^:private showing-dialog? false)


(def ^:private dialog-keys
  {:expired {:title "Session Expired"
             :message "Your session has expired. Please sign-in again to continue."}
   :refresh-token {:title "Refresh Token Expired"
                   :message "Your refresh token has expired. Please sign-in again to acquire a new refresh token."}})


(react/defc SignInDialog
  {:render
   (fn [{:keys [state props]}]
     (let [{:keys [success?]} @state]
       [:div {:style {:position "relative"}}
        [:div {:style {:position "absolute" :top 0 :right 0
                       :cursor "pointer" :fontSize "150%" :lineHeight "1ex" :padding 6}
               :onClick modal/pop-modal}
         "×"]
        [:div {:style {:borderBottom style/standard-line
                       :padding "20px 48px 18px"
                       :fontSize "137%" :fontWeight 400 :lineHeight 1}}
         (:title props)]
        [:div {:style {:padding "22px 48px 40px" :backgroundColor (:background-gray style/colors)}}
         (if success?
           [:div {:style {:color (:success-green style/colors)}} "Sign-in complete."
            [:div {:style {:marginTop 20 :textAlign "center"}}
             [comps/Button {:text "Close" :onClick modal/pop-modal}]]]
           [:div {}
            (:message props)
            [:div {:style {:marginTop 20 :textAlign "center"}}
             [Button {:on-login (fn [message]
                                  (let [token (get message "access_token")]
                                    (reset! u/access-token token)
                                    (u/set-access-token-cookie token)
                                    (swap! state assoc :success? true)
                                    (when-let [callback (:callback props)]
                                      (callback))))}]]])]]))
   :component-did-update
   (fn [{:keys [state]}]
     (let [{:keys [success?]} @state]
       (when success?
         (modal/pop-modal))))
   :component-will-mount
   (fn []
     (set! showing-dialog? true))
   :component-will-unmount
   (fn []
     (set! showing-dialog? false))})


(defn show-sign-in-dialog [key & [callback]]
  (when-not showing-dialog?
    (modal/push-modal [SignInDialog (merge (dialog-keys key) {:callback callback})])))
