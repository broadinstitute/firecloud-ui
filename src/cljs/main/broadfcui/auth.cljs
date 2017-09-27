(ns broadfcui.auth
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.config :as config]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   ))

(react/defc GoogleAuthLibLoader
  {:render
   (fn []
     [:div {:style {:padding "40px 0"}}
      [comps/Spinner {:text "Loading auth..."}]])
   :component-did-mount
   (fn [{:keys [this]}]
     (js-invoke js/gapi "load" "auth2" #(this :-handle-auth2-loaded)))
   :-handle-auth2-loaded
   (fn [{:keys [props]}]
     (let [{:keys [on-loaded]} props
           scopes (string/join
                   " "
                   ["email" "profile"
                    "https://www.googleapis.com/auth/devstorage.full_control"
                    "https://www.googleapis.com/auth/compute"])
           init-options (clj->js {:client_id (config/google-client-id) :scope scopes})
           auth2 (js-invoke (aget js/gapi "auth2") "init" init-options)]
       (utils/set-google-auth2-instance! auth2)
       (on-loaded auth2)))})

(react/defc- Policy
  {:render
   (fn []
     [:div {:style {:maxWidth 600 :paddingTop "2em"}}
      [:p {:style {:fontWeight "bold"}} "WARNING NOTICE"]
      [:p {}
       "You are accessing a US Government web site which may contain information that must be
          protected under the US Privacy Act or other sensitive information and is intended for
          Government authorized use only."]
      [:p {}
       "Unauthorized attempts to upload information, change information, or use of this web site
          may result in disciplinary action, civil, and/or criminal penalties. Unauthorized users
          of this website should have no expectation of privacy regarding any communications or
          data processed by this website."]
      [:p {}
       "Anyone accessing this website expressly consents to monitoring of their actions and all
          communications or data transiting or stored on related to this website and is advised
          that if such monitoring reveals possible evidence of criminal activity, NIH may provide
          that evidence to law enforcement officials."]
      [:p {:style {:fontWeight "bold"}} "WARNING NOTICE (when accessing controlled data)"]
      [:p {:style {:fontWeight "bold"}}
       "You are reminded that when accessing controlled access information you are bound by the
          dbGaP TCGA "
       [:a {:href "http://cancergenome.nih.gov/pdfs/Data_Use_Certv082014" :target "_blank"}
        "DATA USE CERTIFICATION AGREEMENT (DUCA)"] "."]])})

(react/defc LoggedOut
  {:render
   (fn [{:keys [this props]}]
     ;; Google's code complains if the sign-in button goes missing, so we hide this component rather
     ;; than removing it from the page.
     [:div {:style {:display (when (:hidden? props) "none") :marginTop "2rem"}}
      [buttons/Button {:text "Sign In" :onClick #(this :-handle-sign-in-click)}]
      [:div {:style {:marginTop "2em" :maxWidth 600}}
       [:div {} [:b {} "New user? FireCloud requires a Google account."]]
       [:div {} "Please use the \"Sign In\" button above to sign-in with your Google Account.
         Once you have successfully signed-in with Google, you will be taken to the FireCloud
         registration page."]]
      [:div {:style {:maxWidth 600 :paddingTop "2em" :fontSize "small"}}
       [Policy]]])
   :component-did-mount
   (fn [{:keys [props locals]}]
     (swap! locals assoc :refresh-token-saved? true)
     (let [{:keys [on-change]} props]
       (utils/add-user-listener
        ::logged-out
        #(on-change (js-invoke % "isSignedIn") (:refresh-token-saved? @locals)))))
   :component-will-unmount
   (fn []
     (utils/remove-user-listener ::logged-out))
   :-handle-sign-in-click
   (fn [{:keys [props locals]}]
     (swap! locals dissoc :refresh-token-saved?)
     (let [{:keys [auth2 on-change]} props]
       (-> auth2
           (.grantOfflineAccess (clj->js {:redirect_uri "postmessage"
                                          :prompt "select_account"}))
           (.then (fn [response]
                    (utils/ajax {:url (str (config/api-url-root) "/handle-oauth-code")
                                 :method "POST"
                                 :data (utils/->json-string
                                        {:code (.-code response)
                                         :redirectUri (.. js/window -location -origin)})
                                 :on-done (fn [{:keys [success?]}]
                                            (when success?
                                              (swap! locals assoc :refresh-token-saved? true)
                                              (let [signed-in? (-> auth2
                                                                   (aget "currentUser")
                                                                   (js-invoke "get")
                                                                   (js-invoke "isSignedIn"))]
                                                (on-change signed-in? true))))}))))))})

(react/defc UserStatus
  {:render
   (fn [{:keys [state]}]
     [:div {:style {:padding "40px 0"}}
      (case (:error @state)
        nil [comps/Spinner {:text "Loading user information..."}]
        :not-active [:div {:style {:color (:exception-reds style/colors)}}
                     "Thank you for registering. Your account is currently inactive."
                     " You will be contacted via email when your account is activated."]
        [:div {:style {:color (:exception-state style/colors)}}
         "Error loading user information. Please try again later."])])
   :component-did-mount
   (fn [{:keys [props state]}]
     (utils/ajax-orch "/me"
                      {:on-done (fn [{:keys [success? status-code]}]
                                  (if success?
                                    ((:on-success props))
                                    (case status-code
                                      403 (swap! state assoc :error :not-active)
                                      ;; 404 means "not yet registered"
                                      404 ((:on-success props))
                                      (swap! state assoc :error true))))}
                      :service-prefix ""))})

(react/defc RefreshCredentials
  {:get-initial-state
   (fn []
     {:hidden? true})
   :render
   (fn [{:keys [this state]}]
     [:div {:style {:display (when (:hidden? @state) "none")
                    :padding "1ex 1em" :backgroundColor "#fda" :color "#530" :fontSize "80%"}}
      "Your offline credentials are missing or out-of-date."
      " Your workflows may not run correctly until they have been refreshed."
      " " [:a {:href "javascript:;" :onClick #(this :-re-auth)} "Refresh now..."]])
   :component-did-mount
   (fn [{:keys [state]}]
     (utils/ajax-orch "/refresh-token-status"
                      {:on-done (fn [{:keys [raw-response]}]
                                  (let [[parsed _]
                                        (utils/parse-json-string raw-response true false)]
                                    (when (and parsed (:requiresRefresh parsed))
                                      (swap! state dissoc :hidden?))))}))
   :-re-auth
   (fn [{:keys [props state]}]
     (-> (:auth2 props)
         (.grantOfflineAccess (clj->js {:redirect_uri "postmessage"
                                        :prompt "consent"}))
         (.then (fn [response]
                  (utils/ajax {:url (str (config/api-url-root) "/handle-oauth-code")
                               :method "POST"
                               :data (utils/->json-string
                                      {:code (.-code response)
                                       :redirectUri (.. js/window -location -origin)})
                               :on-done #(swap! state assoc :hidden? true)})))))})

(defn add-nav-paths []
  (nav/defpath
   :policy
   {:public? true
    :component Policy
    :regex #"policy"
    :make-props (fn [_] {})
    :make-path (fn [] "policy")}))
