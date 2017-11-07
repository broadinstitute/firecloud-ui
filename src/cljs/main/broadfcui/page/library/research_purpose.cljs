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
   (fn [{:keys [state this]}]
     [modals/OKCancelForm
      {:header [:div {:style {:lineHeight 1.3}}
                [:div {} "Filter by Research Purpose"]
                [:div {:style {:fontSize "initial" :color (:text-light style/colors)}}
                 [:em {} "Powered by "] [:b {} "DUOS"]]]
       :dismiss #(swap! state dissoc :showing-modal?)
       :ok-button {:text "Search" :onClick #(this :-search (:research-purpose-values @state))}
       :content
       [:div {}
        [:div {:style {:marginBottom "1rem"}}
         "The datasets will be used for the following purposes:"]
        (this :-render-disease-question)
        (this :-render-checkbox "Methods development/Validation study" :methods)
        (this :-render-checkbox "Control set" :control)
        (this :-render-checkbox "Aggregate analysis to understand variation in the general population" :aggregates)
        (this :-render-checkbox "Study population origins or ancestry" :poa)
        (this :-render-checkbox "Commercial purpose/by a commercial entity" :commercial)]}])
   :-render-disease-question
   (fn [{:keys [state]}]
     [:div {}
      [Checkbox {:style {:margin "0.75rem 0"}
                 :label "Disease focused research"
                 :checked? (:disease-checked? @state)
                 :on-change (fn [new-val]
                              (swap! state assoc :disease-checked? new-val))}]
      (when (:disease-checked? @state)
        [:div {} "TODO: DOID autocomplete goes here"])])
   :-render-checkbox
   (fn [{:keys [props state]} label code]
     [Checkbox {:style {:margin "0.75rem 0"}
                :label [:span {}
                        label
                        [:span {:style {:marginLeft "0.3rem" :fontSize "66%" :verticalAlign "middle"}}
                         (-> code name string/upper-case style/render-tag)]]
                :initial-checked? (contains? (:research-purpose-values props) code)
                :on-change (fn [new-val]
                             (swap! state assoc-in [:research-purpose-values code] new-val))}])
   :-show-modal
   (fn [{:keys [props state]}]
     (let [{:keys [research-purpose-values]} props]
       (swap! state assoc
              :showing-modal? true
              :research-purpose-values research-purpose-values
              :disease-checked? (seq (:ds research-purpose-values)))))
   :-search
   (fn [{:keys [props state]} values]
     ((:on-search props) values)
     (swap! state dissoc :showing-modal?))})
