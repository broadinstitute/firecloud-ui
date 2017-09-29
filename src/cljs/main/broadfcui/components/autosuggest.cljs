(ns broadfcui.components.autosuggest
  (:require
   [dmohs.react :as react]
   [cljsjs.react-autosuggest]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))


(react/defc Autosuggest
  "One of
  :data - string suggestion possibilities
  :get-suggestions - function accepting value and returning suggestions

  :on-change (required)
  :on-submit (optional) - fires when hitting return or clear button

  :caching? (optional) - set true to have re-renders managed internally
  :default-value (optional when caching)
  :value (required when not caching)

  Other props to pass through to input element go in :inputProps."
  {:component-will-mount
   (fn [{:keys [locals props]}]
     (let [{:keys [on-submit]} props
           wrapped-on-submit (fn [e]
                               (on-submit (.. e -target -value)))
           on-clear (fn [e]
                      (when (zero? (.. e -target -value -length)) (on-submit "")))]
       (swap! locals assoc
              :id (gensym "autosuggest")
              :on-clear (when on-submit on-clear)
              :on-submit (when on-submit wrapped-on-submit))))
   :get-initial-state
   (fn [{:keys [props]}]
     {:value (:default-value props)})
   :render
   (fn [{:keys [state props locals]}]
     (let [{:keys [data on-change get-suggestions caching?]} props
           {:keys [suggestions]} @state
           value (:value (if caching? @state props))
           get-suggestions (if get-suggestions
                             (fn [value]
                               (get-suggestions (.-value value) #(swap! state assoc :suggestions %)))
                             (fn [value]
                               (filterv
                                #(string/includes? (string/lower-case %)
                                                   (string/lower-case (.-value value)))
                                data)))]

       (assert (or (fn? (:get-suggestions props)) (seq? data)) "Must provide either seq-able :data or :get-suggestions function")
       (assert (fn? on-change) "Must provide :on-change callback")
       (assert (or caching? (:value props)) "Must provide value when not using cached mode")

       [js/Autosuggest
        (clj->js (utils/deep-merge
                  {:suggestions (or suggestions [])
                   :onSuggestionsFetchRequested #(swap! state assoc :suggestions (get-suggestions %))
                   :onSuggestionsClearRequested #(swap! state assoc :suggestions [])
                   :getSuggestionValue identity
                   :renderSuggestion (fn [suggestion]
                                       (react/create-element [:div {:style {:textOverflow "ellipsis"
                                                                            :overflow "hidden"}}
                                                              suggestion]))
                   :inputProps
                   {:value (or value "")
                    :onKeyDown (when-let [on-submit (:on-submit @locals)]
                                 (common/create-key-handler [:enter] on-submit))
                    :onChange (fn [_ value]
                                (let [value (.-newValue value)]
                                  (when caching?
                                    (swap! state assoc :value value))
                                  (on-change value)))
                    :type "search"}
                   :shouldRenderSuggestions (complement string/blank?)
                   :highlightFirstSuggestion true
                   :id (:id @locals)
                   :theme
                   {:container {}
                    :containerOpen {}
                    :input (assoc style/input-text-style
                             :marginBottom "0.25rem"
                             :WebkitAppearance "none")
                    :inputOpen {:borderBottomLeftRadius 0 :borderBottomRightRadius 0}
                    :inputFocused {:outline "none"}
                    :suggestionsContainer {}
                    :suggestionsContainerOpen {:display "block"
                                               :position "absolute"
                                               :width 280
                                               :boxSizing "border-box"
                                               :marginTop "-0.25rem"
                                               :borderWidth 1
                                               :borderStyle "solid"
                                               :borderColor (:border-light style/colors)
                                               :backgroundColor "#fff"
                                               :borderBottomLeftRadius 3
                                               :borderBottomRightRadius 3
                                               :zIndex 2}
                    :suggestionsList {:margin 0
                                      :padding 0
                                      :listStyle "none"}
                    :suggestion {:cursor "pointer"
                                 :padding "0.75rem 1rem"}
                    :suggestionFirst {}
                    :suggestionHighlighted {:backgroundColor (:tag-background style/colors)}
                    :sectionContainer {}
                    :sectionContainerFirst {}
                    :sectionTitle {}}}
                  (dissoc props :data :on-change :get-suggestions :clear-suggestions :caching? :on-submit)))]))
   :component-did-mount
   (fn [{:keys [locals this]}]
     (when-let [on-clear (:on-clear @locals)]
       (.addEventListener (react/find-dom-node this) "search" on-clear)))
   :component-will-unmount
   (fn [{:keys [locals this]}]
     (when-let [on-clear (:on-clear @locals)]
       (.removeEventListener (react/find-dom-node this) "search" on-clear)))})
