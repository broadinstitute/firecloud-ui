(ns broadfcui.page.workspace.summary.catalog.questions
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.components :as comps]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.autosuggest :refer [Autosuggest]]
   [broadfcui.config :as config]
   [broadfcui.page.workspace.summary.library-utils :as library-utils]
   [broadfcui.utils :as utils]
   ))


(def ^:private ENUM_EMPTY_CHOICE "<select an option>")

(defn- resolve-enum [value]
  (when-not (= value ENUM_EMPTY_CHOICE)
    value))


(defn- parse-attributes [attributes library-schema]
  (utils/map-kv
   (fn [k v]
     (let [{:keys [type items enum]} (get-in library-schema [:properties k])]
       [k (if enum
            (resolve-enum v)
            (case type
              "integer" (int v)
              "array" (let [tokens (keep (comp not-empty string/trim) (string/split v #","))]
                        (case (:type items)
                          "integer" (map int tokens)
                          tokens))
              v))]))
   attributes))

(defn- validate-numbers [attributes library-schema]
  (let [invalid-numbers (->> attributes
                             ;; get ones that are integers:
                             (utils/filter-keys #(= "integer" (get-in library-schema [:properties % :type])))
                             ;; attach min/max props from library-schema:
                             (utils/map-kv (fn [k v]
                                             [k (merge (select-keys (get-in library-schema [:properties k]) [:minimum :maximum])
                                                       {:value v})]))
                             ;; throw out decimals and out of range values:
                             (keep (fn [[k {:keys [value minimum maximum] :or {minimum -Infinity maximum Infinity}}]]
                                     (when-not (and (or
                                                     (= value (str (int value)))
                                                     (= value (int value)))
                                                    (<= minimum (int value) maximum))
                                       k)))
                             set)]
    (when-not (empty? invalid-numbers)
      {:error "Invalid number"
       :invalid invalid-numbers})))


(defn- render-header [{:keys [prop consentCode library-schema required? colorize]}]
  [:div {:style {:marginBottom 2}}
   (:title prop)
   (when consentCode
     (list
      " ["
      [:abbr {:style {:cursor "help" :whiteSpace "nowrap" :borderBottom "1px dotted"}
              :title (get-in library-schema [:consentCodes (keyword consentCode)])}
       consentCode]
      "]"))
   (when required?
     [:span {:style (colorize {:fontWeight "bold"})}
      " (required)"])])

(defn- render-enum [{:keys [enum style wording radio current-value colorize update-property set-property disabled property]}]
  (if (= style "large")
    [:div {}
     (map (fn [option]
            (let [selected? (= current-value option)]
              [:div {:style {:display "flex" :alignItems "center"
                             :margin "0.5rem 0" :padding "1em"
                             :border style/standard-line :borderRadius 8
                             :backgroundColor (cond disabled (:disabled-state style/colors) selected? (:button-primary style/colors))
                             :cursor "pointer"}
                     :onClick #(when-not disabled (set-property option))}
               [:input {:type "radio" :readOnly true :checked selected? :disabled disabled
                        :style {:cursor "pointer"}}]
               [:div {:style {:marginLeft "0.75rem" :color (cond disabled (:text-light style/colors) selected? "white")}}
                (or (wording (keyword option)) option)]]))
          enum)]
    (if (< (count enum) 4)
      [:div {:style {:display "inline-block" :margin "0.75em 0 0.75em 1em"}}
       (map #(radio {:val % :property property}) enum)]
      (style/create-identity-select {:value (or current-value ENUM_EMPTY_CHOICE)
                                     :style (colorize {})
                                     :disabled disabled
                                     :onChange update-property}
                                    (cons ENUM_EMPTY_CHOICE enum)))))

(defn- render-boolean [{:keys [radio required? emptyChoice wording property]}]
  [:div {:style {:display "inline-block" :margin "0.75em 0 0.75em 1em"}}
   (radio {:val true :label (case wording "yes/no" "Yes" "True") :property property})
   (radio {:val false :label (case wording "yes/no" "No" "False") :property property})
   (when-not required?
     (radio {:val nil :label (or emptyChoice "N/A") :property property}))])

(defn- render-freetext [{:keys [colorize value-nullsafe update-property disabled property]}]
  (style/create-text-area {:style (colorize {:width "100%"})
                           :value value-nullsafe
                           :onChange update-property
                           :disabled disabled
                           :rows 3
                           :data-test-id property})) ;; Dataset attribute, looks like "library:datasetOwner"

(defn- render-ontology-typeahead [{:keys [prop colorize value-nullsafe set-property state property library-schema disabled]}]
  (let [clear #(apply swap! state update :attributes dissoc property
                      (library-utils/get-related-id+label-props library-schema property))]
    [:div {:style {:marginBottom "0.75em"}}
     [Autosuggest
      {:value value-nullsafe
       :inputProps {:placeholder (:inputHint prop)
                    :data-test-id property
                    :disabled disabled}
       :url "/autocomplete/"
       :service-prefix "/duos"
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
                               (let [suggestion (utils/log (js->clj (.-suggestion suggestion) :keywordize-keys true))
                                     {:keys [id label]} suggestion
                                     [related-id-prop related-label-prop] (map (comp keyword #(% prop)) [:relatedID :relatedLabel])]
                                 (swap! state update :attributes assoc
                                        related-id-prop id
                                        related-label-prop label)))
       :on-change set-property
       :on-submit #(when (empty? %) (clear))
       :theme {:input (colorize {:width "100%" :marginBottom 0})
               :suggestionsContainerOpen {:marginTop -1 :width "100%"}}}]

     (let [[related-id related-label] (library-utils/get-related-id+label (:attributes @state) library-schema property)]
       (when (not-any? clojure.string/blank? [related-id related-label])
         [:div {}
          [:span {:style {:fontWeight "bold"}} related-label]
          [:span {:style {:fontSize "small" :float "right"}} related-id]
          [:div {}
           (when-not disabled
             (links/create-internal {:onClick clear}
                                    "Clear Selection"))]]))]))

(defn- render-populate-typeahead [{:keys [value-nullsafe property inputHint colorize set-property disabled]}]
  [:div {:style {:marginBottom "0.75em"}}
   [Autosuggest
    {:value value-nullsafe
     :inputProps {:placeholder inputHint
                  :disabled disabled
                  :data-test-id property} ;; Dataset attribute, looks like "library:datasetOwner"
     :url (str "/library/populate/suggest/" (name property) "?q=")
     :on-change set-property
     :theme {:input (colorize {:width "100%" :marginBottom 0})
             :suggestionsContainerOpen {:marginTop -1 :width "100%"}}}]])

(defn- render-textfield [{:keys [colorize type datatype prop value-nullsafe update-property disabled property]}]
  (style/create-text-field {:style (colorize {:width "100%"})
                            :data-test-id property ;; Dataset attribute, looks like "library:datasetOwner"
                            :type (cond (= datatype "date") "date"
                                        (= datatype "email") "email"
                                        (= type "integer") "number"
                                        :else "text")
                            :min (:minimum prop)
                            :placeholder (:inputHint prop)
                            :value value-nullsafe
                            :disabled disabled
                            :onChange update-property}))


(react/defc Questions
  {:validate
   (fn [{:keys [props state locals]}]
     (let [{:keys [library-schema]} props
           processed-attributes (->> (:attributes @state)
                                     (utils/map-values (fn [val]
                                                         (if (string? val)
                                                           (string/trim val)
                                                           val)))
                                     (utils/filter-values some?))
           {:keys [error invalid]} (or (validate-numbers processed-attributes library-schema))]
       (swap! locals assoc :processed-attributes processed-attributes)
       (when error
         (swap! state assoc :invalid-properties invalid)
         error)))
   :get-attributes
   (fn [{:keys [props locals]}]
     (parse-attributes (:processed-attributes @locals) (:library-schema props)))
   :get-initial-state
   (fn [{:keys [props]}]
     (let [{:keys [questions attributes library-schema]} props
           prop->string (fn [prop] (if (seq? prop) (string/join ", " prop) prop))
           get-prop (fn [prop-key] (prop->string (get attributes prop-key
                                                      (get-in library-schema [:properties prop-key :default]))))]
       {:attributes
        (reduce (fn [map prop-key] (assoc map prop-key (get-prop prop-key)))
                {}
                (map keyword questions))}))
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [library-schema missing-properties questions required-attributes enumerate editable?]} props
           {:keys [attributes invalid-properties]} @state]
       [(if enumerate :ol :div) {}
        (map
         (fn [property]
           (let [current-value (get attributes property)
                 {:keys [type enum renderHint] :as prop} (get-in library-schema [:properties property])
                 error? (or (contains? invalid-properties property) (contains? missing-properties property))
                 colorize #(merge % (when error? {:borderColor (:exception-state style/colors)
                                                  :color (:exception-state style/colors)}))
                 data (merge (utils/restructure prop state property library-schema colorize current-value)
                             {:value-nullsafe (or current-value "") ;; avoids warning for nil value
                              :required? (contains? required-attributes property)
                              :update-property #(swap! state update :attributes assoc property (.. % -target -value))
                              :set-property #(swap! state update :attributes assoc property %)
                              :disabled (not editable?)
                              :radio (fn [{:keys [val label property]}]
                                       [:label {:style (colorize {:display "inline-flex" :alignItems "center"
                                                                  :cursor "pointer" :marginRight "2em"})}
                                        [:input {:type "radio" :readOnly true :checked (= val current-value)
                                                 ;; looks like "library:RS-G-Male" or "library:requiresExternalApproval-Yes"
                                                 :data-test-id (str (name property) "-" (or label (str val)))
                                                 :style {:cursor "pointer"}
                                                 :disabled (not editable?)
                                                 :onChange #(swap! state update :attributes assoc property val)}]
                                        [:div {:style {:padding "0 0.4em" :fontWeight "500"}} (or label (str val))]])}
                             prop
                             renderHint)]
             (when-not (:hidden prop)
               [(if enumerate :li :div) {}
                (render-header data)
                (cond enum (render-enum data)
                      (= type "boolean") (render-boolean data)
                      (= (:datatype renderHint) "freetext") (render-freetext data)
                      (= (:typeahead prop) "ontology") (render-ontology-typeahead data)
                      (= (:typeahead prop) "populate") (render-populate-typeahead data)
                      :else (render-textfield data))])))
         (map keyword questions))]))})
