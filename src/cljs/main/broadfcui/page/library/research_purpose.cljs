(ns broadfcui.page.library.research-purpose
  (:require
   [dmohs.react :as react]
   [broadfcui.common.filter :as filter]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.modals :as modals]
   [broadfcui.utils :as utils]
   ))


(react/defc ResearchPurposeSection
  {:render
   (fn [{:keys [props state]}]
     [:div {}
      (when (:showing-modal? @state)
        [modals/OKCancelForm
         {:header [:div {:style {:lineHeight 1.3}}
                   [:div {} "Filter by Research Purpose"]
                   [:div {:style {:fontSize "initial" :color (:text-light style/colors)}}
                    [:em {} "Powered by "] [:b {} "DUOS"]]]
          :dismiss #(swap! state dissoc :showing-modal?)
          :ok-button {:text "Search" :onClick #(utils/log "search")}
          :content
          [:div {}
           [:div {} "The datasets will be used for the following purposes:"]]}])
      (filter/section
       {:title (links/create-internal
                 {:onClick #(swap! state assoc :showing-modal? true)}
                 "Filter by Research Purpose")
        :on-clear #(utils/log "cleared")})])})
