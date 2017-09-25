(ns broadfcui.components.autosuggest
  (:require
   [dmohs.react :as react]
   [cljsjs.react-autosuggest]
   [clojure.string :as string]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))


(react/defc Autosuggest
  {:component-will-mount
   (fn [{:keys [locals]}]
     (swap! locals assoc :id (gensym "autosuggest")))
   :render
   (fn [{:keys [state props locals]}]
     (let [{:keys [initial-value data placeholder label on-change]} props
           {:keys [suggestions value]} @state
           get-suggestions (fn [value]
                             (filterv
                              #(string/includes? (string/lower-case %)
                                                 (string/lower-case (.-value value)))
                              data))]
       [js/Autosuggest
        (clj->js (utils/deep-merge
                  {:suggestions (or suggestions [])
                   :onSuggestionsFetchRequested #(swap! state assoc :suggestions (get-suggestions %))
                   :onSuggestionsClearRequested #(swap! state assoc :suggestions [])
                   :getSuggestionValue identity
                   :renderSuggestion (fn [suggestion]
                                       (react/create-element [:div {} suggestion]))
                   :inputProps
                   {:data-test-id (str label "-text-input")
                    :value (or value initial-value "")
                    :placeholder placeholder
                    :onChange (fn [_ value]
                                (let [value (.-newValue value)]
                                  (swap! state assoc :value value)
                                  (on-change value)))}
                   :highlightFirstSuggestion true
                   :id (:id @locals)
                   :theme
                   {:container {}
                    :containerOpen {}
                    :input (merge style/input-text-style {:width "95%"})
                    :inputOpen {:borderBottomLeftRadius 0 :borderBottomRightRadius 0}
                    :inputFocused {:outline "none"}
                    :suggestionsContainer {}
                    :suggestionsContainerOpen {:display "block"
                                               :position "absolute"
                                               :width 280
                                               :border style/standard-line
                                               :backgroundColor "#fff"
                                               :borderBottomLeftRadius 4
                                               :borderBottomRightRadius 4
                                               :zIndex 2}
                    :suggestionsList {:margin 0
                                      :padding 0
                                      :listStyle "none"}
                    :suggestion {:cursor "pointer"
                                 :padding "10px 20px"}
                    :suggestionFirst {}
                    :suggestionHighlighted {:backgroundColor (:tag-background style/colors)}
                    :sectionContainer {}
                    :sectionContainerFirst {}
                    :sectionTitle {}}}
                  (dissoc props :data :placeholder :label :on-change)))]))})
