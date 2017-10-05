(ns broadfcui.components.autosuggest
  (:require
   [dmohs.react :as react]
   [cljsjs.react-autosuggest]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))


(react/defc Autosuggest
  "One of
  :data - string suggestion possibilities
  :url - string orch path to append query to and GET from
  :get-suggestions - function accepting value and returning suggestion seq
    Can also return [:loading] or [:error].

  :service-prefix (optional) - passed through to ajax-orch when using :url

  :get-value (optional) - function to turn suggestion into string value

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
     (let [{:keys [data url service-prefix get-suggestions on-change caching? get-value]} props
           {:keys [suggestions]} @state
           value (or (:value (if caching? @state props)) "")
           get-suggestions (cond
                             get-suggestions (fn [value]
                                               (get-suggestions (.-value value) #(swap! state assoc :suggestions %)))
                             url (fn [value]
                                   (utils/ajax-orch
                                    (str url (.-value value))
                                    {:on-done (fn [{:keys [success? get-parsed-response]}]
                                                (swap! state assoc :suggestions
                                                       (if success?
                                                         ; don't bother keywordizing, it's just going to be converted to js
                                                         (get-parsed-response false)
                                                         [:error])))}
                                    (when service-prefix :service-prefix) service-prefix)
                                   [:loading])
                             :else (fn [value]
                                     (let [value (string/lower-case (.-value value))]
                                       (filterv
                                        #(string/includes? (string/lower-case %) value)
                                        data))))]

       (assert (or (fn? (:get-suggestions props)) url (seq? data))
               "Must provide either seq-able :data, url, or :get-suggestions function")
       (assert (fn? on-change)
               "Must provide :on-change callback")

       [js/Autosuggest
        (clj->js (utils/deep-merge
                  {:suggestions (or suggestions [])
                   :onSuggestionsFetchRequested #(swap! state assoc :suggestions (get-suggestions %))
                   :onSuggestionsClearRequested #(swap! state assoc :suggestions [])
                   :getSuggestionValue #(if (keyword? %) value ((or get-value identity) %))
                   :renderSuggestion (fn [suggestion]
                                       (react/create-element
                                        [:div {:style {:textOverflow "ellipsis" :overflow "hidden"}} suggestion]))
                   :inputProps
                   {:value value
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
                   :renderSuggestionsContainer (fn [arg]
                                                 (let [{:keys [containerProps]} (js->clj arg :keywordize-keys true)]
                                                   (react/create-element
                                                    [:div containerProps
                                                     (case suggestions
                                                       [:loading] [comps/Spinner {:text "Loading..."}]
                                                       [:error] [:div {:style {:margin "1em"}} "Error loading results."]
                                                       (.-children arg))])))
                   :theme
                   {:container {}
                    :containerOpen {}
                    :input (assoc style/input-text-style
                             :marginBottom "0.25rem"
                             :WebkitAppearance "none")
                    :inputOpen {:borderBottomLeftRadius 0 :borderBottomRightRadius 0}
                    :inputFocused {:outline "none"}
                    :suggestionsContainer {:fontSize "88%"
                                           :position "absolute"
                                           :width 280
                                           :boxSizing "border-box"
                                           :marginTop "-0.25rem"
                                           :backgroundColor "#fff"
                                           :borderBottomLeftRadius 3
                                           :borderBottomRightRadius 3
                                           :maxHeight 250
                                           :overflowY "auto"
                                           :zIndex 2}
                    :suggestionsContainerOpen {:display "block"
                                               :borderWidth 1
                                               :borderStyle "solid"
                                               :borderColor (:border-light style/colors)}
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
                  (dissoc props :default-value :value :on-submit :data :url :service-prefix :get-suggestions :on-change :caching? :get-value)))]))
   :component-did-mount
   (fn [{:keys [locals this]}]
     (when-let [on-clear (:on-clear @locals)]
       (.addEventListener (react/find-dom-node this) "search" on-clear)))
   :component-will-unmount
   (fn [{:keys [locals this]}]
     (when-let [on-clear (:on-clear @locals)]
       (.removeEventListener (react/find-dom-node this) "search" on-clear)))})
