(ns broadfcui.auth
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.config :as config]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   [broadfcui.utils.user :as user]
   ))

(react/defc GoogleAuthLibLoader
  {:render
   (fn []
     [:div {:style {:padding "40px 0"}}
      (spinner "Loading auth...")])
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
       (user/set-google-auth2-instance! auth2)
       (on-loaded auth2)))})

(react/defc- Policy
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:maxWidth 616 :backgroundColor "white"
                    :padding "1.5rem" :margin "1rem auto"
                    :border style/standard-line
                    :fontSize (when (= (:context props) :logged-out) "88%")}}
      (when (= (:context props) :policy-page)
        (list
         [:h3 {} "FireCloud Privacy Policy"]
         [:p {}
          "The following Privacy Policy discloses our information gathering and dissemination
           practices for the Broad Institute FireCloud application accessed via the website "
          (links/create-external {:href "https://portal.firecloud.org/"}
                                 "https://portal.firecloud.org/")
          ". By using the FireCloud, you agree to the collection and use of information in
           accordance with this policy. This Privacy Policy is effective as of 1-19-2017."]
         [:h4 {} "Information Gathering"]
         [:p {}
          "The Broad Institute FireCloud receives and stores information related to users’ Google
           profiles, including names, email addresses, user IDs, and OAuth tokens. This information
           is gathered as part of the standard Google sign-in process."]
         [:p {}
          "We also collect information that your browser sends whenever you visit the FireCloud
           website (“Log Data”).  This Log Data may include information such as your computer’s
           Internet Protocol (“IP”) address, browser type, browser version, which pages of the
           FireCloud Portal that you visit, the time and date of your visit, the time spent on
           individual pages, and other statistics.  This information may include any search terms
           that you enter on the FireCloud (e.g., dataset name, method name, tag labels). We do not
           link IP addresses to any personally identifying information.  User sessions will be
           tracked, but users will remain anonymous."]
         [:p {}
          "In addition, we use web tools such as Google Analytics that collect, monitor, and analyze
           the Log Data.  User information (i.e., name and email address) is not included in our
           Google Analytics tracking, but can be internally linked within the FireCloud development
           team."]
         [:h4 {} "Use of Information"]
         [:p {}
          "FireCloud uses the information gathered above to enable integration with Google-based
           services that require a Google account, such as Google Cloud Storage Platform. We may
           also use this information to provide you with the services on FireCloud, improve
           FireCloud, and to communicate with you (e.g., about new feature announcements, unplanned
           site maintenance, and general notices). Web server logs are retained on a temporary basis
           and then deleted completely from our systems. User information is stored in a
           password-protected database, and OAuth tokens are only stored for the length of an active
           session, are encrypted at rest, and are deleted upon sign out."]
         [:p {}
          "At no time do we disclose any user information to third parties."]
         [:h4 {} "Publicly Uploaded Information"]
         [:p {}
          "Some features of FireCloud are public facing (e.g, publishing a workspace in the Data
           Library) and allow you to upload information (such as new studies) that you may choose to
           make publicly available. If you choose to upload content is public-facing, third parties
           may access and use it. We do not sell any information that you provide to FireCloud; it
           is yours. However, any information that you make publicly available may be accessed and
           used by third parties, such as research organizations or commercial third parties."]
         [:h4 {} "Security"]
         [:p {}
          "This site has security measures in place to prevent the loss, misuse, or alteration of
           the information under our control. It is compliant with NIST-800-53 and has been audited
           as per FISMA Moderate. The Broad Institute, however, is not liable for the loss, misuse,
           or alteration of information on this site by any third party."]
         [:h4 {} "Changes"]
         [:p {}
          "Although most changes are likely to be minor, we may change our Privacy Policy from time
           to time. We will notify you of material changes to this Privacy Policy through the
           FireCloud website at least 30 days before the change takes effect by posting a notice on
           our home page or by sending an email to the email address associated with your user
           account. For changes to this Privacy Policy that do not affect your rights, we encourage
           you to check this page frequently."]
         [:h4 {} "Third Party Sites"]
         [:p {}
          "Some FireCloud pages may link to third party websites or services that are not maintained
           by the Broad Institute. The Broad Institute is not responsible for the privacy practices
           or the content of any such third party websites or services."]
         [:h4 {} "Contacting the FireCloud team"]
         [:p {}
          "If you have any questions about this privacy statement, the practices of this site, or
          your dealings with this site, you can contact us through "
          (links/create-external {:href "mailto:help@firecloud.org"} "help@firecloud.org")
          " or our "
          (links/create-external {:href (config/forum-url)} "User Forum")
          "."]
         [:hr]))
      [:h3 {} "WARNING NOTICE"]
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
      [:h4 {} "WARNING NOTICE (when accessing controlled data)"]
      [:p {:style {:fontWeight "bold"}}
       "You are reminded that when accessing controlled access information you are bound by the
        dbGaP TCGA "
       (links/create-external {:href "http://cancergenome.nih.gov/pdfs/Data_Use_Certv082014"}
                              "DATA USE CERTIFICATION AGREEMENT (DUCA)")
       "."]])})

(react/defc- PolicyPage
  {:render
   (fn []
     [:div {:style {:padding "1rem" :margin -20 :marginTop "1rem"
                    :backgroundColor (:background-light style/colors)}}
      [Policy {:context :policy-page}]])})

(react/defc LoggedOut
  {:render
   (fn [{:keys [this props]}]
     ;; Google's code complains if the sign-in button goes missing, so we hide this component rather
     ;; than removing it from the page.
     [:div {:style {:display (when (:hidden? props) "none") :marginTop "2rem"}}
      [:div {:style {:margin "0 auto" :maxWidth 616}}
       [:div {:style {:textAlign "center"}}
        [buttons/Button {:text "Sign In" :onClick #(this :-handle-sign-in-click)}]]
       [:div {:style {:margin "2rem 0"}}
        [:div {} [:b {} "New user? FireCloud requires a Google account."]]
        [:p {}
         "Please use the \"Sign In\" button above to sign-in with your Google Account.
          Once you have successfully signed-in with Google, you will be taken to the FireCloud
          registration page."]]]
      [Policy {:context :logged-out}]])
   :component-did-mount
   (fn [{:keys [props locals]}]
     (swap! locals assoc :refresh-token-saved? true)
     (let [{:keys [on-change]} props]
       (user/add-user-listener
        ::logged-out
        #(on-change (js-invoke % "isSignedIn") (:refresh-token-saved? @locals)))))
   :component-will-unmount
   (fn []
     (user/remove-user-listener ::logged-out))
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
        nil (spinner "Loading user information...")
        :not-active [:div {:style {:color (:exception-reds style/colors)}}
                     "Thank you for registering. Your account is currently inactive."
                     " You will be contacted via email when your account is activated."]
        [:div {:style {:color (:state-exception style/colors)}}
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

(defn force-signed-in [{:keys [on-sign-in on-sign-out on-error]}]
  (fn [auth-token]
    (utils/ajax {:url (str "https://www.googleapis.com/oauth2/v3/tokeninfo?access_token="
                           (js/encodeURIComponent auth-token))
                 :on-done
                 (fn [{:keys [status-code success? get-parsed-response raw-response]}]
                   (if success?
                     (let [{:keys [email sub]} (get-parsed-response)
                           auth2 (clj->js
                                  {:currentUser
                                   {:get
                                    (constantly
                                     (clj->js
                                      {:getAuthResponse
                                       (constantly (clj->js {:access_token auth-token}))
                                       :getBasicProfile
                                       (constantly (clj->js {:getEmail (constantly email)
                                                             :getId (constantly sub)}))
                                       :hasGrantedScopes (constantly true)}))
                                    :listen (constantly nil)}
                                   :signOut on-sign-out})]
                       (user/set-google-auth2-instance! auth2)
                       (on-sign-in))
                     (on-error {:status status-code :response raw-response})))})))

(defn render-forced-sign-in-error [error]
  [:div {}
   [:div {} "Status: " (:status error)]
   [:div {} "Response: " (:response error)]])

(defn add-nav-paths []
  (nav/defpath
   :policy
   {:public? true
    :component PolicyPage
    :regex #"policy"
    :make-props (fn [_] {})
    :make-path (fn [] "policy")}))
