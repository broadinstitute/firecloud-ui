(ns broadfcui.header
  (:require
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   [dmohs.react :as r]
   ))

(r/defc TopNavBarLink
  {:render
   (fn [{:keys [props state]}]
     [:a {:href (:href props)
          :style {:padding "1em" :textDecoration "none"
                  :fontWeight (when (:selected props) "bold")
                  :color (if (:hovering? @state) (:button-primary style/colors) "black")}
          :data-test-id (:data-test-id props)
          :onMouseOver #(swap! state assoc :hovering? true)
          :onMouseOut #(swap! state assoc :hovering? false)}
      (:name props)])})

(r/defc TopNavBar
  {:render
   (fn [{:keys [props]}]
     [:div {}
      (style/render-text-logo)
      [:div {:style {:display "inline-block" :paddingLeft "1em" :fontSize 18 :height 38
                     :verticalAlign "baseline"}}
       (map (fn [item]
              [TopNavBarLink {:name (:label item) :href (nav/get-link (:nav-key item))
                              :selected ((:is-selected? item))
                              :data-test-id (:data-test-id item)}])
            (:items props))]])})

(defn create-account-dropdown []
  (let [auth2 @utils/auth2-atom]
    (common/render-dropdown-menu
     {:label [:div {:style {:borderRadius 2
                            :backgroundColor (:background-light style/colors)
                            :color "#000" :textDecoration "none"
                            :padding "0.5rem" :border style/standard-line}
                    :data-test-id "account-dropdown"}
              (-> auth2 (.-currentUser) (.get) (.getBasicProfile) (.getEmail))
              [:div {:style {:display "inline-block" :marginLeft "1em" :fontSize 8}} "▼"]]
      :width :auto
      :button-style {:height 32}
      :items [{:href (nav/get-link :profile) :text "Profile"}
              {:href (nav/get-link :groups) :text "Groups"}
              {:href (nav/get-link :billing) :text "Billing"}
              {:href (nav/get-link :notifications) :text "Notifications"}
              {:text "Sign Out" :dismiss #(.signOut auth2) :data-test-id "sign-out"}]})))

(r/defc GlobalSubmissionStatus
  {:render
   (fn [{:keys [state]}]
     (let [{:keys [status-error status-code status-counts]} @state
           {:keys [queued active queue-position]} status-counts]
       (when-not (= status-code 401) ; to avoid displaying "Workflows: Unauthorized"
         [:div {}
          (str "Workflows: "
               (cond status-error status-error
                 status-counts (str queued " Queued; " active " Active; "
                                    queue-position " ahead of yours")
                 :else "loading..."))])))
   :component-did-mount
   (fn [{:keys [this locals]}]
     ;; Call once for initial load
     (this :load-data)
     ;; Add a long-polling call for continuous updates
     (swap! locals assoc :interval-id
            (js/setInterval #(this :load-data) (config/submission-status-refresh))))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (js/clearInterval (:interval-id @locals)))
   :load-data
   (fn [{:keys [state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/submissions-queue-status)
       :on-done (fn [{:keys [success? status-text status-code get-parsed-response]}]
                  (if success?
                    (swap! state assoc :status-error nil :status-code nil
                           :status-counts (common/queue-status-counts (get-parsed-response false)))
                    (swap! state assoc :status-error status-text :status-code status-code
                           :status-counts nil)))}))})
