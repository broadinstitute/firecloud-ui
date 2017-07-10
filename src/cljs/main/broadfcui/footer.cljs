(ns broadfcui.footer
  (:require
    [dmohs.react :as react]
    [broadfcui.common.style :as style]
    [broadfcui.config :as config]
    [broadfcui.nav :as nav]
    [broadfcui.utils :as utils]
    ))

(react/defc PopUpFooterControl
  {:render
   (fn [{:keys [state]}]
     [:div {:style {:minWidth 50 :minHeight 20}
            :onMouseOver #(swap! state assoc :visible? true)
            :onMouseOut #(swap! state dissoc :visible?)}
      [:div {:style {:display (when-not (or (:visible? @state) (not @utils/use-live-data?)) "none")
                     :padding 20 :paddingBottom 10 :margin -20 :marginBottom -10}}
       [:div {}
        "Fake data: "
        [:a {:href "javascript:;"
             :style {:textDecoration "none" :color (if @utils/use-live-data? "green" "red")}
             :onClick #(do (swap! utils/use-live-data? not) (swap! state assoc :foo 1))}
         (if @utils/use-live-data? "off" "on")]]
       [:div {}
        [:a {:href "#styles" :style {:textDecoration "none" :display "block"}} "Style Guide"]
        [:a {:href "#status" :style {:textDecoration "none"}} "Status Page"]]]])})

(defn render-footer []
  (let [thisyear (.getFullYear (js/Date.))
        startyear 2015
        yeartext (if (= startyear thisyear) (str startyear) (str startyear "-" thisyear))
        spacer [:span {:style {:padding "0 0.6em"}} "|"]
        Link (react/create-class
              {:render
               (fn [{:keys [props state]}]
                 [:a {:href (:href props)
                      :target (:target props)
                      :style {:color (:text-lightest style/colors)
                              :textDecoration (when-not (:hovering? @state) "none")}
                      :onMouseOver #(swap! state assoc :hovering? true)
                      :onMouseOut #(swap! state assoc :hovering? false)}
                  (:text props)])})]
    [:div {:style {:borderTop (str "2px solid " (:line-default style/colors))
                   :padding "1em 25px 2em 25px"
                   :color (:text-lightest style/colors) :fontSize "90%"}}
     (when (config/debug?)
       [:div {:style {:float "right"}} [PopUpFooterControl]])
     [:div {:style {:display "block"}}
      (str "\u00A9 " yeartext " Broad Institute")
      spacer
      [Link {:href (nav/get-link :policy) :text "Privacy Policy"}]
      spacer
      [Link {:href "http://gatkforums.broadinstitute.org/firecloud/discussion/6819/firecloud-terms-of-service#latest"
             :text "Terms of Service" :target "_blank"}]
      spacer
      [Link {:href (config/user-guide-url) :text "User Guide"
             :target "_blank"}]
      spacer
      [Link {:href (config/forum-url) :text "FireCloud Forum"
             :target "_blank"}]]]))
