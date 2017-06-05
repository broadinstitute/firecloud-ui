(ns broadfcui.common.notifications
  (:require
   [dmohs.react :as react]
   [broadfcui.utils :as utils]
   [broadfcui.config :as config]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.components.top-banner :as top-banner]
   ))

(defn render-alert [{:keys [cleared? link message title]} dismiss]
  (let [text-color "#eee"]
    (top-banner/render
     [:div {:style {:color text-color
                    :background-color (if cleared?
                                        (:success-state style/colors)
                                        (:exception-state style/colors))
                    :padding "1rem"}}
      (when cleared?
        [:div {:style {:float "right"}}
         (icons/icon {:style {:fontSize "80%" :cursor "pointer"} :on-click #(dismiss)} :close)])
      [:div {:style {:display "flex" :align-items "baseline"}}
       [icons/ExceptionIcon {:size 18 :color text-color}]
       [:span {:style {:margin-left "0.5rem" :font-weight "bold" :vertical-align "middle"}}
        (or title "Service Alert")]
       [:span {:style {:color text-color :fontSize "90%" :margin-left "1rem"}}
        message
        (when link
          [:a {:style {:color text-color :margin-left "1rem"} :href link :target "_blank"}
           "Read more..." icons/external-link-icon])]]])))

(defn- status-alert-interval [attempt]
  (cond
    (= attempt 0) (config/status-alerts-refresh)
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
   (fn [{:keys [this state]} & [first-time?]]
     (utils/ajax {:url (config/alerts-json-url)
                  :headers {"Cache-Control" "no-store, no-cache"}
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
           existing (set (filter (complement :cleared?) (:service-alerts @state)))
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
       (when (and (not (empty? new)) (not first-time?))
         (comps/push-message
          {:header "New Service Alert" :message "See the page header for details."}))))
   :-remove-alert
   (fn [{:keys [state]} alert]
     (swap! state update :service-alerts #(filter (partial not= alert) %)))})
