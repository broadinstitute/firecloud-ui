(ns broadfcui.components.tab-bar
  (:require
   [dmohs.react :as react]
   [broadfcui.common.style :as style]
   [broadfcui.config :as config]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   ))

(react/defc- Tab
  {:render
   (fn [{:keys [props state]}]
     [:a {:style {:flex "0 0 auto" :padding "1em 2em"
                  :borderLeft (when (:first? props) style/standard-line)
                  :borderRight style/standard-line
                  :backgroundColor (when (:active? props) "white")
                  :cursor "pointer" :textDecoration "none" :color "inherit"
                  :position "relative"}
          :href (:href props)
          :data-test-id (:data-test-id props)
          :onMouseOver #(swap! state assoc :hovering? true)
          :onMouseOut #(swap! state dissoc :hovering?)
          :onClick (:on-refresh props)}
      (:label props)
      (when (or (:active? props) (:hovering? @state))
        [:div {:style {:position "absolute" :top "-0.25rem" :left 0
                       :width "100%" :height "0.25rem"
                       :backgroundColor (:button-primary style/colors)}}])
      (when (:active? props)
        [:div {:style {:position "absolute" :bottom -1 :left 0 :width "100%" :height 2
                       :backgroundColor "white"}}])])})

(defn render-title [label title]
  [:div {}
   [:div {:style {:fontSize "80%"}} label]
   [:span {:style {:fontWeight 500 :fontSize "125%"}} title]])

(defn- make-tab [{:keys [label first? active? link-key context-id refresh-tab request-refresh on-click]}]
  [Tab (merge
        {:label label :first? first? :active? active?
         :data-test-id (str label "-tab")
         :on-refresh #(when active?
                        (request-refresh)
                        (refresh-tab label))}
        (if on-click
          {:onClick #(on-click link-key)}
          {:href (nav/get-link link-key context-id)}))])

(defn create-bar [{:keys [tabs active-tab context-id refresh-tab request-refresh on-click]}]
  [:div {:style {:marginTop "1rem"
                 :display "flex" :backgroundColor (:background-light style/colors)
                 :borderTop style/standard-line :borderBottom style/standard-line
                 :padding "0 1.5rem" :justifyContent "space-between"}}
   [:div {:style {:display "flex"}}
    (map-indexed
     (fn [index [label link-key]]
       (make-tab (merge {:first? (= index 0)
                         :active? (= label active-tab)}
                        (utils/restructure label link-key context-id refresh-tab request-refresh on-click))))
     tabs)]])
