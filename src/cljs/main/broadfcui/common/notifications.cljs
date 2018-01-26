(ns broadfcui.common.notifications
  (:require
   [dmohs.react :as react]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.markdown :as markdown]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.components.top-banner :as top-banner]
   [broadfcui.config :as config]
   [broadfcui.user-info :as user-info]
   [broadfcui.utils :as utils]
   ))

(defn render-alert [{:keys [cleared? link message title]} dismiss]
  (let [text-color "#eee"]
    (top-banner/render
     [:div {:style {:color text-color
                    :background-color (if cleared?
                                        (:state-success style/colors)
                                        (:state-exception style/colors))
                    :padding "1rem"}}
      (when cleared?
        [:div {:style {:float "right"}}
         (icons/render-icon {:style {:fontSize "80%" :cursor "pointer"} :on-click dismiss} :close)])
      [:div {:style {:display "flex" :align-items "baseline"}}
       [icons/ExceptionIcon {:size 18 :color text-color}]
       [:span {:style {:margin-left "0.5rem" :font-weight "bold" :vertical-align "middle"}}
        (or title "Service Alert")
        (when cleared?
          " (resolved)")]
       [:span {:style {:color text-color :fontSize "90%" :margin-left "1rem"}}
        message
        (when link
          (links/create-external {:href link
                                  :style {:color text-color :margin-left "1rem"}}
                                 "Read more..."))]]])))

(defn- status-alert-interval [attempt]
  (cond
    (zero? attempt) (config/status-alerts-refresh)
    (> attempt (config/max-retry-attempts)) (config/status-alerts-refresh)
    :else (utils/get-exponential-backoff-interval attempt)))

(react/defc ServiceAlertContainer
  {:get-initial-state
   (fn []
     {:failed-retries 0})
   :render
   (fn [{:keys [this state]}]
     (let [{:keys [service-alerts]} @state]
       [:div {}
        (when (:show-new-service-alert-message? @state)
          (modals/render-message {:header "New Service Alert" :text "See the page header for details."
                                  :dismiss #(swap! state dissoc :show-new-service-alert-message?)}))
        (map #(render-alert % (partial this :-remove-alert %)) service-alerts)]))
   :component-did-update
   (fn [{:keys [this state locals]}]
     ;; Reset the interval
     (js/clearInterval (:interval-id @locals))
     ;; Update the poll interval based on the number of failed attempts (for exponential back offs)
     (swap! locals assoc :interval-id
            (js/setInterval #(this :-load-service-alerts)
                            (status-alert-interval (:failed-retries @state)))))
   :component-did-mount
   (fn [{:keys [this state locals]}]
     ;; Call once for initial load
     (this :-load-service-alerts true)
     ;; Add initial poll interval
     (swap! locals assoc :interval-id
            (js/setInterval #(this :-load-service-alerts)
                            (status-alert-interval (:failed-retries @state)))))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (js/clearInterval (:interval-id @locals)))
   :-load-service-alerts
   (fn [{:keys [this]} & [first-time?]]
     (utils/ajax {:url (config/google-bucket-url "alerts")
                  :on-done (partial this :-handle-response first-time?)}))
   :-handle-response
   (fn [{:keys [state]} first-time? {:keys [status-code raw-response]}]
     (let [alerts (if (and (utils/check-server-down status-code)
                           (>= (:failed-retries @state) (config/max-retry-attempts)))
                    [{:title "Google Service Alert"
                      :message "There may be problems accessing data in Google Cloud Storage."
                      :link "https://status.cloud.google.com/"}]
                    (let [[parsed _] (utils/parse-json-string raw-response true false)]
                      parsed))
           alerts-set (set alerts)
           existing (set (remove :cleared? (:service-alerts @state)))
           cleared (clojure.set/difference existing alerts-set)
           new (clojure.set/difference alerts-set existing)
           updated (concat (:service-alerts @state) (filter #(contains? new %) alerts))
           updated (map (fn [alert]
                          (if (contains? cleared alert) (assoc alert :cleared? true) alert))
                        updated)]
       (swap! state assoc
              :service-alerts updated
              :failed-retries (if (utils/check-server-down status-code)
                                (inc (:failed-retries @state))
                                0))
       (when (and (seq new) (not first-time?))
         (swap! state assoc :show-new-service-alert-message? true))))
   :-remove-alert
   (fn [{:keys [state]} alert]
     (swap! state update :service-alerts #(filter (partial not= alert) %)))})

(react/defc TrialAlertContainer
  {:render
   (fn [{:keys [state this]}]
     (let [{:keys [dismissed? loading? messages error]} @state]
       (when-let [current-trial-state (keyword (:trialState @user-info/saved-user-profile))]
         (when (and (not dismissed?) (current-trial-state messages)) ; Disabled or mis-keyed users do not see a banner
           (let [{:keys [title message warning? link button eulas]} (messages current-trial-state)]
             (apply ;; needed until dmohs/react deals with nested seq's
              flex/box
              {:style {:color "white"
                       :background-color ((if warning? :state-warning :button-primary) style/colors)}}
              (flex/box
               {:style {:padding "1rem" :flexGrow 1
                        :justifyContent "center" :alignItems "center"}}
               [:div {:data-test-id "trial-banner-title"
                      :style {:fontSize "1.5rem" :fontWeight 500 :textAlign "right"
                              :borderRight "1px solid white"
                              :paddingRight "1rem" :marginRight "1rem"
                              :maxWidth 200 :flexShrink 0}}
                title]
               [:span {:style {:maxWidth 600 :lineHeight "1.5rem"}}
                message
                (when-let [{:keys [label url]} link]
                  (links/create-external {:href url :style {:color "white" :marginLeft "0.5rem"}} label))]
               (when-let [{:keys [label url external?]} button]
                 [:a {:data-test-id "trial-banner-button"
                      :data-test-state (if loading? "loading" "ready")
                      :style {:display "block"
                              :color "white" :textDecoration "none" :fontSize "1.125rem"
                              :fontWeight 500
                              :border "2px solid white" :borderRadius "0.25rem"
                              :padding "0.5rem 1rem" :marginLeft "0.5rem" :flexShrink 0}
                      :href (if external? url (when-not loading? "javascript:;"))
                      :onClick (when-not (or external? loading?)
                                 #(swap! state assoc :displaying-eula? true))
                      :target (when external? "_blank")}
                  (if loading?
                    (spinner {:style {:fontSize "1rem" :margin 0}})
                    label)
                  (when external?
                    (icons/render-icon
                     {:style {:margin "-0.5em -0.3em -0.5em 0.5em" :fontSize "1rem"}}
                     :external-link))]))
              [:div {:style {:alignSelf "center" :padding "1rem" :display "flex"
                             :flexDirection "column"}}
               [:button {:className "button-reset" :onClick #(swap! state assoc :dismissed? true)
                         :style {:display "block"
                                 :alignSelf "center" :fontSize "1.5rem"
                                 :color "white" :cursor "pointer"}
                         :title "Hide for now"}
                (icons/render-icon {} :close)]
               (when (= current-trial-state :Terminated)
                 (links/create-internal
                   {:style {:fontSize "small" :color "white" :marginTop "0.5rem"}
                    :onClick (fn []
                               (utils/ajax-orch
                                "/profile/trial?operation=finalize"
                                {:method :post
                                 :on-done user-info/reload-user-profile}))}
                   "Hide forever"))]
              (modals/show-modals
               state
               {:displaying-eula?
                (this :-show-eula-modal eulas)
                :error
                (modals/render-error {:text error :on-dismiss #(swap! state dissoc :error)})})))))))
   :component-will-mount
   (fn [{:keys [this state]}]
     (add-watch user-info/saved-user-profile :trial-alerts
                (fn [_ _ _ {:keys [trialState]}]
                  (when trialState
                    (if-not (:messages @state)
                      (utils/get-google-bucket-file "trial" #(swap! state assoc :messages %))
                      (.forceUpdate this))))))
   :-show-eula-modal
   (fn [{:keys [state]} eulas]
     (let [{:keys [page-2? terms-agreed? cloud-terms-agreed?]} @state
           {:keys [broad onix]} eulas
           accept-eula (fn []
                         (utils/ajax-orch
                          "/profile/trial/userAgreement"
                          {:method :put
                           :on-done
                           (fn [{:keys [success?]}]
                             (if-not success?
                               (utils/multi-swap! state
                                                  (assoc :error "An error occurred. Please try again.")
                                                  (dissoc :loading?))
                               (utils/ajax-orch
                                "/profile/trial"
                                {:method :post
                                 :on-done
                                 (fn [{:keys [success? get-parsed-response]}]
                                   (if success?
                                     (user-info/reload-user-profile
                                      #(swap! state dissoc :loading?))
                                     (utils/multi-swap! state
                                                        (assoc :error (:message (get-parsed-response)))
                                                        (dissoc :loading?))))})))})
                         (utils/multi-swap! state
                                            (assoc :loading? true)
                                            (dissoc :displaying-eula?)))]
       [modals/OKCancelForm
        {:header "Welcome to the FireCloud Free Credit Program!"
         :dismiss #(swap! state dissoc :displaying-eula? :page-2? :terms-agreed? :cloud-terms-agreed?)
         :content
         (let [div-id (gensym "eula")]
           [:div {:id div-id
                  :style {:backgroundColor "white" :padding "1rem" :maxWidth 850}}
            [:style {}
             (str "#" div-id " .markdown-body strong {text-decoration: underline}\n"
                  "#" div-id " .markdown-body ol {counter-reset: item}\n"
                  "#" div-id " .markdown-body li:before {content: counters(item, \".\") \".\";\n"
                  "  counter-increment: item; margin-left: -2em; position: absolute}\n"
                  "#" div-id " .markdown-body li li:before {margin-left: -3em}\n"
                  "#" div-id " .markdown-body li li li:before {margin-left: -4em}\n"
                  "#" div-id " .markdown-body li {display: block}")]
            (if-not page-2?
              [markdown/MarkdownView {:text broad}]
              [:div {}
               [:div {:style {:fontSize "80%"}}
                [markdown/MarkdownView {:text onix}]]
               [:div {:style {:padding "1rem" :marginTop "0.5rem"
                              :border style/standard-line :background (:background-light style/colors)}}
                [:label {:style {:marginBottom "0.5rem" :display "block"}}
                 [:input {:type "checkbox"
                          :onChange #(swap! state assoc :terms-agreed? (.. % -target -checked))
                          :data-test-id "agree-terms"}]
                 "I agree to the terms of this Agreement."]
                [:label {:style {:display "block"}}
                 [:input {:type "checkbox"
                          :onChange #(swap! state assoc :cloud-terms-agreed? (.. % -target -checked))
                          :data-test-id "agree-cloud-terms"}]
                 "I agree to the Google Cloud Terms of Service."]
                [:div {:style {:paddingLeft "1rem"}} "Google Cloud Terms of Service: "
                 (links/create-external {:href "https://cloud.google.com/terms/"}
                                        "https://cloud.google.com/terms/")]]])])
         :data-test-id "eula-modal"
         :button-bar (flex/box
                      {:style {:justifyContent "center" :width "100%"}}
                      (when page-2?
                        [buttons/Button
                         {:text "Back"
                          :style {:marginRight "2rem"}
                          :onClick #(swap! state dissoc :page-2? :terms-agreed? :cloud-terms-agreed?)}])
                      [buttons/Button
                       (if-not page-2?
                         {:text "Review Terms of Service"
                          :data-test-id "review-terms-of-service"
                          :onClick #(swap! state assoc :page-2? true)}
                         {:text "Accept"
                          :data-test-id "accept-terms-of-service"
                          :disabled? (when-not (and terms-agreed? cloud-terms-agreed?)
                                       "You must check the boxes to accept the agreement.")
                          :onClick accept-eula})])}]))})
