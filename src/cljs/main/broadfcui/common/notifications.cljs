(ns broadfcui.common.notifications
  (:require
   [dmohs.react :as react]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.markdown :as markdown]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.foundation-tooltip :refer [FoundationTooltip]]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.components.top-banner :as top-banner]
   [broadfcui.config :as config]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   [broadfcui.utils.user :as user]
   ))

(defn render-alert [{:keys [cleared? link message title link-title severity]} dismiss]
  (let [text-color (case severity
                     :info (:text-lighter style/colors)
                     "#eee")
        background-color (case severity
                           :info (:background-light style/colors)
                           (if cleared?
                             (:state-success style/colors)
                             (:state-exception style/colors)))]
    (top-banner/render
      [:div {:style {:color text-color
                     :background-color background-color
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
                                  (or link-title "Read more...")))]]])))

(defn- status-alert-interval [attempt]
  (cond
    (zero? attempt) (config/status-alerts-refresh)
    (> attempt (config/max-retry-attempts)) (config/status-alerts-refresh)
    :else (ajax/get-exponential-backoff-interval attempt)))

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
     (ajax/call {:url (config/google-bucket-url "alerts")
                 :on-done (partial this :-handle-response first-time?)}))
   :-handle-response
   (fn [{:keys [state]} first-time? {:keys [status-code raw-response]}]
     (let [alerts (if (and (ajax/check-server-down status-code)
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
              :failed-retries (if (ajax/check-server-down status-code)
                                (inc (:failed-retries @state))
                                0))
       (when (and (seq new) (not first-time?))
         (swap! state assoc :show-new-service-alert-message? true))))
   :-remove-alert
   (fn [{:keys [state]} alert]
     (swap! state update :service-alerts #(filter (partial not= alert) %)))})

(react/defc TerraBanner
  {:render
    (fn [{:keys [state this]}]
      (let [{:keys [dismissed?]} @state]
        (when (not dismissed?)
          [:div {}
            [:div {:style {:height "66px"
                          :line-height "66px"
                          :width "100%"
                          :position "relative"
                          :overflow "hidden"
                          :white-space "nowrap"
                          :border-bottom "2px solid rgb(176, 210, 57)"
                          :box-shadow "rgba(0, 0, 0, 0.12) 0px 3px 2px 0px"
                          :background "81px url('assets/header-left-hexes.svg') no-repeat,
                                      right url('assets/header-right-hexes.svg') no-repeat, rgb(116, 174, 67)"}}
                [:img {:src "assets/terra-logo.svg"
                      :style {:height "56px"
                              :width "60px"
                              :float "left"
                              :margin "4px 10px"}}]
                [:span {:style {:color "white"
                                :height "66px"
                                :display "inline-block"
                                :vertical-align "middle"
                                :margin-left "112px"
                                :text-align "center"
                                :font-weight "500"}}
                  "On May 1st FireCloud will get a new look as it becomes "
                  [:a {:href "https://software.broadinstitute.org/firecloud/blog?id=23627"
                      :target "_blank"
                      :style {:color "white"}}
                    "powered by Terra."]
                  " Please click "
                  [:a {:href "http://app.terra.bio/"
                      :target "_blank"
                      :style {:color "white"}}
                    "here"]
                  " to test-drive the new experience."]]
                  [:div {:style {:alignSelf "center"
                                :padding "1rem"
                                :position "absolute"
                                :top "0px"
                                :right "0px"}}
                    [FoundationTooltip
                      {:tooltip "Hide for now"
                      :style {:borderBottom "none"}
                      :position "left"
                      :data-hover-delay 0
                      :text [:button {:className "button-reset" :onClick #(swap! state assoc :dismissed? true)
                                      :style {:display "block" :fontSize "1.5rem"
                                              :color "white" :cursor "pointer"}}
                              (icons/render-icon {} :close)]}]]])))})
