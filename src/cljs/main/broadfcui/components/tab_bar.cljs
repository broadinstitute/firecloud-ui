(ns broadfcui.components.tab-bar
  (:require
   [dmohs.react :as react]
   [broadfcui.common.style :as style]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   ))

(react/defc- Tab
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [data-test-id first? active? label href on-refresh]} props]
       [:a {:data-test-id data-test-id
            :style {:flex "0 0 auto" :padding "1em 2em"
                    :borderLeft (when first? style/standard-line)
                    :borderRight style/standard-line
                    :backgroundColor (when active? "white")
                    :cursor "pointer" :textDecoration "none" :color "inherit"
                    :position "relative"}
            :href href
            :onMouseOver #(swap! state assoc :hovering? true)
            :onMouseOut #(swap! state dissoc :hovering?)
            :onClick on-refresh}
        label
        (when (or active? (:hovering? @state))
          [:div {:style {:position "absolute" :top "-0.25rem" :left 0
                         :width "100%" :height "0.25rem"
                         :backgroundColor (:button-primary style/colors)}}])
        (when active?
          [:div {:style {:position "absolute" :bottom -1 :left 0 :width "100%" :height 2
                         :backgroundColor "white"}}])]))})

(defn render-title [label title]
  [:div {}
   [:div {:style {:fontSize "80%"}} label]
   [:span {:style {:fontWeight 500 :fontSize "125%"}} title]])

(defn- make-tab [{:keys [label first? active? link-key context-id refresh-tab request-refresh on-click]}]
  [Tab {:data-test-id (str label "-tab")
        :label label :first? first? :active? active?
        :on-refresh #(if active?
                       (do (request-refresh)
                           (refresh-tab label))
                       (when on-click
                         (on-click label)))
        :href (when-not on-click (nav/get-link link-key context-id))}])

(defn create-bar [{:keys [data-test-id tabs active-tab context-id refresh-tab request-refresh on-click]}]
  [:div {:style {:marginTop "1rem"
                 :display "flex" :backgroundColor (:background-light style/colors)
                 :borderTop style/standard-line :borderBottom style/standard-line
                 :padding "0 1.5rem" :justifyContent "space-between"}}
   [:div {:data-test-id (or data-test-id "tabs")
          :style {:display "flex"}}
    (map-indexed
     (fn [index [label link-key]]
       (make-tab (merge {:first? (zero? index)
                         :active? (= label active-tab)}
                        (utils/restructure label link-key context-id refresh-tab request-refresh on-click))))
     (remove nil? tabs))]])
