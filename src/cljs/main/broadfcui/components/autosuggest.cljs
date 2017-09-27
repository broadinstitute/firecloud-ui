(ns broadfcui.components.autosuggest
  (:require
   [dmohs.react :as react]
   [cljsjs.react-autosuggest]
   [clojure.string :as string]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))


(react/defc Autosuggest
  "One of
  :data - string suggestion possibilities
  :get-suggestions - function accepting value and returning suggestions

  :on-change (required)

  :caching? (optional) - set true to have re-renders managed internally
  :default-value (optional when caching)
  :value (required when not caching)

  :placeholder (optional)
  :label (optional) - used to generate data-test-id"
  {:component-will-mount
   (fn [{:keys [locals]}]
     (swap! locals assoc :id (gensym "autosuggest")))
   :get-initial-state
   (fn [{:keys [props]}]
     {:value (:default-value props)})
   :render
   (fn [{:keys [state props locals]}]
     (let [{:keys [data placeholder label on-change get-suggestions caching?]} props
           {:keys [suggestions]} @state
           value (:value (if caching? @state props))
           get-suggestions (or get-suggestions
                               (fn [value]
                                 (filterv
                                  #(string/includes? (string/lower-case %)
                                                     (string/lower-case (.-value value)))
                                  data)))]

       (assert (or (fn? get-suggestions) (seq? data)) "Must provide either seq-able :data or :get-suggestions function")
       (assert (fn? on-change) "Must provide :on-change callback")
       (assert (or caching? (:value props)) "Must provide value when not using cached mode")

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
                    :value (or value "")
                    :placeholder placeholder
                    :onChange (fn [_ value]
                                (let [value (.-newValue value)]
                                  (when caching?
                                    (swap! state assoc :value value))
                                  (on-change value)))}
                   :shouldRenderSuggestions (constantly true)
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
                  (dissoc props :data :placeholder :label :on-change :get-suggestions)))]))})
