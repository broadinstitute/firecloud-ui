(ns broadfcui.page.library.research-purpose
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.filter :as filter]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.autosuggest :refer [Autosuggest]]
   [broadfcui.components.checkbox :refer [Checkbox]]
   [broadfcui.components.modals :as modals]
   [broadfcui.utils :as utils]
   ))


(defn- render-doid [doid]
  (-> doid
      (string/split "/")
      last
      (string/replace-all #"_" ": ")))


(react/defc ResearchPurposeSection
  {:render
   (fn [{:keys [props state this]}]
     (filter/section
      {:title (links/create-internal
                {:onClick #(this :-show-modal)}
                "Filter by Research Purpose")
       :on-clear #(this :-search true)
       :content (let [{:keys [research-purpose-values]} props
                      filtered (if (seq (:ds research-purpose-values))
                                 research-purpose-values
                                 (dissoc research-purpose-values :ds))]
                  [:div {}
                   (when (:showing-modal? @state)
                     (this :-render-modal))
                   (map (comp style/render-tag string/upper-case name) (keys filtered))])}))
   :-render-modal
   (fn [{:keys [state this]}]
     [modals/OKCancelForm
      {:header [:div {:style {:lineHeight 1.3}}
                [:div {} "Filter by Research Purpose"]
                [:div {:style {:fontSize "initial" :color (:text-light style/colors)}}
                 [:em {} "Powered by "] [:b {} "DUOS"]]]
       :dismiss #(swap! state dissoc :showing-modal?)
       :ok-button {:text "Search" :onClick #(this :-search)}
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
   (fn [{:keys [state this]}]
     [:div {:style {:position "relative"}}
      [Checkbox {:style {:margin "0.75rem 0"}
                 :label "Disease focused research"
                 :checked? (:disease-checked? @state)
                 :on-change (fn [new-val]
                              (swap! state assoc :disease-checked? new-val))}]
      (when (:disease-checked? @state)
        [:div {:style {:paddingLeft "1.5rem"}}
         (this :-render-selected-diseases)
         [Autosuggest
          {:url "/autocomplete/"
           :service-prefix "/duos"
           :caching? true
           :inputProps {:placeholder "Search for a disease ontology value"}
           :get-value #(.-label %)
           :renderSuggestion (fn [suggestion]
                               (react/create-element
                                [:div {}
                                 [:div {:style {:lineHeight "1.5em"}}
                                  (.-label suggestion)
                                  [:small {:style {:float "right"}} (.-id suggestion)]]
                                 [:small {:style {:fontStyle "italic"}}
                                  (.-definition suggestion)]
                                 [:div {:style {:clear "both"}}]]))
           :highlightFirstSuggestion false
           :onSuggestionSelected (fn [_ suggestion]
                                   (let [{:keys [id label]} (js->clj (.-suggestion suggestion) :keywordize-keys true)]
                                     (swap! state update :selected-diseases assoc id label)))
           :theme {:input {:width "100%" :marginBottom 0}
                   :suggestionsContainerOpen {:marginTop -1 :width "100%"}}}]])])
   :-render-selected-diseases
   (fn [{:keys [state]}]
     (map (fn [[id label]]
            (flex/box
             {:style {:margin "0.3rem 0" :alignItems "center"}}
             [:span {:style {:fontSize "66%"}} (style/render-tag (render-doid id))]
             [:span {:style {:margin "0 0.5rem" :color (:text-light style/colors)}} label]
             flex/spring
             (links/create-internal
               {:onClick #(swap! state update :selected-diseases dissoc id)}
               (icons/render-icon {:className "fa-lg"
                                   :style {:color (:text-light style/colors)}}
                                  :remove))))
          (:selected-diseases @state)))
   :-render-checkbox
   (fn [{:keys [state]} label code]
     [Checkbox {:style {:margin "0.75rem 0"}
                :label [:span {}
                        label
                        [:span {:style {:marginLeft "0.3rem" :fontSize "66%" :verticalAlign "middle"}}
                         (-> code name string/upper-case style/render-tag)]]
                :checked? (boolean (code (:research-purpose-values @state)))
                :on-change (fn [new-val]
                             (swap! state assoc-in [:research-purpose-values code] new-val))}])
   :-show-modal
   (fn [{:keys [props state]}]
     (let [{:keys [research-purpose-values]} props
           {:keys [ds]} research-purpose-values]
       (swap! state assoc
              :showing-modal? true
              :research-purpose-values research-purpose-values
              :disease-checked? (seq ds)
              :selected-diseases ds)))
   :-search
   (fn [{:keys [props state]} & [clearing?]]
     (let [values (if clearing?
                    {}
                    (assoc (:research-purpose-values @state)
                      :ds (if (:disease-checked? @state)
                            (:selected-diseases @state)
                            {})))]
       ((:on-search props) values)
       (swap! state dissoc :showing-modal?)))})
