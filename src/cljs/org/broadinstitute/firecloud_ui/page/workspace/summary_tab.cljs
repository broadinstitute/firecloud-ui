(ns org.broadinstitute.firecloud-ui.page.workspace.summary-tab
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.paths :as paths]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- create-section-header [text]
  [:div {:style {:fontSize "125%" :fontWeight 500}} text])

(defn- create-paragraph [& children]
  [:div {:style {:margin "17px 0 0.33333em 0" :paddingBottom "2.5em"
                 :fontSize "90%" :lineHeight 1.5}}
   children])

(defn- render-tags [tags]
  (let [tagstyle {:marginRight 13 :borderRadius 2 :padding "5px 13px"
                  :backgroundColor (:tag-background style/colors)
                  :color (:tag-foreground style/colors)
                  :display "inline-block" :fontSize "94%"}]
    [:div {}
     (map (fn [tag] [:span {:style tagstyle} tag]) tags)]))


(react/defc Summary
  {:render
   (fn [{:keys [state]}]
     (cond
        (nil? (:server-response @state))
        [comps/Spinner {:text "Loading workspace..."}]
        (get-in @state [:server-response :error-message])
        (style/create-server-error-message (get-in @state [:server-response :error-message]))
        :else
        (let [ws (get-in @state [:server-response :workspace])]
          [:div {:style {:margin "45px 25px"}}
           [:div {:style {:float "left" :display "inline-block" :width 290 :marginRight 40}}
            ;; TODO - make the width of the float-left dynamic
            [:div {:style {:borderRadius 5 :padding 25 :textAlign "center"
                           :color "#fff"
                           :backgroundColor (style/color-for-status (ws "status"))
                           :fontSize "125%" :fontWeight 400}}
             [:span {:style {:display "inline-block" :marginRight 14 :marginTop -4
                             :verticalAlign "middle" :position "relative"}}
              (case (ws "status")
                "Complete" [comps/CompleteIcon {:size 36}]
                "Running" [comps/RunningIcon {:size 36}]
                "Exception" [comps/ExceptionIcon {:size 36}])]
             [:span {:style {:marginLeft "1.5ex"}} (ws "status")]]
            [:div {:style {:marginTop 27}}
             [:div {:style {:backgroundColor "transparent" :color (:button-blue style/colors)
                            :border (str "1px solid " (:line-gray style/colors))
                            :fontSize "106%" :lineHeight 1 :position "relative"
                            :padding "0.7em 0em"
                            :textAlign "center" :cursor "pointer"}}
              [:span {:style {:display "inline-block" :verticalAlign "middle"}}
               (icons/font-icon {:style {:fontSize "135%"}} :pencil)]
              [:span {:style {:marginLeft "1em"}} "Edit this page"]]]]
           [:div {:style {:display "inline-block"}}
            (create-section-header "Workspace Owner")
            (create-paragraph
             [:strong {} (ws "createdBy")]
             " ("
             [:a {:href "#" :style {:color (:button-blue style/colors) :textDecoration "none"}}
              "shared with -1 people"]
             ")")
            (create-section-header "Description")
            (create-paragraph [:em {} "Description info not available yet"])
            (create-section-header "Tags")
            (create-paragraph (render-tags ["Fake" "Tag" "Placeholders"]))
            (create-section-header "Research Purpose")
            (create-paragraph [:em {} "Research purpose not available yet"])
            (create-section-header "Billing Account")
            (create-paragraph [:em {} "Billing account not available yet"])]
           [:div {:style {:clear "both"}}]])))
   :load-workspace
   (fn [{:keys [props state]}]
     (utils/call-ajax-orch
      (paths/workspace-details-path (:workspace-id props))
      {:on-success
       (fn [{:keys [parsed-response]}]
         (swap! state assoc
                :server-response {:workspace (merge {"status" "Complete"} ;; TODO Remove.
                                                    parsed-response)}))
       :on-failure (fn [{:keys [status-text]}]
                     (swap! state assoc :server-response {:error-message status-text}))
       :mock-data (merge (:workspace-id props) {:status "Complete" :createdBy "Nobody"})}))
   :component-did-mount
   (fn [{:keys [this state]}]
     (react/call :load-workspace this))
   :component-did-update
   (fn [{:keys [this state]}]
     (when (nil? (:server-response @state))
       (react/call :load-workspace this)))
   :component-will-receive-props
   (fn [{:keys [this props next-props state]}]
     (utils/cljslog props next-props)
     (when-not (apply = (map :workspace-id [props next-props]))
       (swap! state assoc :server-response nil)))})


(defn render [workspace-id]
  (react/create-element Summary {:workspace-id workspace-id}))
