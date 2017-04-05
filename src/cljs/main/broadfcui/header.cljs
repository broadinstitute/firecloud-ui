(ns broadfcui.header
  (:require
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.nih-link-warning :as nih-link-warning]
   [broadfcui.utils :as u]
   [dmohs.react :as r]
   ))

(r/defc TopNavBarLink
  {:render
   (fn [{:keys [props state]}]
     [:a {:href (:href props)
          :style {:padding "1em" :textDecoration "none"
                  :fontWeight (when (:selected props) "bold")
                  :color (if (:hovering? @state) (:link-active style/colors) "black")}
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
                              :selected ((:is-selected? item))}])
            (:items props))
       (when (:show-nih-link-warning? props)
         [nih-link-warning/NihLinkWarning])]])})

(r/defc AccountDropdown
  {:render
   (fn [{:keys [props state]}]
     [:div {:style {:float "right" :position "relative" :marginBottom "0.4rem"}}
      (when (:show-dropdown? @state)
        [:div {:style {:position "fixed" :top 0 :left 0 :right 0 :bottom 0}
               :onClick #(swap! state assoc :show-dropdown? false)}])
      [:a {:href "javascript:;"
           :onClick #(swap! state assoc :show-dropdown? true)
           :style {:display "block"
                   :borderRadius 2
                   :backgroundColor (:background-light style/colors)
                   :color "#000" :textDecoration "none"
                   :padding "0.5rem" :border style/standard-line
                   :minWidth 100}}
       [:div {}
        (-> (:auth2 props) (.-currentUser) (.get) (.getBasicProfile) (.getEmail))
        [:div {:style {:display "inline-block" :marginLeft "1em" :fontSize 8}} "▼"]]]
      (when (:show-dropdown? @state)
        (let [DropdownItem
              (r/create-class
               {:render
                (fn [{:keys [props state]}]
                  [:a {:style {:display "block"
                               :color "#000" :textDecoration "none" :fontSize 14
                               :padding "0.5rem 1.3rem 0.5rem 0.5rem"
                               :backgroundColor (when (:hovering? @state) "#e8f5ff")}
                       :href (:href props)
                       :onMouseOver #(swap! state assoc :hovering? true)
                       :onMouseOut #(swap! state assoc :hovering? false)
                       :onClick (:dismiss props)}
                   (:text props)])})]
          [:div {:style {:boxShadow "0px 3px 6px 0px rgba(0, 0, 0, 0.15)"
                         :backgroundColor "#fff"
                         :position "absolute" :width "100%"
                         :border (str "1px solid " (:line-default style/colors))}}
           [DropdownItem {:href (nav/get-link :broadfcui.page.profile/main) :text "Profile"
                          :dismiss #(swap! state assoc :show-dropdown? false)}]
           [DropdownItem {:href (nav/get-link :broadfcui.page.billing.billing-management/main)
                          :text "Billing"
                          :dismiss #(swap! state assoc :show-dropdown? false)}]
           [DropdownItem {:href "javascript:;" :text "Sign Out"
                          :dismiss #(.signOut (:auth2 props))}]]))])})

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
