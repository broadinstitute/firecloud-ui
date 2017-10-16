(ns broadfcui.page.notifications
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.foundation-dropdown :as dropdown]
   [broadfcui.components.foundation-switch :refer [render-foundation-switch]]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   ))

(def notification-details
  {"WorkspaceChangedNotification"
   {:label "Data Added or Changed"
    :description (str "A Writer or Owner can trigger this notification when data is added to or"
                      " changed within this workspace.")}})

(defn- profile-response->map [notification-keys profile-response]
  (->> (:keyValuePairs profile-response)
       (filter #(contains? notification-keys (:key %)))
       (reduce #(assoc %1 (:key %2) (if (= "false" (:value %2)) false true)) {})))

(defn- handle-ajax-response [{:keys [state]} k {:keys [raw-response] :as m}]
  (let [[parsed error] (utils/parse-json-string raw-response true false)]
    (if error
      (swap! state assoc k (assoc m :parse-error? true))
      (swap! state assoc k (assoc m :parsed parsed)))))

(defn- start-ajax-calls [{:keys [props] :as component-attributes} & [did-get-profile]]
  (let [{:keys [workspace-id]} props
        {:keys [namespace name]} workspace-id
        path (if workspace-id
               (str "/notifications/workspace/"
                    (js/encodeURIComponent namespace) "/" (js/encodeURIComponent name))
               "/notifications/general")]
    (utils/ajax-orch
     path
     {:on-done (partial handle-ajax-response component-attributes :general-response)})
    (utils/ajax-orch
     "/profile"
     {:on-done (fn [m]
                 (handle-ajax-response component-attributes :profile-response m)
                 (when did-get-profile
                   (did-get-profile)))}
     :service-prefix "/register")))

(defn- render-ajax-or-continue [{:keys [state]} f]
  (let [{:keys [general-response profile-response]} @state
        show-error (fn [message]
                     [:div {:style {:color (:state-exception style/colors)}}
                      "Error when retrieving notifications: " message])]
    (cond
      (not (and general-response profile-response))
      [comps/Spinner {:text "Loading notifications..."}]
      (not (:success? general-response))
      (show-error (:status-text general-response))
      (not (:success? profile-response))
      (show-error (:status-text profile-response))
      (or (:parse-error? general-response) (:parse-error? profile-response))
      (show-error "Failed to parse response")
      :else (f (:parsed general-response) (:parsed profile-response)))))

(defn- collect-notification-state [general-response profile-response overrides]
  (let [general (:parsed general-response)
        notification-keys (set (map :notificationKey general))
        prefs (profile-response->map notification-keys (:parsed profile-response))]
    (sort-by :key
             (map (fn [x]
                    (let [k (:notificationKey x)]
                      (merge (select-keys x [:description])
                             {:key k
                              :value (if (contains? overrides k)
                                       (get overrides k)
                                       (if (false? (get prefs k)) false true))})))
                  general))))

(defn- save [data on-done]
  (utils/ajax-orch
   "/profile/preferences"
   {:method :post
    :headers utils/content-type=json
    :data (js-invoke js/JSON "stringify" (clj->js data))
    :on-done on-done}))

(react/defc- Page
  {:render
   (fn [{:keys [this] :as m}]
     [:div {:style style/thin-page-style}
      [:h2 {} "Account Notifications"]
      (render-ajax-or-continue m #(this :-render-notifications))])
   :component-did-mount
   (fn [m]
     (start-ajax-calls m))
   :-render-notifications
   (fn [{:keys [this state]}]
     (let [{:keys [overridden]} @state
           notifications (collect-notification-state
                          (:general-response @state) (:profile-response @state) overridden)
           set-checked? (fn [k value] (swap! state assoc-in [:overridden k] value))
           checkbox (fn [k checked?]
                      [:input {:type "checkbox" :checked checked?
                               :onChange #(set-checked? k (aget % "target" "checked"))}])
           row (fn [{:keys [description key value]}]
                 [:tr {}
                  [:label {}
                   [:td {} (checkbox key value)]
                   [:td {:style {:paddingLeft "0.5rem"}} description]]])]
       [:div {}
        [:table {:style {:fontSize "90%"}}
         [:tbody {}
          (map row notifications)]]
        [:div {:style {:marginTop "1rem" :display "flex" :alignItems "center"}}
         [buttons/Button {:text "Save" :disabled? (zero? (count overridden))
                          :onClick #(this :-save)}]
         [:div {:style {:marginLeft "1rem"}}
          (cond
            (:saving? @state) (icons/icon {:className "fa-pulse fa-lg fa-fw"} :spinner)
            (:error? @state) (icons/icon {:style {:color (:state-exception style/colors)}}
                                         :error)
            (:saved? @state) (icons/icon {:style {:color (:state-success style/colors)}}
                                         :done))]]]))
   :-save
   (fn [{:keys [state after-update] :as m}]
     (swap! state assoc :saving? true :error? false)
     (let [notifications (collect-notification-state
                          (:general-response @state) (:profile-response @state)
                          (:overridden @state))
           profile (apply merge (map (fn [x] {(:key x) (str (boolean (:value x)))}) notifications))]
       (save
        profile
        (fn [{:keys [success?]}]
          (swap! state dissoc :saving?)
          (if success?
            (do
              (swap! state assoc :saved? true)
              (start-ajax-calls m #(swap! state assoc :overridden {}))
              (after-update (fn [] (js/setTimeout #(swap! state dissoc :saved?) 1000))))
            (swap! state assoc :error? true))))))})

(defn- parse-ws-notification-key [k]
  (let [[_ id ws-namespace ws-name] (clojure.string/split k #"/")]
    {:id id :workspace-id {:namespace ws-namespace :name ws-name}}))

(react/defc WorkspaceComponent
  {:get-initial-state
   (fn []
     {:save-disabled? true})
   :render
   (fn [{:keys [this] :as m}]
     [:div {}
      [:div {:style {:borderBottom style/standard-line
                     :padding "0 1rem 0.5rem" :margin "0 -1rem 0.5rem"
                     :fontWeight 500}}
       "Workspace Notifications"]
      (render-ajax-or-continue m #(this :-render-notifications))])
   :component-did-mount
   (fn [m]
     (start-ajax-calls m))
   :-render-notifications
   (fn [{:keys [this state after-update]}]
     (let [{:keys [overridden pending]} @state
           notifications (collect-notification-state
                          (:general-response @state) (:profile-response @state) overridden)
           set-checked? (fn [k value]
                          (swap! state assoc-in [:overridden k] value)
                          (swap! state assoc-in [:pending k] true)
                          (after-update #(this :-save k)))
           checkbox (fn [k checked?]
                      (render-foundation-switch
                       {:checked? checked? :on-change (partial set-checked? k)}))
           row (fn [{:keys [key value]}]
                 (let [{:keys [id]} (parse-ws-notification-key key)
                       {:keys [label description]} (notification-details id)]
                   [:tr {}
                    [:td {} (checkbox key value)]
                    [:td {:style {:padding "0.5rem"}} label]
                    [:td {} (dropdown/render-info-box {:text description})]
                    [:td {} (when-let [pending (get pending key)]
                              (case pending
                                :error
                                (icons/icon {:style {:color (:state-exception style/colors)}}
                                            :error)
                                :done
                                (icons/icon {:style {:color (:state-success style/colors)}}
                                            :done)
                                (icons/icon {:className "fa-pulse fa-lg fa-fw"} :spinner)))]]))]
       [:table {:style {:fontSize "90%"}}
        [:tbody {}
         (map row notifications)]]))
   :-save
   (fn [{:keys [state after-update]} k]
     (save
      {k (str (boolean (get-in @state [:overridden k])))}
      (fn [{:keys [success?]}]
        (if success?
          (do
            (swap! state assoc-in [:pending k] :done)
            (after-update
             (fn [] (js/setTimeout #(swap! state update :pending dissoc k) 1000))))
          (do
            (swap! state assoc-in [:pending k] :error)
            (swap! state update-in [:overridden k] not))))))})

(defn add-nav-paths []
  (nav/defpath
   :notifications
   {:component Page
    :regex #"notifications"
    :make-props (constantly nil)
    :make-path (constantly "notifications")}))
