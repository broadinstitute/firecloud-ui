(ns broadfcui.main
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.config :as config]
   [broadfcui.config.loader :as config-loader]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.footer :as footer]
   [broadfcui.header :as header]
   [broadfcui.nav :as nav]
   [broadfcui.page.billing.billing-management :as billing-management]
   [broadfcui.page.library.library-page :as library-page]
   [broadfcui.page.method-repo.method-repo-page :as method-repo]
   [broadfcui.page.profile :as profile-page]
   [broadfcui.page.status :as status-page]
   [broadfcui.page.workspaces-list :as workspaces]
   [broadfcui.utils :as utils]
   ))


(react/defc Policy
  {:render
    (fn [{:keys [state]}]
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


(def routes
  [{:key :profile
    :render #(react/create-element profile-page/Page %)}
   {:key :billing
    :render #(react/create-element billing-management/Page %)}
   {:key :library :href "#library"
    :name "Data Library"
    :render #(react/create-element library-page/Page %)}
   {:key :workspaces :href "#workspaces"
    :name "Workspaces"
    :render #(react/create-element workspaces/Page %)}
   {:key :methods :href "#methods"
    :name "Method Repository"
    :render #(react/create-element method-repo/Page %)}
   {:key :policy
    :render #(react/create-element Policy %)}])

(react/defc LoggedIn
  {:render
   (fn [{:keys [this props state]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           page (keyword (:segment nav-context))]
       (when-not (or (contains? (set (map :key routes)) page)
                     (= page :status))
         (nav/navigate (:nav-context props) "workspaces"))
       [:div {}
        [:div {:style {:width "100%" :borderBottom (str "1px solid " (:line-default style/colors))}}
         [:div {:style {:float "right" :fontSize "70%" :margin "0 0 0.5em 0"}}
          [header/AccountDropdown {:auth2 (:auth2 props)}]
          (common/question-icon-link "FireCloud User Guide" (config/user-guide-url) {:display "block" :float "right"})
          (common/clear-both)
          (when (= :registered (:registration-status @state))
            [header/GlobalSubmissionStatus])]
         (when (= :registered (:registration-status @state))
           [header/TopNavBar {:routes routes
                              :selected-item page
                              :show-nih-link-warning? (not (contains? #{:status :profile} page))}])
         (common/clear-both)]
        (case (:registration-status @state)
          nil [:div {:style {:margin "2em 0" :textAlign "center"}}
               [comps/Spinner {:text "Loading user information..."}]]
          :error [:div {:style {:margin "2em 0"}}
                  (style/create-server-error-message (.-errorMessage this))]
          :not-registered (profile-page/render
                           {:new-registration? true
                            :on-done #(.. js/window -location (reload))})
          :update-registered (profile-page/render
                              {:update-registration? true
                               :on-done #(.. js/window -location (reload))})
          :registered
          (if (and (= page :status) (config/debug?))
            (status-page/render)
            [:div {}
             (let [item (first (filter #(= (% :key) page) routes))]
               (if item
                 ((item :render) {:nav-context nav-context})
                 [:div {} "Page not found."]))]))]))
   :component-did-mount
   (fn [{:keys [this state]}]
     (when (nil? (:registration-status @state))
       (endpoints/profile-get
        (fn [{:keys [success? status-text get-parsed-response]}]
          (let [parsed-values (when success? (common/parse-profile (get-parsed-response false)))]
            (cond
              (and success? (>= (int (:isRegistrationComplete parsed-values)) 3))
              (swap! state assoc :registration-status :registered)
              (and success? (some? (:isRegistrationComplete parsed-values))) ; partial profile case
              (swap! state assoc :registration-status :update-registered)
              success? ; unregistered case
              (swap! state assoc :registration-status :not-registered)
              :else
              (do
                (set! (.-errorMessage this) status-text)
                (swap! state assoc :registration-status :error))))))))})


(react/defc LoggedOut
  {:render
   (fn [{:keys [this props]}]
     ;; Google's code complains if the sign-in button goes missing, so we hide this component rather
     ;; than removing it from the page.
     [:div {:style {:display (when (:hidden? props) "none")}}
      [:div {:style {:marginBottom "2em"}} (style/render-text-logo)]
      [comps/Button {:text "Sign In" :onClick #(react/call :.handle-sign-in-click this)}]
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
   (fn [{:keys [props locals]}]
     (utils/remove-user-listener ::logged-out))
   :.handle-sign-in-click
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


(defn- show-system-status-dialog [maintenance-mode?]
  (comps/push-ok-cancel-modal
    {:header (if maintenance-mode? "Maintenance Mode" "Server Unavailable")
     :show-cancel? false
     :content (if maintenance-mode?
                [:div {:style {:width 500}} "FireCloud is currently undergoing planned maintenance.
                   We should be back online shortly. For more information, please see "
                 [:a {:href "http://status.firecloud.org/" :target "_blank"}
                  "http://status.firecloud.org/"] "."]
                [:div {:style {:width 500}} "FireCloud service is temporarily unavailable.  If this problem persists, check "
                 [:a {:href "http://status.firecloud.org/" :target "_blank"}
                  "http://status.firecloud.org/"]
                 " for more information."])}))


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
      " " [:a {:href "javascript:;" :onClick #(react/call :.re-auth this)} "Refresh now..."]])
   :component-did-mount
   (fn [{:keys [state]}]
     (utils/ajax-orch "/refresh-token-status"
                      {:on-done (fn [{:keys [raw-response]}]
                                  (let [[parsed _]
                                        (utils/parse-json-string raw-response true false)]
                                    (when (and parsed (:requiresRefresh parsed))
                                      (swap! state dissoc :hidden?))))}))
   :.re-auth
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


(react/defc App
  {:handle-hash-change
   (fn [{:keys [state]}]
     (swap! state assoc :root-nav-context (nav/create-nav-context)))
   :get-initial-state
   (fn []
     {:root-nav-context (nav/create-nav-context)
      :user-status #{}})
   :render
   (fn [{:keys [this state]}]
     [:div {}
      (when (and (contains? (:user-status @state) :signed-in)
                 (contains? (:user-status @state) :refresh-token-saved))
        [RefreshCredentials {:auth2 (:auth2 @state)}])
      [:div {:style {:backgroundColor "white" :padding 20}}
       [:div {}
        (when-let [auth2 (:auth2 @state)]
          [LoggedOut {:auth2 auth2 :hidden? (contains? (:user-status @state) :signed-in)
                      :on-change (fn [signed-in? token-saved?]
                                   (swap! state update :user-status
                                          #(-> %
                                               ((if signed-in? conj disj)
                                                :signed-in)
                                               ((if token-saved? conj disj)
                                                :refresh-token-saved))))}])
        (cond
          (not (:config-loaded? @state))
          [config-loader/Component
           {:on-success #(do (swap! state assoc :config-loaded? true) (this :-initialize-auth2))}]
          (nil? (:auth2 @state))
          [:div {}
           (style/render-text-logo)
           [:div {:style {:padding "40px 0"}}
            [comps/Spinner {:text "Loading auth..."}]]]
          (contains? (:user-status @state) :signed-in)
          (cond
            (not (contains? (:user-status @state) :go))
            [UserStatus {:on-success #(swap! state update :user-status conj :go)}]
            :else [LoggedIn {:nav-context (:root-nav-context @state)
                             :auth2 (:auth2 @state)}])
          (contains? (:user-status @state) :signed-in)
          [LoggedIn {:nav-context (:root-nav-context @state)
                     :auth2 (:auth2 @state)}])]]
      (footer/render-footer)
      ;; As low as possible on the page so it will be the frontmost component when displayed.
      [modal/Component {:ref "modal"}]])
   :component-did-mount
   (fn [{:keys [this state refs locals]}]
     ;; pop up the message only when we start getting 503s, not on every 503
     (add-watch
      utils/server-down? :server-watcher
      (fn [_ _ _ down-now?]
        (when down-now?
          (show-system-status-dialog false))))
     (add-watch
      utils/maintenance-mode? :server-watcher
      (fn [_ _ _ maintenance-now?]
        (when maintenance-now?
          (show-system-status-dialog true))))
     (modal/set-instance! (@refs "modal"))
     (swap! locals assoc :hash-change-listener (partial react/call :handle-hash-change this))
     (.addEventListener js/window "hashchange" (:hash-change-listener @locals)))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (.removeEventListener js/window "hashchange" (:hash-change-listener @locals))
     (remove-watch utils/server-down? :server-watcher)
     (remove-watch utils/maintenance-mode? :server-watcher))
   :-initialize-auth2
   (fn [{:keys [state]}]
     (let [scopes (clojure.string/join
                   " "
                   ["email" "profile"
                    "https://www.googleapis.com/auth/devstorage.full_control"
                    "https://www.googleapis.com/auth/compute"])]
       (.. js/gapi
           (load "auth2"
                 (fn []
                   (let [auth2 (.. js/gapi -auth2
                                   (init (clj->js
                                          {:client_id (config/google-client-id)
                                           :scope scopes})))]
                     (swap! state assoc :auth2 auth2)
                     (utils/set-google-auth2-instance! auth2)))))))})


(defn render-application []
  (react/render (react/create-element App) (utils/get-app-root-element)))


(render-application)
