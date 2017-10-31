(ns broadfcui.page.library.research-purpose
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.filter :as filter]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.checkbox :refer [Checkbox]]
   [broadfcui.components.modals :as modals]
   [broadfcui.utils :as utils]
   ))


(react/defc ResearchPurposeSection
  {:render
   (fn [{:keys [props state this]}]
     [:div {}
      (when (:showing-modal? @state)
        (this :-render-modal))
      (filter/section
       {:title (links/create-internal
                 {:onClick #(this :-show-modal)}
                 "Filter by Research Purpose")
        :on-clear #(this :-search {})
        :content [:div {}
                  (->> (:research-purpose-values props)
                       keys
                       (map (comp style/render-tag string/upper-case name)))]})])
   :-render-modal
   (fn [{:keys [state this locals]}]
     [modals/OKCancelForm
      {:header [:div {:style {:lineHeight 1.3}}
                [:div {} "Filter by Research Purpose"]
                [:div {:style {:fontSize "initial" :color (:text-light style/colors)}}
                 [:em {} "Powered by "] [:b {} "DUOS"]]]
       :dismiss #(swap! state dissoc :showing-modal?)
       :ok-button {:text "Search" :onClick #(this :-search @locals)}
       :content
       [:div {}
        [:div {:style {:marginBottom "1rem"}}
         "The datasets will be used for the following purposes:"]
        (this :-render-checkbox "Disease focused research" :disabled? true)
        (this :-render-checkbox "Methods development/Validation study" :disabled? true :code :methods)
        (this :-render-checkbox "Control set" :disabled? true :code :control)
        (this :-render-checkbox "Aggregate analysis to understand variation in the general population" :code :pop-var)
        (this :-render-checkbox "Study population origins or ancestry" :code :origins)
        (this :-render-checkbox "Commercial purpose/by a commercial entity" :code :commercial)
        #_(this :-render-checkbox "Restricted to a specific population")
        #_(this :-render-checkbox "Other purpose" :code :other)]}])
   :-render-checkbox
   (fn [{:keys [props locals]} label & {:keys [disabled? code]}]
     [Checkbox {:style {:margin "0.75rem 0"}
                :label [:span {}
                        label
                        [:span {:style {:marginLeft "0.3rem" :fontSize "66%" :verticalAlign "middle"}}
                         (some-> code name string/upper-case style/render-tag)]]
                :disabled? disabled?
                :initial-checked? (contains? (:research-purpose-values props) code)
                :on-change (fn [new-val]
                             (when code
                               (swap! locals assoc code new-val)))}])
   :-show-modal
   (fn [{:keys [props state locals]}]
     (reset! locals (:research-purpose-values props))
     (swap! state assoc :showing-modal? true))
   :-search
   (fn [{:keys [props state]} values]
     ((:on-search props) values)
     (swap! state dissoc :showing-modal?))})
