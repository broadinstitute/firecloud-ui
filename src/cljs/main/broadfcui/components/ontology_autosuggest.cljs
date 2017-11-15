(ns broadfcui.components.ontology-autosuggest
  (:require
   [dmohs.react :as react]
   [broadfcui.components.autosuggest :refer [Autosuggest]]
   [broadfcui.utils :as utils]
   ))


(react/defc OntologyAutosuggest
  {:get-default-props
   (fn []
     {:render-suggestion
      (fn [{:keys [label id definition]}]
        (react/create-element
         [:div {}
          [:div {:style {:lineHeight "1.5em"}}
           label
           [:small {:style {:float "right"}} id]]
          [:small {:style {:fontStyle "italic"}}
           definition]
          [:div {:style {:clear "both"}}]]))})
   :render
   (fn [{:keys [props]}]
     (let [{:keys [render-suggestion on-suggestion-selected]} props]
       [Autosuggest
        {:url "/autocomplete/"
         :service-prefix "/duos"
         :caching? true
         :inputProps {:placeholder "Search for a disease ontology value"}
         :get-value #(.-label %)
         :renderSuggestion (fn [suggestion]
                             (render-suggestion (js->clj suggestion :keywordize-keys true)))
         :highlightFirstSuggestion false
         :onSuggestionSelected (fn [_ suggestion]
                                 (on-suggestion-selected (js->clj (.-suggestion suggestion) :keywordize-keys true)))
         :theme {:input {:width "100%" :marginBottom 0}
                 :suggestionsContainerOpen {:marginTop -1 :width "100%"}}}]))})
