(ns broadfcui.components.ontology-autosuggest
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.autosuggest :refer [Autosuggest]]
   [broadfcui.utils :as utils]
   ))

(defn render-doid [doid]
  (-> doid
      (string/split "/")
      last
      (string/replace-all #"_" ":")))


(defn render-multiple-ontology-selections [{:keys [onClick selection-map]}]
  (map (fn [[id label]]
         (flex/box
          {:style {:margin "0.3rem 0" :alignItems "center"}}
          [:span {:style {:fontSize "66%"}} (style/render-tag (render-doid id))]
          [:span {:style {:margin "0 0.5rem" :color (:text-light style/colors)}} label]
          flex/spring
          (links/create-internal
           {:onClick #(onClick id label)}
           (icons/render-icon {:className "fa-lg"
                               :style {:color (:text-light style/colors)}}
                              :remove))))
       selection-map))


(defn create-ontology-autosuggest [props]
  (let [{:keys [ref render-suggestion on-suggestion-selected selected-ids on-change on-submit value input-props]} props
        render-suggestion (or render-suggestion
                              (fn [{:keys [label id definition]}]
                                (react/create-element
                                 [:div {}
                                  [:div {:style {:lineHeight "1.5em"}}
                                   label
                                   [:small {:style {:float "right"}} id]]
                                  [:small {:style {:fontStyle "italic"}}
                                   definition]
                                  [:div {:style {:clear "both"}}]])))]
    [Autosuggest
     {:ref ref
      :value value
      :inputProps (or input-props {})
      :url "/autocomplete/"
      :service-prefix "/duos"
      :caching? true
      :remove-selected selected-ids
      :get-value #(.-label %)
      :renderSuggestion (fn [suggestion]
                          (render-suggestion (js->clj suggestion :keywordize-keys true)))
      :highlightFirstSuggestion false
      :onSuggestionSelected (fn [_ suggestion]
                              (on-suggestion-selected (js->clj (.-suggestion suggestion) :keywordize-keys true)))
      :on-change on-change
      :on-submit on-submit
      :theme {:input {:width "100%" :marginBottom 0}
              :suggestionsContainerOpen {:marginTop -1 :width "100%"}}}]))
