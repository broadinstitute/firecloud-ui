(ns broadfcui.main
  (:require
   clojure.string
   [dmohs.react :as react]
   [broadfcui.auth :as auth]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
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
   [broadfcui.page.workspace.details :as workspace-details]
   [broadfcui.page.workspaces-list :as workspaces]
   [broadfcui.utils :as utils]
   ))

(defn- init-nav-paths []
  (nav/clear-paths)
  (auth/add-nav-paths)
  (billing-management/add-nav-paths)
  (library-page/add-nav-paths)
  (method-repo/add-nav-paths)
  (profile-page/add-nav-paths)
  (status-page/add-nav-paths)
  (workspace-details/add-nav-paths)
  (workspaces/add-nav-paths))

(react/defc LoggedIn
  {:render
   (fn [{:keys [this props state]}]
     (let [{:keys [component make-props]} props
           path (subs (aget js/window "location" "hash") 1)]
       [:div {}
        [:div {:style {:display "flex" :borderBottom (str "1px solid " (:line-default style/colors))}}
         (when (= :registered (:registration-status @state))
           [header/TopNavBar
            {:items [{:label "Workspaces"
                      :nav-key :workspaces
                      :is-selected? #(or (empty? path)
                                         (clojure.string/starts-with? path "workspaces/"))}
                     {:label "Data Library"
                      :nav-key :library
                      :is-selected? #(= path "library")}
                     {:label "Method Repository"
                      :nav-key :method-repo
                      :is-selected? #(or (= path "methods")
                                         (clojure.string/starts-with? path "methods/"))}]
             :show-nih-link-warning? (not (or (nav/is-current-path? :profile)
                                              (nav/is-current-path? :status)))}])
         flex/spring
         [:div {:style {:display "flex" :flexDirection "column" :fontSize "70%" :marginBottom "0.4rem"}}
          [:div {:style {:marginBottom "0.4rem"}}
           (header/create-account-dropdown (:auth2 props))
           (common/render-dropdown-menu {:label (icons/icon {:style style/secondary-icon-style} :help)
                                         :width 150
                                         :button-style {:height 32 :marginRight "0.5rem"}
                                         :items [{:href (config/user-guide-url) :target "_blank"
                                                  :text [:span {} "User Guide" icons/external-link-icon]}
                                                 {:href (config/forum-url) :target "_blank"
                                                  :text [:span {} "FireCloud Forum" icons/external-link-icon]}]})]
          (when (= :registered (:registration-status @state))
            [header/GlobalSubmissionStatus])]]
        (case (:registration-status @state)
          nil [:div {:style {:margin "2em 0" :textAlign "center"}}
               [comps/Spinner {:text "Loading user information..."}]]
          :error [:div {:style {:margin "2em 0"}}
                  (style/create-server-error-message (.-errorMessage this))]
          :not-registered (profile-page/render
                           {:new-registration? true
                            :on-done #(do (nav/go-to-path :library)
                                          (js-invoke (aget js/window "location") "reload"))})
          :update-registered (profile-page/render
                              {:update-registration? true
                               :on-done #(do (nav/go-to-path :workspaces)
                                             (js-invoke (aget js/window "location") "reload"))})
          :registered
          (if component
            [component (make-props)]
            [:h2 {} "Page not found."]))]))
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

(defn status-alert [title message link]
  [:div {:style {:borderBottom "1px solid" :borderBottomColor (:line-default style/colors)
                 :backgroundColor (:exception-state style/colors) :padding "1rem"}}
   [:div {:style {:display "flex" :background (:exception-state style/colors) :color "#fff"
                  :alignItems "center" :marginBottom "0.5rem"}}
    [icons/ExceptionIcon {:size 18}]
    [:span {:style {:marginLeft "0.5rem" :fontWeight 600
                    :verticalAlign "middle"}}
     (if title title "Service Alert")]]
   [:div {:style {:color "#fff" :fontSize "90%"}}
    (str message " ")
     (when link [:a {:href (str link) :target "_blank" :style {:color "#fff"}} "Read more..."
                 icons/external-link-icon])]])

(defn status-alert-interval [attempt]
  (cond
    (= attempt 0) (config/status-alerts-refresh)
    (> attempt (config/max-retry-attempts)) (config/status-alerts-refresh)
    :else (utils/get-exponential-backoff-interval attempt)))

(react/defc ServiceAlertContainer
  {:get-initial-state
   (fn []
     {:failed-retries 0})
   :render
   (fn [{:keys [state]}]
     (let [{:keys [service-alerts]} @state]
       [:div {}
        (map (fn [alert]
               [comps/Banner (merge (select-keys alert [:title :message])
                              {:background-color (:exception-state style/colors)
                               :text-color "#fff"
                               :link (when-let [link (:link alert)]
                                       [:a {:style {:color "#fff"} :href (str link)
                                          :target "_blank"}
                                      "Read more..."])})])
             service-alerts)]))
   :component-did-update
   (fn [{:keys [this state locals]}]
     ;; Reset the interval
     (js/clearInterval (:interval-id @locals))
     ;; Update the poll interval based on the number of failed attempts (for exponential back offs)
     (swap! locals assoc :interval-id
            (js/setInterval #(this :-load-service-alerts) (status-alert-interval (:failed-retries @state)))))
   :component-did-mount
   (fn [{:keys [this state locals]}]
     ;; Call once for initial load
     (this :-load-service-alerts)
     ;; Add initial poll interval
     (swap! locals assoc :interval-id
            (js/setInterval #(this :-load-service-alerts) (status-alert-interval (:failed-retries @state)))))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (js/clearInterval (:interval-id @locals)))
   :-load-service-alerts
   (fn [{:keys [state]}]
     (utils/ajax {:url (config/alerts-json-url)
                  :headers {"Cache-Control" "no-store, no-cache"}
                  :on-done (fn [{:keys [status-code raw-response]}]
                             (if (utils/check-server-down status-code)
                               (if (>= (:failed-retries @state) (config/max-retry-attempts))
                                 (swap! state assoc :service-alerts
                                        [{:title "Google Service Alert"
                                          :message "There may be problems accessing data in Google Cloud Storage."
                                          :link "https://status.cloud.google.com/"}])
                                 (swap! state assoc :failed-retries (+ (:failed-retries @state) 1)))
                               (let [[parsed _] (utils/parse-json-string raw-response true false)]
                                 (utils/parse-json-string raw-response true false)
                                 (if (not-empty parsed)
                                   (swap! state assoc :service-alerts parsed :failed-retries 0)
                                   (swap! state dissoc :service-alerts)))))}))})

(defn- show-js-exception [e]
  (comps/push-ok-cancel-modal
   {:header [:span {} (icons/icon {:style {:color (:warning-state style/colors)
                                           :marginRight "1rem"}}
                                  :warning)
             "Something Went Wrong"]
    :content [:div {:style {:width 800}}
              "A JavaScript error occurred; please try reloading the page. If the error persists, please report it to our "
               [:a {:href (config/forum-url)
                    :target "_blank" :style {}} "forum"] " for help. Details of the error message are below."
              [:div {:style {:fontFamily "monospace" :whiteSpace "pre" :overflow "auto"
                             :backgroundColor "black" :color "white"
                             :padding "0.5rem" :marginTop "0.5rem" :borderRadius "0.3rem"}}
               [:div {:style {:fontWeight "bold"}} "Error: "]
               (aget e "message")
               [:div {:style {:fontWeight "bold" :paddingTop "0.5rem"}} "Source: "]
               (aget e "filename")]]
    :show-cancel? false :ok-button "OK"}))

(react/defc App
  {:handle-hash-change
   (fn [{:keys [state]}]
     (let [window-hash (aget js/window "location" "hash")]
       (when-not (nav/execute-redirects window-hash)
         (swap! state assoc :window-hash window-hash))))
   :get-initial-state
   (fn []
     {:user-status #{}})
   :component-will-mount
   (fn [{:keys [this]}]
     (init-nav-paths)
     (this :handle-hash-change))
   :render
   (fn [{:keys [state]}]
     (let [{:keys [auth2 user-status window-hash]} @state
           {:keys [component make-props public?]} (nav/find-path-handler window-hash)
           sign-in-hidden? (or (nil? component)
                               public?
                               (contains? (:user-status @state) :signed-in))]
       [:div {}
        (when (:config-loaded? @state)
          ;; We want these banners to be shown in front of the modals, so we use a zIndex above them
          [:div {:style {:zIndex (inc style/modals-z-index) :position "relative"}}
           [ServiceAlertContainer]])
        (when (and (contains? user-status :signed-in) (contains? user-status :refresh-token-saved))
          [auth/RefreshCredentials {:auth2 auth2}])
        [:div {:style {:position "relative"}}
         [:div {:style {:backgroundColor "white" :padding 20}}
          (when-not (contains? user-status :signed-in)
            (style/render-text-logo))
          [:div {}
           (when auth2
             [auth/LoggedOut {:auth2 auth2 :hidden? sign-in-hidden?
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
              {:on-success #(swap! state assoc :config-loaded? true)}]
             (and (not (contains? user-status :signed-in)) (nil? component))
             [:h2 {} "Page not found."]
             public?
             [component (make-props)]
             (nil? auth2)
             [auth/GoogleAuthLibLoader {:on-loaded #(swap! state assoc :auth2 %)}]
             (contains? user-status :signed-in)
             (cond
               (not (contains? user-status :go))
               [auth/UserStatus {:on-success #(swap! state update :user-status conj :go)}]
               :else [LoggedIn {:component component :make-props make-props
                                :auth2 auth2}]))]]
         (footer/render-footer)
         ;; As low as possible on the page so it will be the frontmost component when displayed.
         [modal/Component {:ref "modal"}]]]))
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
     (.addEventListener js/window "hashchange" (:hash-change-listener @locals))
     (.addEventListener js/window "error" (fn [e] (show-js-exception e))))
     :component-will-receive-props
     (fn []
       (init-nav-paths))
     :component-will-unmount
     (fn [{:keys [locals]}]
       (.removeEventListener js/window "hashchange" (:hash-change-listener @locals))
       (remove-watch utils/server-down? :server-watcher)
       (remove-watch utils/maintenance-mode? :server-watcher))})


(defn render-application []
  (react/render (react/create-element App) (utils/get-app-root-element)))


(render-application)
