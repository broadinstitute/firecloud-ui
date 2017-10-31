(ns broadfcui.page.library.research-purpose
  (:require
   [dmohs.react :as react]
   [broadfcui.common.filter :as filter]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.checkbox :refer [Checkbox]]
   [broadfcui.components.modals :as modals]
   [broadfcui.utils :as utils]
   ))


(react/defc ResearchPurposeSection
  {:render
   (fn [{:keys [state this]}]
     [:div {}
      (when (:showing-modal? @state)
        (this :-render-modal))
      (filter/section
       {:title (links/create-internal
                 {:onClick #(swap! state assoc :showing-modal? true)}
                 "Filter by Research Purpose")
        :on-clear #(utils/log "cleared")
        :content [:div {} "stuff goes here"]})])
   :-render-modal
   (fn [{:keys [state this]}]
     [modals/OKCancelForm
      {:header [:div {:style {:lineHeight 1.3}}
                [:div {} "Filter by Research Purpose"]
                [:div {:style {:fontSize "initial" :color (:text-light style/colors)}}
                 [:em {} "Powered by "] [:b {} "DUOS"]]]
       :dismiss #(swap! state dissoc :showing-modal?)
       :ok-button {:text "Search" :onClick #(utils/log "search")}
       :content
       [:div {}
        [:div {:style {:marginBottom "0.75rem"}}
         "The datasets will be used for the following purposes:"]
        (this :-render-checkbox "Disease focused research" true)
        (this :-render-checkbox "Methods development/Validation study" true)
        (this :-render-checkbox "Control set" true)
        (this :-render-checkbox "Aggregate analysis to understand variation in the general population")
        (this :-render-checkbox "Study population origins or ancestry")
        (this :-render-checkbox "Commercial purpose/by a commercial entity")
        (this :-render-checkbox "Restricted to a specific population" true)
        (this :-render-checkbox "Other purpose" true)]}])
   :-render-checkbox
   (fn [{:keys [props state]} label & [disabled?]]
     [:div {:style {:margin "0.5rem 0"}}
      [Checkbox {:label label
                 :disabled? disabled?
                 :on-change #(utils/log "changed" %)}]])})
