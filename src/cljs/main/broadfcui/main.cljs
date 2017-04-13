(ns broadfcui.main
  (:require
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
   [broadfcui.page.workspaces-list :as workspaces]
   [broadfcui.utils :as utils]
   ))


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
    :render #(react/create-element auth/Policy %)}])

(react/defc LoggedIn
  {:render
   (fn [{:keys [this props state]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           page (keyword (:segment nav-context))]
       (when-not (or (contains? (set (map :key routes)) page)
                     (= page :status))
         (nav/navigate (:nav-context props) "workspaces"))
       [:div {}
        [:div {:style {:display "flex" :borderBottom (str "1px solid " (:line-default style/colors))}}
         (when (= :registered (:registration-status @state))
           [header/TopNavBar {:routes routes
                              :selected-item page
                              :show-nih-link-warning? (not (contains? #{:status :profile} page))}])
         flex/spring
         [:div {:style {:display "flex" :flexDirection "column" :fontSize "70%" :marginBottom "0.4rem"}}
          [:div {:style {:marginBottom "0.4rem"}}
           (header/create-account-dropdown (:auth2 props))
           (common/render-dropdown-menu {:label (icons/icon {:style style/secondary-icon-style} :help)
                                         :width 150
                                         :button-style {:height 32 :marginRight "0.5rem"}
                                         :items [{:href (config/user-guide-url) :target "_blank"
                                                  :text "User Guide"}
                                                 {:href (config/forum-url) :target "_blank"
                                                  :text "Firecloud Forums"}]})]
          (when (= :registered (:registration-status @state))
            [header/GlobalSubmissionStatus])]]
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
        [auth/RefreshCredentials {:auth2 (:auth2 @state)}])
      [:div {:style {:backgroundColor "white" :padding 20}}
       [:div {}
        (when-let [auth2 (:auth2 @state)]
          [auth/LoggedOut {:auth2 auth2 :hidden? (contains? (:user-status @state) :signed-in)
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
          (nil? (:auth2 @state))
          [:div {}
           (style/render-text-logo)
           [auth/GoogleAuthLibLoader {:on-loaded #(swap! state assoc :auth2 %)}]]
          (contains? (:user-status @state) :signed-in)
          (cond
            (not (contains? (:user-status @state) :go))
            [auth/UserStatus {:on-success #(swap! state update :user-status conj :go)}]
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
     (remove-watch utils/maintenance-mode? :server-watcher))})


(defn render-application []
  (react/render (react/create-element App) (utils/get-app-root-element)))


(render-application)
