(ns org.broadinstitute.firecloud-ui.page.workspace.summary-tab
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


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
        (let [ws (get-in @state [:server-response :workspace])
              status (ws "status")]
          [:div {:style {:margin "45px 25px"}}
           [:div {:style {:float "left" :width 290 :marginRight 40}}
            ;; TODO - make the width of the float-left dynamic
            [:div {:style {:borderRadius 5 :padding 20 :textAlign "center"
                           :color "#fff"
                           :backgroundColor (style/color-for-status status)
                           :fontSize "125%" :fontWeight 400}}
             (case status
               "Complete" [comps/CompleteIcon {:size 36}]
               "Running" [comps/RunningIcon {:size 36}]
               "Exception" [comps/ExceptionIcon {:size 36}])
             [:span {:style {:marginLeft "1.5ex"}}
              (clojure.string/capitalize (name status))]]
            [:div {:style {:backgroundColor "transparent" :color (:button-blue style/colors)
                           :border (str "1px solid " (:line-gray style/colors))
                           :fontSize "106%" :padding "0.7em 0em" :marginTop 27
                           :cursor "pointer" :textAlign "center"}}
             (icons/font-icon {:style {:verticalAlign "middle" :fontSize "135%"}} :pencil)
             [:span {:style {:verticalAlign "middle" :marginLeft "1em"}} "Edit this page"]]]
           [:div {:style {:display "inline-block"}}
            (style/create-section-header "Workspace Owner")
            (style/create-paragraph
             [:strong {} (ws "createdBy")]
             " ("
             [:a {:href "#" :style {:color (:button-blue style/colors) :textDecoration "none"}}
              "shared with -1 people"]
             ")")
           (style/create-section-header "Description")
           (style/create-paragraph [:em {} "Description info not available yet"])
           (style/create-section-header "Tags")
           (style/create-paragraph (render-tags ["Fake" "Tag" "Placeholders"]))
           (style/create-section-header "Research Purpose")
           (style/create-paragraph [:em {} "Research purpose not available yet"])
           (style/create-section-header "Billing Account")
           (style/create-paragraph [:em {} "Billing account not available yet"])]
          [:div {:style {:clear "both"}}]])))
   :load-workspace
   (fn [{:keys [props state]}]
     (utils/call-ajax-orch
       {:endpoint (endpoints/get-workspace (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc
                       :server-response {:workspace (merge {"status" "Complete"} ;; TODO Remove.
                                                      (get-parsed-response))})
                     (swap! state assoc :server-response {:error-message status-text})))}))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-workspace this))
   :component-did-update
   (fn [{:keys [this state]}]
     (when (nil? (:server-response @state))
       (react/call :load-workspace this)))
   :component-will-receive-props
   (fn [{:keys [props next-props state]}]
     (utils/cljslog props next-props)
     (when-not (apply = (map :workspace-id [props next-props]))
       (swap! state assoc :server-response nil)))})


(defn render [workspace-id]
  (react/create-element Summary {:workspace-id workspace-id}))
