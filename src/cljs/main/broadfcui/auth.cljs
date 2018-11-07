(ns broadfcui.auth
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :refer [login-scopes]]
   [broadfcui.common.links :as links]
   [broadfcui.common.markdown :as markdown]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.page.external-importer :as external-importer]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   [broadfcui.utils.user :as user]
   ))


(react/defc GoogleAuthLibLoader
  {:render
   (constantly nil)
   :component-did-mount
   (fn [{:keys [this]}]
     (js/gapi.load "auth2" #(this :-handle-auth2-loaded)))
   :-handle-auth2-loaded
   (fn [{:keys [props]}]
     ;; NB: we do not override the fetch_basic_profile config option on auth2.init.
     ;; fetch_basic_profile defaults to true, and adds "openid email profile" to the
     ;; list of requested scopes.
     (let [{:keys [on-loaded]} props
           scopes (string/join
                   " "
                   login-scopes)
           init-options (clj->js {:client_id (config/google-client-id) :scope scopes})
           auth2 (js/gapi.auth2.init init-options)]
       (gapi.signin2.render "sign-in-button" #js{:width 180 :height 40 :longtitle true :theme "dark"})
       (user/set-google-auth2-instance! auth2)
       (on-loaded auth2)))})

(react/defc- Policy
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:maxWidth 716 :backgroundColor "white"
                    :margin "2.5rem auto 0"
                    :fontSize (when (= (:context props) :logged-out) "88%")}}
      (when (= (:context props) :policy-page)
        (list
         [:h4 {} "FireCloud Privacy Policy"]
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
          your dealings with this site, you can contact us through our "
          (links/create-external {:href (config/forum-url)} "help forum")
          "."]
         [:hr]))
      [:h4 {} "WARNING NOTICE"]
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
      [:h4 {} "WARNING NOTICE (when accessing TCGA controlled data)"]
      [:p {:style {:fontWeight "bold"}}
       "You are reminded that when accessing TCGA controlled access information you are bound by the
        dbGaP TCGA "
       (links/create-external {:href "http://cancergenome.nih.gov/pdfs/Data_Use_Certv082014"}
         "DATA USE CERTIFICATION AGREEMENT (DUCA)")
       "."]])})

(react/defc- PolicyPage
  {:render
   (fn []
     [:div {:style {:padding "1rem" :margin -20 :marginTop "1rem"
                    :backgroundColor "white"}}
      [Policy {:context :policy-page}]])})

(react/defc LoggedOut
  {:render
   (fn [{:keys [this props]}]
     (let [import-page? (string/starts-with? js/document.location.hash "#import")]
       ;; Google's code complains if the sign-in button goes missing, so we hide this component rather
       ;; than removing it from the page.
       [:div {:style {:display (when (:hidden? props) "none") :marginTop "2rem"}}
        [:div {:style {:margin "0 auto" :maxWidth 716}}
         [:h1 {:style {:marginBottom "0.3rem" :fontWeight 400}}
          (if import-page? external-importer/import-title "New User?")]
         [:div {:style {:marginBottom "1.5rem"}}
          (if import-page? external-importer/import-subtitle "FireCloud requires a Google account.")]
         [:div {:style {:display "flex"}}
          [:div {:style {:paddingRight "2rem" :borderRight style/dark-line}}
           (if import-page?
             (external-importer/render-import-tutorial)
             [:div {:style {:lineHeight "130%"}}
              "Need to create a FireCloud account? FireCloud uses your Google account. Once you have
               signed in and completed the user profile registration step, you can start using FireCloud."
              (links/create-external {:style {:display "block" :marginTop "0.3rem"}
                                      :href "https://software.broadinstitute.org/firecloud/documentation/article?id=9846"}
                "Learn how to create a Google account with any email address.")])]
          [:div {:id "sign-in-button"
                 :style {:flexShrink 0 :width 180 :paddingLeft "2rem" :alignSelf "center"}
                 :onClick #(this :-handle-sign-in-click)}
           (spinner (:spinner-text props))]]]
        [Policy {:context :logged-out}]]))
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
                    (ajax/call {:url (str (config/api-url-root) "/handle-oauth-code")
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

;; Borked servers often return HTML pages instead of JSON, so suppress JSON parsing
;; exceptions because they are useless ("Unexpected token T in JSON...")
(defn- handle-server-error [status-code get-parsed-response]
  (let [[_ parsing-error] (get-parsed-response true)]
    (if (= 0 status-code)
      ;; status code 0 typically happens when CORS preflight fails/rejects
      {:message "Ajax error."
       :statusCode 0}
      (if parsing-error
        {:message (str "Cannot reach the API server. The API server or one of its subsystems may be down.")
         :statusCode status-code}
        {:message (get-parsed-response)
         :statusCode status-code}))))

(react/defc UserStatus
  {:render
   (fn [{:keys [state]}]
     [:div {:style {:padding "40px 0"}}
      (case (:error @state)
        nil (spinner "Loading user information...")
        :not-active [:div {:style {:color (:state-exception style/colors)}}
                     "Thank you for registering. Your account is currently inactive."
                     " You will be contacted via email when your account is activated."]
        [:div {}
         [:div {:style {:color (:state-exception style/colors) :paddingBottom "1rem"}}
          "Error loading user information. Please try again later."]
         [:table {:style {:color (:text-lighter style/colors)}}
          [:tbody {:style {}}
           [:tr {} [:td {:style {:fontStyle "italic" :textAlign "right" :paddingRight "0.3rem"}} "What went wrong:"] [:td {} (:message (:error @state))]]
           [:tr {} [:td {:style {:fontStyle "italic" :textAlign "right" :paddingRight "0.3rem"}} "Status code:"] [:td {} (:statusCode (:error @state))]]]]])])
   :component-did-mount
   (fn [{:keys [props state]}]
     (let [{:keys [on-success]} props]
       (ajax/call-orch "/me?userDetailsOnly=true"
                       {:on-done (fn [{:keys [success? status-code get-parsed-response]}]
                                   (if success?
                                     (on-success)
                                     (case status-code
                                       403 (swap! state assoc :error :not-active)
                                       ;; 404 means "not yet registered"
                                       404 (on-success)
                                       (swap! state assoc :error (handle-server-error status-code get-parsed-response)))))}
                       :service-prefix "")))})

(react/defc TermsOfService
  {:render
   (fn [{:keys [state this]}]
     (let [{:keys [error tos]} @state
           update-status #(this :-get-status)]
       [:div {}
        (links/create-internal {:style {:position "absolute" :right "1rem" :top "1rem"}
                                :onClick #(.signOut @user/auth2-atom)}
          "Sign Out")
        (case error
          nil (spinner "Loading Terms of Service information...")
          (:declined :not-agreed) [:div {:style {:padding "2rem" :margin "5rem auto" :maxWidth 600
                                                 :border style/standard-line}}
                                   [:h2 {:style {:marginTop 0}} "You must accept the Terms of Service to use FireCloud."]
                                   (if tos
                                     [:div {:style {:display "flex" :flexDirection "column" :alignItems "center"}}
                                      [markdown/MarkdownView {:text tos}]
                                      [:div {:style {:display "flex" :width 200 :justifyContent "space-evenly" :marginTop "1rem"}}
                                       [buttons/Button {:text "Accept" :onClick #(endpoints/tos-set-status true update-status)}]]]
                                     (spinner "Loading Terms of Service..."))]
          [:div {}
           [:div {:style {:color (:state-exception style/colors) :paddingBottom "1rem"}}
            "Error loading Terms of Service information. Please try again later."]
           [:table {:style {:color (:text-lighter style/colors)}}
            [:tbody {:style {}}
             [:tr {} [:td {:style {:fontStyle "italic" :textAlign "right" :paddingRight "0.3rem"}} "What went wrong:"] [:td {} (:message error)]]
             [:tr {} [:td {:style {:fontStyle "italic" :textAlign "right" :paddingRight "0.3rem"}} "Status code:"] [:td {} (:statusCode error)]]]]])]))
   :component-did-mount
   (fn [{:keys [state this]}]
     (ajax/get-google-bucket-file "tos" #(swap! state assoc :tos (% (-> (config/tos-version) str keyword))))
     (this :-get-status))
   :-get-status
   (fn [{:keys [props state]}]
     (let [{:keys [on-success]} props]
       (endpoints/tos-get-status
        (fn [{:keys [success? status-code get-parsed-response]}]
          (if success?
            (on-success)
            (case status-code
              ;; 403 means the user declined the TOS (or has invalid token? Need to distinguish)
              403 (swap! state assoc :error :declined)
              ;; 404 means the user hasn't seen the TOS yet and must agree (or url is wrong? need to distinguish)
              404 (swap! state assoc :error :not-agreed)
              (swap! state assoc :error (handle-server-error status-code get-parsed-response))))))))})

(defn reject-tos [on-done] (endpoints/tos-set-status false on-done))

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
     (ajax/call-orch "/refresh-token-status"
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
                  (ajax/call {:url (str (config/api-url-root) "/handle-oauth-code")
                              :method "POST"
                              :data (utils/->json-string
                                     {:code (.-code response)
                                      :redirectUri (.. js/window -location -origin)})
                              :on-done #(swap! state assoc :hidden? true)})))))})

(defn force-signed-in [{:keys [on-sign-in on-sign-out on-error]}]
  (fn [auth-token]
    (ajax/call {:url (str "https://www.googleapis.com/oauth2/v3/tokeninfo?access_token="
                          (js/encodeURIComponent auth-token))
                :on-done
                (fn [{:keys [status-code success? get-parsed-response raw-response]}]
                  ;; use console.warn to make sure logs are captured by selenium
                  (js/console.warn (str "force-signed-in: <" success? "> " raw-response))
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
