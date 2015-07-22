(ns org.broadinstitute.firecloud-ui.page.workspace.workspace-summary
  (:require
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]))


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

(defn render-workspace-summary [workspace]
  [:div {:style {:margin "45px 25px"}}
   [:div {:style {:position "relative" :float "left" :display "inline-block"
                  :top 0 :left 0 :width 290 :marginRight 40}}
    ;; TODO - make the width of the float-left dynamic
    [:div {:style {:borderRadius 5 :padding 25 :textAlign "center"
                   :color "#fff" :backgroundColor (style/color-for-status (workspace "status"))
                   :fontSize "125%" :fontWeight 400}}
     [:span {:style {:display "inline-block" :marginRight 14 :marginTop -4
                     :verticalAlign "middle" :position "relative"}}
      (case (workspace "status")
        "Complete"  [comps/CompleteIcon {:size 36}]
        "Running"   [comps/RunningIcon {:size 36}]
        "Exception" [comps/ExceptionIcon {:size 36}])]
     [:span {:style {:marginLeft "1.5ex"}} (workspace "status")]
     ]
    [:div {:style {:marginTop 27}}
     [:div {:style {:backgroundColor "transparent" :color (:button-blue style/colors)
                    :border (str "1px solid " (:line-gray style/colors))
                    :fontSize "106%" :lineHeight 1 :position "relative"
                    :padding "0.7em 0em"
                    :textAlign "center" :cursor "pointer"
                    :textDecoration "none"}}
      [:span {:style {:display "inline-block" :verticalAlign "middle"}}
       (icons/font-icon {:style {:fontSize "135%"}} :pencil)]
      [:span {:style {:marginLeft "1em"}} "Edit this page"]]]]
   [:div {:style {:display "inline-block"}}
    (create-section-header "Workspace Owner")
    (create-paragraph
      [:strong {} (workspace "createdBy")]
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
   [:div {:style {:clear "both"}}]])
