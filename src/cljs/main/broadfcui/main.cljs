(ns broadfcui.main
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.uicomps.modal :as modal]
   [clojure.string :as string]
   [broadfcui.auth :as auth]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.modal :as old-modal]
   [broadfcui.common.notifications :as notifications]
   [broadfcui.common.style :as style]
   [broadfcui.components.foundation-dropdown :as dropdown]
   [broadfcui.components.top-banner :as top-banner]
   [broadfcui.config :as config]
   [broadfcui.config.loader :as config-loader]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.footer :as footer]
   [broadfcui.injections :as injections]
   [broadfcui.header :as header]
   [broadfcui.nav :as nav]
   [broadfcui.nih-link-warning :refer [NihLinkWarning]]
   [broadfcui.page.billing.billing-management :as billing-management]
   [broadfcui.page.groups.groups-management :as group-management]
   [broadfcui.page.library.library-page :as library-page]
   [broadfcui.page.method-repo.method.details :as method-details]
   [broadfcui.page.method-repo.method-repo-page :as method-repo]
   [broadfcui.page.notifications :as billing-notifications]
   [broadfcui.page.profile :as profile-page]
   [broadfcui.page.status :as status-page]
   [broadfcui.page.style-guide :as style-guide]
   [broadfcui.page.workspace.details :as workspace-details]
   [broadfcui.page.workspaces-list :as workspaces]
   [broadfcui.utils :as utils]
   ))


(injections/setup)


(defn- init-nav-paths []
  (nav/clear-paths)
  (auth/add-nav-paths)
  (billing-management/add-nav-paths)
  (group-management/add-nav-paths)
  (library-page/add-nav-paths)
  (method-details/add-nav-paths)
  (method-repo/add-nav-paths)
  (billing-notifications/add-nav-paths)
  (profile-page/add-nav-paths)
  (status-page/add-nav-paths)
  (style-guide/add-nav-paths)
  (workspace-details/add-nav-paths)
  (workspaces/add-nav-paths))

(react/defc- LoggedIn
  {:render
   (fn [{:keys [this props state]}]
     (let [{:keys [component make-props]} props
           path (subs (aget js/window "location" "hash") 1)]
       [:div {}
        [:div {:style {:display "flex" :borderBottom (str "1px solid " (:line-default style/colors))}}
         (when (= :registered (:registration-status @state))
           [header/TopNavBar
            {:items (concat
                     [{:label "Workspaces"
                       :nav-key :workspaces
                       :data-test-id "workspace-nav-link"
                       :is-selected? #(or (empty? path)
                                          (string/starts-with? path "workspaces/"))}
                      {:label "Data Library"
                       :nav-key :library
                       :data-test-id "library-nav-link"
                       :is-selected? #(= path "library")}
                      {:label "Method Repository"
                       :nav-key :method-repo
                       :data-test-id "method-repo-nav-link"
                       :is-selected? #(or (= path "methods")
                                          (string/starts-with? path "methods/"))}])}])
         flex/spring
         [:div {:style {:display "flex" :flexDirection "column" :fontSize "70%" :marginBottom "0.4rem"}}
          [:div {:style {:display "flex" :justifyContent "flex-end"}}
           (dropdown/render-dropdown-menu {:label (icons/render-icon {:style style/secondary-icon-style} :help)
                                           :width 150
                                           :button-style {:height 32 :marginRight "0.5rem" :marginBottom "0.4rem"}
                                           :items [{:href (config/user-guide-url) :target "_blank"
                                                    :text [:span {} "User Guide" icons/external-link-icon]}
                                                   {:href (config/forum-url) :target "_blank"
                                                    :text [:span {} "FireCloud Forum" icons/external-link-icon]}]})
           (header/create-account-dropdown)]]]
        (let [original-destination (aget js/window "location" "hash")
              on-done (fn [fall-through]
                        (when (empty? original-destination)
                          (nav/go-to-path fall-through))
                        (this :-load-registration-status))]
          (case (:registration-status @state)
            nil [:div {:style {:margin "2em 0" :textAlign "center"}}
                 [comps/Spinner {:text "Loading user information..."}]]
            :error [:div {:style {:margin "2em 0"}}
                    (style/create-server-error-message (.-errorMessage this))]
            :not-registered (profile-page/render
                             {:new-registration? true
                              :on-done #(on-done :library)})
            :update-registered (profile-page/render
                                {:update-registration? true
                                 :on-done #(on-done :workspaces)})
            :registered
            (if component
              [component (make-props)]
              [:h2 {} "Page not found."])))]))
   :component-did-mount
   (fn [{:keys [this state]}]
     (when (nil? (:registration-status @state))
       (this :-load-registration-status)))
   :-load-registration-status
   (fn [{:keys [this state]}]
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
              (swap! state assoc :registration-status :error)))))))})

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


(defn- show-js-exception [e]
  (comps/push-ok-cancel-modal
   {:header [:span {} (icons/render-icon {:style {:color (:warning-state style/colors)
                                           :marginRight "1rem"}}
                                         :warning)
             "Something Went Wrong"]
    :content [:div {:style {:width 800}}
              "A JavaScript error occurred; please try reloading the page. If the error persists, please report it to our "
              (links/create-external {:href (config/forum-url)} "forum")
              " for help. Details of the error message are below."
              [:div {:style {:fontFamily "monospace" :whiteSpace "pre" :overflow "auto"
                             :backgroundColor "black" :color "white"
                             :padding "0.5rem" :marginTop "0.5rem" :borderRadius "0.3rem"}}
               [:div {:style {:fontWeight "bold"}} "Error: "]
               (aget e "message")
               [:div {:style {:fontWeight "bold" :paddingTop "0.5rem"}} "Source: "]
               (aget e "filename")]]
    :show-cancel? false :ok-button "OK"}))

(react/defc- App
  {:handle-hash-change
   (fn [{:keys [state]}]
     (let [window-hash (aget js/window "location" "hash")]
       (when-not (nav/execute-redirects window-hash)
         (swap! state assoc :window-hash window-hash))))
   :get-initial-state
   (fn []
     {:user-status #{}})
   :component-will-mount
   (fn [{:keys [this state]}]
     (init-nav-paths)
     (this :handle-hash-change)
     (set! js/window.forceSignedIn
           (fn [auth-token]
             (utils/ajax {:url (str "https://www.googleapis.com/oauth2/v3/tokeninfo?access_token=" auth-token)
                          :on-done
                          (fn [{:keys [status-code success? get-parsed-response]}]
                            (if success?
                              (let [{:keys [email user_id]} (get-parsed-response)
                                    instance (clj->js
                                              {:currentUser
                                               {:get
                                                (fn []
                                                  (clj->js {:getAuthResponse
                                                            (fn [] (clj->js {:access_token auth-token}))
                                                            :getBasicProfile
                                                            (fn [] (clj->js
                                                                    {:getEmail (fn [] email)
                                                                     :getId (fn [] user_id)}))}))
                                                :listen (fn [])}
                                               :signOut
                                               (fn [] (swap! state update :user-status disj :signed-in))})]
                                (utils/set-google-auth2-instance! instance)
                                (swap! state update :user-status conj :signed-in))
                              (utils/log "Failed to validate token: " status-code)))}))))
   :render
   (fn [{:keys [state]}]
     (let [{:keys [auth2 user-status window-hash]} @state
           {:keys [component make-props public?]} (nav/find-path-handler window-hash)
           sign-in-hidden? (or (nil? component)
                               public?
                               (contains? (:user-status @state) :signed-in))]
       [:div {}
        (when (and (contains? user-status :signed-in)
                   (not (or (nav/is-current-path? :profile)
                            (nav/is-current-path? :status))))
          [NihLinkWarning])
        [top-banner/Container]
        (when (:config-loaded? @state)
          [notifications/ServiceAlertContainer])
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
              {:on-success (fn []
                             (swap! state assoc :config-loaded? true)
                             (when (config/debug?)
                               (.addEventListener
                                js/window "error" show-js-exception)))}]
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
               :else [LoggedIn {:component component :make-props make-props}]))]]
         (footer/render-footer)
         ;; As low as possible on the page so it will be the frontmost component when displayed.
         [old-modal/Component {:ref "modal"}]
         [modal/Container {:z-index style/modals-z-index}]]]))
   :component-did-mount
   (fn [{:keys [this refs locals]}]
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
     (old-modal/set-instance! (@refs "modal"))
     (swap! locals assoc :hash-change-listener (partial this :handle-hash-change))
     (.addEventListener js/window "hashchange" (:hash-change-listener @locals))
     (aset js/window "testJsException"
           (fn [] (js/setTimeout #(throw (js/Error. "You told me to do this.")) 100) nil)))
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
