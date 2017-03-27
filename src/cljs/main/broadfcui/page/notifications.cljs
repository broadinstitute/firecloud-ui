(ns broadfcui.page.notifications
  (:require
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   [dmohs.react :as r]
   ))

(r/defc Page
  {:render
   (fn [{:keys [this state]}]
     [:div {}
      [:h1 {} "Notifications"]
      (this
       :-render-ajax-or-continue
       (fn [notifications notifications-state]
         (let [find-notification (fn [k]
                                   (first (filter #(= k (:notificationKey %)) notifications)))
               is-checked? (fn [k] (get notifications-state k))
               set-checked? (fn [k value]
                              (swap! state assoc-in [:notifications-state k] value))
               checkbox (fn [k]
                          (common/render-foundation-switch
                           {:checked? (is-checked? k) :on-change (partial set-checked? k)}))
               row (fn [{:keys [description notificationKey]}]
                     [:tr {}
                      [:td {} (checkbox notificationKey)]
                      [:td {:style {:padding "0.3rem 0 0.3rem 1rem"}} description]])]
           [:div {}
            [:table {}
             [:tbody {}
              (map row notifications)]]
            [comps/Button {:style {:marginTop "1rem"} :text "Save"}]])))])
   :component-did-mount
   (fn [{:keys [state]}]
     (utils/ajax-orch
      "/notifications/general"
      {:on-done (fn [{:keys [raw-response] :as m}]
                  (let [[parsed error] (utils/parse-json-string raw-response true false)
                        notifications-state (reduce (fn [r k] (assoc r k true))
                                                    {}
                                                    (map :notificationKey parsed))]
                    (if error
                      (swap! state assoc :server-response (assoc m :parse-error? true))
                      (swap! state assoc
                             :server-response (assoc m :parsed parsed)
                             :notifications-state notifications-state))))}))
   :-render-ajax-or-continue
   (fn [{:keys [state]} f]
     (let [{:keys [notifications-state server-response]} @state
           show-error (fn [message]
                        [:div {:style {:color (:exception-state style/colors)}}
                         "Error when retrieving notifications: " message])]
       (cond
         (not server-response) [comps/Spinner {:text "Loading notifications..."}]
         (not (:success? server-response))
         (show-error (:status-text server-response))
         (:parse-error? server-response) (show-error "Failed to parse response")
         :else (f (:parsed server-response) notifications-state))))})

(defn add-nav-paths []
  (nav/defpath
    :notifications
    {:component Page
     :regex #"notifications"
     :make-props (constantly nil)
     :make-path (constantly "notifications")}))
