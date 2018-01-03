(ns broadfcui.common.notifications
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
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
     (utils/ajax {:url (config/alerts-json-url)
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
         (comps/push-message
          {:header "New Service Alert" :message "See the page header for details."}))))
   :-remove-alert
   (fn [{:keys [state]} alert]
     (swap! state update :service-alerts #(filter (partial not= alert) %)))})

(react/defc TrialAlertContainer
  {:render
   (fn [{:keys [state this]}]
     (let [{:keys [dismissed? loading? messages error]} @state]
       (when-let [current-trial-state (keyword (:trialState @user-info/saved-user-profile))]
         (when (and (not dismissed?) (current-trial-state messages)) ; Disabled or mis-keyed users do not see a banner
           (let [{:keys [title message warning? link button eula]} (messages current-trial-state)]
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
              [:button {:className "button-reset" :onClick #(swap! state assoc :dismissed? true)
                        :style {:alignSelf "center" :fontSize "1.5rem" :padding "1rem"
                                :color "white" :cursor "pointer"}
                        :title "Hide for now"}
               (icons/render-icon {} :close)]
              (modals/show-modals
               state
               {:displaying-eula?
                (this :-show-eula-modal eula)
                :error
                (modals/render-error {:text error :on-dismiss #(swap! state dissoc :error)})})))))))
   :component-will-mount
   (fn [{:keys [this state]}]
     (add-watch user-info/saved-user-profile :trial-alerts
                (fn [_ _ _ {:keys [trialState]}]
                  (when trialState
                    (if-not (:messages @state)
                      (this :-get-trial-messages)
                      (.forceUpdate this))))))
   :-get-trial-messages
   (fn [{:keys [state]}]
     (utils/ajax {:url (config/trial-json-url)
                  :on-done (fn [{:keys [get-parsed-response]}]
                             (swap! state assoc :messages (get-parsed-response)))}))
   :-show-eula-modal
   (fn [{:keys [state]} eula]
     (let [{:keys [page-2? terms-agreed? cloud-terms-agreed?]} @state
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
         [:div {:style {:backgroundColor "white" :padding "1rem" :maxWidth 850}}
          (if-not page-2?
            (let [make-li (fn [& body]
                            [:li {:style {:paddingBottom "0.5rem"}} body])]
              [:ol {:style {:paddingRight "1rem"}}
               (make-li "The FireCloud Free Credits Program (\"credits\"), sponsored by Google, is administered by "
                        (links/create-external
                         {:href "https://www.onixnet.com/products/google-cloud/google-cloud-platform/google-app-engine"}
                         "Onix Networking") " (\"Onix\"), a Google Cloud Premier Partner.")
               (make-li "By opting into this program, you are authorizing FireCloud to give Onix and
                Google access to your FireCloud user profile information. This is necessary for Onix
                and Google to give you the free credits.")
               (make-li "Your credits of $250 will expire December 30, 2018 or 60 days after they
                were issued, whichever comes first.")
               (make-li "Onix will contact you during the trial with information on options for
               creating your own billing account to further use FireCloud once the credits expire.
               Other billing options will be available on the FireCloud website.")
               (make-li "FireCloud has no obligation to maintain a billing account or any data saved
               under an account once credits are exhausted.")
               (make-li "Credits are not redeemable for cash and are not transferable.")
               (make-li "All use of FireCloud by researchers is subject to the "
                        (links/create-external
                         {:href "https://software.broadinstitute.org/firecloud/documentation/article?id=6819"}
                         "FireCloud Terms of Use") ", which may be updated from time to time.
                         FireCloud reserves the right to revoke credits for any activity that violates the Terms of Use.")])
            (let [div-id (gensym "eula")]
              [:div {:id div-id}
               [:style {}
                (str "#" div-id " .markdown-body strong {text-decoration: underline}\n"
                     "#" div-id " .markdown-body ol {counter-reset: item}\n"
                     "#" div-id " .markdown-body li:before {content: counters(item, \".\") \".\";\n"
                                "  counter-increment: item; margin-left: -2em; position: absolute}\n"
                     "#" div-id " .markdown-body li li:before {margin-left: -3em}\n"
                     "#" div-id " .markdown-body li li li:before {margin-left: -4em}\n"
                     "#" div-id " .markdown-body li {display: block}")]
               [markdown/MarkdownView {:text eula}]
               [:div {:style {:padding "1rem" :marginTop "0.5rem"
                              :border style/standard-line :background (:background-light style/colors)}}
                [:label {:style {:marginBottom "0.5rem" :display "block"}}
                 [:input {:type "checkbox" :onChange #(swap! state update :terms-agreed? not)}]
                 "I agree to the terms of this Agreement."]
                [:label {:style {:display "block"}}
                 [:input {:type "checkbox" :onChange #(swap! state update :cloud-terms-agreed? not)}]
                 "I agree to the Google Cloud Terms of Service."]
                [:div {:style {:paddingLeft "1rem"}} "Google Cloud Terms of Service: "
                 (links/create-external {:href "https://cloud.google.com/terms/"}
                                        "https://cloud.google.com/terms/")]]]))]
         :data-test-id "eula-modal"
         :button-bar (flex/box
                      {:style {:justifyContent "center" :width "100%"}}
                      (when page-2?
                        [buttons/Button
                         {:text "Back"
                          :style {:marginRight "2rem"}
                          :onClick #(swap! state dissoc :page-2?)}])
                      [buttons/Button
                       (if-not page-2?
                         {:text "Review Terms of Service"
                          :onClick #(swap! state assoc :page-2? true)}
                         {:text "Accept"
                          :disabled? (when-not (and terms-agreed? cloud-terms-agreed?)
                                       "You must check the boxes to accept the agreement.")
                          :onClick accept-eula})])}]))})
