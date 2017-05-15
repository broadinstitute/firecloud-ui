(ns broadfcui.common.notifications
  (:require
    [dmohs.react :as react]
    [broadfcui.utils :as utils]
    [broadfcui.config :as config]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.style :as style]
    ))

(react/defc Banner
            {:get-initial-state
             (fn []
               {:showing-more? false})
             :render
             (fn [{:keys [props state]}]
               (let [{:keys [background-color text-color title message link more-content]} props]
                 [:div {:style {:color text-color :backgroundColor background-color :padding "1rem"}}
                  [:div {:style {:display "flex" :alignItems "baseline"}}
                   [icons/ExceptionIcon {:size 18 :color text-color}]
                   [:span {:style {:marginLeft "0.5rem" :fontWeight "bold"
                                   :verticalAlign "middle"}}
                    (or title "Service Alert")]
                   [:span {:style {:color text-color :fontSize "90%" :marginLeft "1rem"}}
                    (str message " ")
                    (if more-content
                      [:a {:style {:color "#000"}
                           :href "javascript:;"
                           :onClick #(swap! state update :showing-more? not)}
                       (if (:showing-more? @state) " Hide details..." " Show details...")])
                    (when (:showing-more? @state) more-content) link]]]))})

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
             (fn [{:keys [state]}]
               (let [{:keys [service-alerts]} @state]
                 [:div {}
                  (map (fn [alert]
                         [Banner (merge (select-keys alert [:title :message])
                                        {:background-color (:exception-state style/colors)
                                         :text-color "#fff"
                                         :link (when-let [link (:link alert)]
                                                 [:a {:style {:color "#fff"} :href (str link)
                                                      :target "_blank"}
                                                  "Read more..." icons/external-link-icon])})])
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
                                           (if (not-empty parsed)
                                             (swap! state assoc :service-alerts parsed :failed-retries 0)
                                             (swap! state dissoc :service-alerts)))))}))})
