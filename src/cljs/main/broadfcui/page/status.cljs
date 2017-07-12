(ns broadfcui.page.status
  (:require
    [dmohs.react :as react]
    [broadfcui.common.components :as comps]
    [broadfcui.common.style :as style]
    [broadfcui.config :as config]
    [broadfcui.nav :as nav]
    [broadfcui.utils :as utils]
    ))


(react/defc StatusLine
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:marginTop "0.5em"}}
      [:div {}
       (:label props) ": "
       (cond
         (nil? (:success? props))
         [comps/Spinner]
         (not (:success? props))
         [:span {} [:span {:style {:color "red"}} "Error"]
          (when (:errors props)
            [:div {:style {:backgroundColor "#eee" :padding 6 :fontSize "smaller"}}
              [:ul {} (map (fn [e] [:li {} e]) (:errors props))]])]
         :else
         [:span {:style {:color "green"}} "Okay"])]])})

(react/defc Page
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [status-code status-text response-text parsed-response]} @state
           orch-ok? (:ok parsed-response)
           orch-errors [(str "Status Code: " status-code)
                        (str "Status Text: " status-text)
                        (str "Response Text: " response-text)]]
       [:div {:style {:padding "1em"}}
        [:h2 {} "Service Status"]
        [:div {}
         [StatusLine {:label "Orchestration" :success? orch-ok? :errors orch-errors}]
         (map (fn [system]
                (let [current (first system)
                      {:keys [messages ok]} (get-in parsed-response [:systems current])]
                  [StatusLine {:label (name current) :success? ok :errors messages}]))
              (:systems parsed-response))]]))
   :component-did-mount
   (fn [{:keys [props state]}]
     (utils/ajax {:url (str (config/api-url-root) "/status")
                  :method "GET"
                  :on-done (fn [{:keys [status-code status-text raw-response get-parsed-response]}]
                             (swap! state assoc
                                    :status-code status-code
                                    :status-text status-text
                                    :response-text raw-response
                                    :parsed-response (get-parsed-response)))}))})

(defn add-nav-paths []
  (nav/defpath
   :status
   {:component Page
    :regex #"status"
    :make-props (fn [] {})
    :make-path (fn [] "status")}))
