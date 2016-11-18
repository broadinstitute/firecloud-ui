(ns org.broadinstitute.firecloud-ui.main
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.components :as comps]
   [org.broadinstitute.firecloud-ui.common.modal :as modal]
   [org.broadinstitute.firecloud-ui.common.sign-in :as sign-in]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.config :as config]
   [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
   [org.broadinstitute.firecloud-ui.nav :as nav]
   [org.broadinstitute.firecloud-ui.nih-link-warning :as nih-link-warning]
   [org.broadinstitute.firecloud-ui.page.billing.billing-management :as billing-management]
   [org.broadinstitute.firecloud-ui.page.method-repo.method-repo-page :as method-repo]
   [org.broadinstitute.firecloud-ui.page.profile :as profile-page]
   [org.broadinstitute.firecloud-ui.page.status :as status-page]
   [org.broadinstitute.firecloud-ui.page.workspaces-list :as workspaces]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))


(defn- logo []
  [:img {:src "assets/broad_logo.png" :style {:height 38}}])

; Temporary replacement for the Broad Logo.
(defn- text-logo []
  [:div {:style {:display "inline-block"}}
   [:a {:href "/#workspaces" :style {:fontSize 32 :color (:button-primary style/colors) :fontWeight "bold" :textDecoration "none" :height 38}}
   "FireCloud"]])


(react/defc PopUpFooterControl
  {:render
   (fn [{:keys [state]}]
     [:div {:style {:minWidth 50 :minHeight 20}
            :onMouseOver #(swap! state assoc :visible? true)
            :onMouseOut #(swap! state dissoc :visible?)}
      [:div {:style {:display (when-not (or (:visible? @state) (not @utils/use-live-data?)) "none")
                     :padding 20 :paddingBottom 10 :margin -20 :marginBottom -10}}
       [:div {}
        "Fake data: "
        [:a {:href "javascript:;"
             :style {:textDecoration "none" :color (if @utils/use-live-data? "green" "red")}
             :onClick #(do (swap! utils/use-live-data? not) (swap! state assoc :foo 1))}
         (if @utils/use-live-data? "off" "on")]]
       [:div {}
        [:a {:href "#status" :style {:textDecoration "none"}} "Status Page"]]]])})


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


(defn- footer []
  (let [thisyear (.getFullYear (js/Date.))
        startyear 2015
        yeartext (if (= startyear thisyear) (str startyear) (str startyear "-" thisyear))
        spacer [:span {:style {:padding "0 0.6em"}} "|"]
        Link (react/create-class
              {:render
               (fn [{:keys [props state]}]
                 [:a {:href (:href props)
                      :target (:target props)
                      :style {:color (:text-lightest style/colors)
                              :textDecoration (when-not (:hovering? @state) "none")}
                      :onMouseOver #(swap! state assoc :hovering? true)
                      :onMouseOut  #(swap! state assoc :hovering? false)}
                  (:text props)])})]
    [:div {:style {:borderTop (str "2px solid " (:line-default style/colors))
                   :padding "1em 25px 2em 25px"
                   :color (:text-lightest style/colors) :fontSize "90%"}}
     (when (config/debug?)
       [:div {:style {:float "right"}} [PopUpFooterControl]])
     [:div {:style {:display "block"}}
      (str "\u00A9 " yeartext " Broad Institute")
      spacer
      [Link {:href "#policy" :text "Privacy Policy"}]
      spacer
      [Link {:href "http://gatkforums.broadinstitute.org/firecloud/discussion/6819/firecloud-terms-of-service#latest"
             :text "Terms of Service" :target "_blank"}]
      spacer
      [Link {:href "http://gatkforums.broadinstitute.org/firecloud" :text "Support"
             :target "_blank"}]]]))


(def routes
  [{:key :profile
    :render #(react/create-element profile-page/Page %)}
   {:key :billing
    :render #(react/create-element billing-management/Page %)}
   {:key :workspaces :href "#workspaces"
    :name "Workspaces"
    :render #(react/create-element workspaces/Page %)}
   {:key :methods :href "#methods"
    :name "Method Repository"
    :render #(react/create-element method-repo/Page %)}
   {:key :policy
    :render #(react/create-element Policy %)}])

(def top-nav-bar-items
  (filter (fn [r] (contains? #{:workspaces :methods} (:key r))) routes))

(react/defc TopNavBarLink
  {:render
   (fn [{:keys [props state]}]
     [:a {:href (:href props)
          :style {:padding "1em" :textDecoration "none"
                  :fontWeight (when (:selected props) "bold")
                  :color (if (:hovering? @state) (:link-active style/colors) "black")}
          :onMouseOver #(swap! state assoc :hovering? true)
          :onMouseOut #(swap! state assoc :hovering? false)}
      (:name props)])})


(react/defc TopNavBar
  {:render
   (fn [{:keys [props]}]
     [:div {}
      (text-logo)
      [:div {:style {:display "inline-block" :paddingLeft "1em" :fontSize 18 :height 38 :verticalAlign "baseline"}}
       (map (fn [item] [TopNavBarLink {:name (:name item) :href (:href item)
                                       :selected (= (:selected-item props) (:key item))}])
            top-nav-bar-items)
       (when (:show-nih-link-warning? props)
         [nih-link-warning/NihLinkWarning])]])})


(react/defc GlobalSubmissionStatus
  {:render
   (fn [{:keys [state]}]
     (let [{:keys [status-error status-code status-counts]} @state
           {:keys [queued active queue-position]} status-counts]
       (when-not (= status-code 401) ; to avoid displaying "Workflows: Unauthorized"
         [:div {}
          (str "Workflows: "
               (cond status-error status-error
                     status-counts (str queued " Queued; " active " Active; " queue-position " ahead of yours")
                     :else "loading..."))])))
   :component-did-mount
   (fn [{:keys [this locals]}]
     ;; Call once for initial load
     (react/call :load-data this)
     ;; Add a long-polling call for continuous updates
     (swap! locals assoc :interval-id (js/setInterval #(react/call :load-data this) (config/submission-status-refresh))))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (js/clearInterval (:interval-id @locals)))
   :load-data
   (fn [{:keys [state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/submissions-queue-status)
        :on-done (fn [{:keys [success? status-text status-code get-parsed-response]}]
                   (if success?
                     (swap! state assoc :status-error nil :status-code nil :status-counts (common/queue-status-counts (get-parsed-response false)))
                     (swap! state assoc :status-error status-text :status-code status-code :status-counts nil)))}))})

(react/defc AccountDropdown
  {:render
   (fn [{:keys [state]}]
     [:div {:style {:float "right" :position "relative" :marginBottom "0.5em"}}
      (when (:show-dropdown? @state)
        [:div {:style {:position "fixed" :top 0 :left 0 :right 0 :bottom 0}
               :onClick #(swap! state assoc :show-dropdown? false)}])
      [:a {:href "javascript:;"
           :onClick #(swap! state assoc :show-dropdown? true)
           :style {:display "block"
                   :borderRadius 2
                   :backgroundColor (:background-light style/colors)
                   :color "#000" :textDecoration "none"
                   :padding "0.6em" :border style/standard-line
                   :minWidth 100}}
       [:div {}
        (:userEmail @utils/current-user-info)
        [:div {:style {:display "inline-block" :marginLeft "1em" :fontSize 8}} "▼"]]]
      (when (:show-dropdown? @state)
        (let [DropdownItem
              (react/create-class
               {:render
                (fn [{:keys [props state]}]
                  [:a {:style {:display "block"
                               :color "#000" :textDecoration "none" :fontSize 14
                               :padding "0.6em 1.8em 0.6em 0.6em"
                               :backgroundColor (when (:hovering? @state) "#e8f5ff")}
                       :href (:href props)
                       :onMouseOver #(swap! state assoc :hovering? true)
                       :onMouseOut #(swap! state assoc :hovering? false)
                       :onClick (:dismiss props)}
                   (:text props)])})]
          [:div {:style {:boxShadow "0px 3px 6px 0px rgba(0, 0, 0, 0.15)"
                         :backgroundColor "#fff"
                         :position "absolute" :width "100%"
                         :border (str "1px solid " (:line-default style/colors))}}
           [DropdownItem {:href "#profile" :text "Profile" :dismiss #(swap! state assoc :show-dropdown? false)}]
           [DropdownItem {:href "#billing" :text "Billing" :dismiss #(swap! state assoc :show-dropdown? false)}]]))])})

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
          [AccountDropdown]
          (common/clear-both)
          (when (= :registered (:registration-status @state))
            [GlobalSubmissionStatus])]
         (when (= :registered (:registration-status @state))
           [TopNavBar {:selected-item page
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
   (fn [{:keys [props]}]
     [:div {}
      [:div {:style {:marginBottom "2em"}} (text-logo)]
      [:div {}
       [sign-in/Button (select-keys props [:on-login])]]
      [:div {:style {:marginTop "2em" :maxWidth 600}}
       [:div {} [:b {} "New user? FireCloud requires a Google account."]]
       [:div {} "Please use the \"Sign In\" button above to sign-in with your Google Account.
         Once you have successfully signed-in with Google, you will be taken to the FireCloud
         registration page."]]
      [:div {:style {:maxWidth 600 :paddingTop "2em" :fontSize "small"}}
        [Policy]]])})


(defn- parse-hash-into-map []
  ;; Transforms the browser navigation hash into a map to make it easier to pass around. Technique
  ;; stolen from Swagger's authentication flow.
  (let [hash (nav/get-hash-value)
        hash (clojure.string/replace hash #"^[?]" "")
        parts (clojure.string/split hash #"[&=]")
        parts (map js/decodeURIComponent parts)]
    (apply assoc {} parts)))


(defn- attempt-auth [token on-done]
  ;; Use basic ajax call here to bypass default ajax-orch failure behavior.
  (utils/ajax {:url (str (config/api-url-root) "/me")
               :headers {"Authorization" (str "Bearer " token)}
               :on-done on-done}))


(defn- show-system-status-dialog [maintenance-mode?]
  (modal/push-ok-cancel-modal
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


(react/defc App
  {:handle-hash-change
   (fn [{:keys [state]}]
     (swap! state assoc :root-nav-context (nav/create-nav-context)))
   :get-initial-state
   (fn []
     {:root-nav-context (nav/create-nav-context)})
   :render
   (fn [{:keys [this state]}]
     [:div {}
      [:div {:style {:backgroundColor "white" :padding 20}}
       [:div {}
        (cond
          (nil? (:config-status @state))
          [:div {}
           (text-logo)
           [:div {:style {:padding "40px 0"}}
            [comps/Spinner {:text "Loading configuration..."}]]]
          (= :error (:config-status @state))
          [:div {}
           (text-logo)
           [:div {:style {:padding "40px 0"}}
            [:div {:style {:color (:exception-state style/colors)}}
             "Error loading configuration. Please try again later."]]]
          (nil? (:user-status @state))
          [:div {}
           (text-logo)
           [:div {:style {:padding "40px 0"}}
            [comps/Spinner {:text "Loading user data..."}]]]
          (= :error (:user-status @state))
          [:div {}
           (text-logo)
           [:div {:style {:padding "40px 0"}}
            [:div {:style {:color (:exception-state style/colors)}}
             "Error getting user information."]]]
          (= :auth-failure (:user-status @state))
          [:div {}
           (text-logo)
           [:div {:style {:padding "40px 0"}}
            [:div {:style {:color (:exception-state style/colors)}}
             "Authentication failed."]]]
          (= :not-activated (:user-status @state))
          [:div {}
           (text-logo)
           [:div {:style {:padding "40px 0"}}
            [:div {:style {:color (:exception-reds style/colors)}}
             "Thank you for registering. Your account is currently inactive.
              You will be contacted via email when your account is activated."]]]
          (= :logged-in (:user-status @state))
          [LoggedIn {:nav-context (:root-nav-context @state)}]
          (= :not-logged-in (:user-status @state))
          [LoggedOut {:on-login #(react/call :authenticate-with-fresh-token this
                                             (get % "access_token"))}])]]
      (footer)
      ;; As low as possible on the page so it will be the frontmost component when displayed.
      [modal/Component {:ref "modal"}]])
   :component-did-mount
   (fn [{:keys [this state refs]}]
     (set! utils/auth-expiration-handler (partial sign-in/show-sign-in-dialog :expired))
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
     (react/call :load-config this #(react/call :authenticate-user this)))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (.removeEventListener js/window "hashchange" (:hash-change-listener @locals))
     (remove-watch utils/server-down? :server-watcher)
     (remove-watch utils/maintenance-mode? :server-watcher))
   :load-config
   (fn [{:keys [this state]} on-success]
     ;; Use basic ajax call here to bypass authentication.
     (utils/ajax {:url "/config.json"
                  :on-done (fn [{:keys [success? get-parsed-response]}]
                             (if success?
                               (do
                                 (reset! config/config (get-parsed-response false))
                                 (swap! state assoc :config-status :success)
                                 (on-success))
                               (swap! state assoc :config-status :error)))}))
   :authenticate-user
   (fn [{:keys [this state]}]
     (if (= (.. js/window -location -hash) sign-in/flow-start-location-hash)
       (utils/ajax-orch
        "/me"
        {:service-prefix ""
         :on-done (fn []
                    ;; If this call fails, it will be caught by the normal handlers, so no need to
                    ;; check for success here.
                    (set! (.. js/window -location -href)
                          (str (config/api-url-root) "/login?callback="
                               (js/encodeURIComponent (.. js/window -location -origin)))))}
        :ignore-auth-expiration? true)
       ;; Note: window.opener can be non-nil even when it wasn't opened with window.open, so be
       ;; careful with this check.
       (let [opener (.-opener js/window)
             sign-in-handler (and opener (aget opener sign-in/handler-fn-name))]
         (if sign-in-handler
           ;; This window was used for login. Send the token to the parent. No need to render the
           ;; UI.
           (do
             (sign-in-handler (parse-hash-into-map))
             (.close js/window))
           (if-let [token (get (parse-hash-into-map) "access_token")]
             ;; New access token. Attempt to authenticate with it.
             (react/call :authenticate-with-fresh-token this token)
             (let [token (utils/get-access-token-cookie)]
               (if token
                 (react/call :authenticate-with-token this token
                             (fn []
                               ;; maybe bad cookie, not auth failure
                               (utils/delete-access-token-cookie)
                               (swap! state assoc :user-status :not-logged-in))
                             (fn []
                               (react/call :handle-successful-auth this token)))
                 (swap! state assoc :user-status :not-logged-in))))))))
   :authenticate-with-fresh-token
   (fn [{:keys [this state]} token]
     (react/call :authenticate-with-token this token
                 (fn [] (swap! state assoc :user-status :auth-failure))
                 (fn []
                   (utils/set-access-token-cookie token)
                   (react/call :handle-successful-auth this token))))
   :authenticate-with-token
   (fn [{:keys [state]} token on-failure on-success]
     (attempt-auth
      token
      (fn [{:keys [success? status-code get-parsed-response]}]
        (when success?
          (reset! utils/current-user-info (:userInfo (get-parsed-response))))
        (cond
          (contains? #{0 502} status-code)
          (do (reset! utils/maintenance-mode? true) (swap! state assoc :user-status :error))
          (contains? (set (range 501 600)) status-code)
          (do (reset! utils/server-down? true) (swap! state assoc :user-status :error))
          (= 401 status-code)
          (on-failure)
          (= 403 status-code)
          (swap! state assoc :user-status :not-activated)
          ;; 404 just means not registered
          (or success? (= status-code 404))
          (on-success)
          :else
          (swap! state assoc :user-status :error)))))
   :handle-successful-auth
   (fn [{:keys [this state locals]} token]
     (reset! utils/access-token token)
     (swap! locals assoc :hash-change-listener (partial react/call :handle-hash-change this))
     (.addEventListener js/window "hashchange" (:hash-change-listener @locals))
     (swap! state assoc :user-status :logged-in :root-nav-context (nav/create-nav-context)))})


(defn render-application [& [hot-reload?]]
  (react/render (react/create-element App) (.. js/document (getElementById "app")) nil hot-reload?))
