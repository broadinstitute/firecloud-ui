(ns broadfcui.common.notifications
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
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
         (comps/push-message
          {:header "New Service Alert" :message "See the page header for details."}))))
   :-remove-alert
   (fn [{:keys [state]} alert]
     (swap! state update :service-alerts #(filter (partial not= alert) %)))})

(react/defc TrialAlertContainer
  {:render
   (fn [{:keys [state]}]
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
                [modals/OKCancelForm
                 {:header "User License Agreement"
                  :content [:div {:style {:backgroundColor "white" :padding "1rem"
                                          :maxHeight 500 :overflow "auto" :whiteSpace "pre-wrap"}}
                            eula]
                  :data-test-id "message-modal"
                  :cancel-text "Refuse"
                  :ok-button {:text "Accept"
                              :onClick (fn []
                                         (utils/ajax-orch
                                          "/profile/trial/userAgreement"
                                          {:method :put
                                           :on-done (fn [{:keys [success?]}]
                                                      (if-not success?
                                                        (utils/multi-swap! state (assoc :error "An error occurred. Please try again.")
                                                                           (dissoc :loading?))
                                                        (utils/ajax-orch
                                                         "/profile/trial"
                                                         {:method :post
                                                          :on-done (fn [{:keys [success? get-parsed-response]}]
                                                                     (if success?
                                                                       (do
                                                                         (user-info/reload-user-profile
                                                                          #(swap! state dissoc :loading?))
                                                                         (user-info/reload-billing-projects))
                                                                       (utils/multi-swap! state (assoc :error (:message (get-parsed-response)))
                                                                                          (dissoc :loading?))))})))})
                                         (utils/multi-swap! state (assoc :loading? true)
                                                            (dissoc :displaying-eula?)))}}]
                :error
                (modals/render-error {:text error})})))))))
   :component-will-mount
   (fn [{:keys [this state]}]
     (add-watch user-info/saved-user-profile :trial-alerts
                (fn [_ _ _ {:keys [trialState]}]
                  (when trialState
                    (if-not (:messages @state)
                      (utils/get-google-bucket-file "trial" #(swap! state assoc :messages %))
                      (.forceUpdate this))))))})
