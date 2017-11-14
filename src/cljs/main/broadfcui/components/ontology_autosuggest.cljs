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
      (string/replace-all #"_" ": ")))


(defn render-multiple-ontology-selections [{:keys [onClick selection-map]}]
  (utils/log selection-map)
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
     (let [{:keys [render-suggestion on-suggestion-selected selected-ids]} props]
       [Autosuggest
        {:url "/autocomplete/"
         :service-prefix "/duos"
         :caching? true
         :remove-selected selected-ids
         :inputProps {:placeholder "Search for a disease ontology value"}
         :get-value #(.-label %)
         :renderSuggestion (fn [suggestion]
                             (render-suggestion (js->clj suggestion :keywordize-keys true)))
         :highlightFirstSuggestion false
         :onSuggestionSelected (fn [_ suggestion]
                                 (on-suggestion-selected (js->clj (.-suggestion suggestion) :keywordize-keys true)))
         :theme {:input {:width "100%" :marginBottom 0}
                 :suggestionsContainerOpen {:marginTop -1 :width "100%"}}}]))})



