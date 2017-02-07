(ns broadfcui.page.workspace.summary.catalog.questions
  (:require
    [clojure.string :refer [join split trim]]
    [dmohs.react :as react]
    [broadfcui.common.components :as comps]
    [broadfcui.common.style :as style]
    [broadfcui.config :as config]
    [broadfcui.page.workspace.summary.library-utils :as library-utils]
    [broadfcui.utils :as utils]
    ))


(def ^:private ENUM_EMPTY_CHOICE "<select an option>")

(defn resolve-enum [value]
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
              "array" (let [tokens (keep (comp not-empty trim) (split v #","))]
                        (case (:type items)
                          "integer" (map int tokens)
                          tokens))
              v))]))
   attributes))


(defn- validate-required [attributes questions required-attributes]
  (let [required-props (->> questions
                            (map keyword)
                            (filter (partial contains? required-attributes))
                            set)
        missing-props (clojure.set/difference required-props (-> attributes keys set))]
    (when-not (empty? missing-props)
      {:error "Please provide all required attributes"
       :invalid missing-props})))

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

(defn- render-enum [{:keys [enum radio current-value colorize update-property]}]
  (if (< (count enum) 4)
    [:div {:style {:display "inline-block" :margin "0.75em 0 0.75em 1em"}}
     (map #(radio {:val %}) enum)]
    (style/create-identity-select {:value (or current-value ENUM_EMPTY_CHOICE)
                                   :style (colorize {})
                                   :onChange update-property}
                                  (cons ENUM_EMPTY_CHOICE enum))))

(defn- render-boolean [{:keys [radio required? emptyChoice wording]}]
  [:div {:style {:display "inline-block" :margin "0.75em 0 0.75em 1em"}}
   (radio {:val true :label (case wording "yes/no" "Yes" "True")})
   (radio {:val false :label (case wording "yes/no" "No" "False")})
   (when-not required?
     (radio {:val nil :label (or emptyChoice "N/A")}))])

(defn- render-freetext [{:keys [colorize value-nullsafe update-property]}]
  (style/create-text-area {:style (colorize {:width "100%"})
                           :value value-nullsafe
                           :onChange update-property
                           :rows 3}))

(defn- render-ontology-typeahead [{:keys [prop colorize value-nullsafe update-property state property library-schema]}]
  [:div {:style {:marginBottom "0.75em"}}
   [comps/Typeahead {:field-attributes {:placeholder (:inputHint prop)
                                        :style (colorize {:width "100%" :marginBottom "0px"})
                                        :value value-nullsafe
                                        :onChange update-property}
                     :remote {:url (str (config/api-url-root) "/duos/autocomplete/%QUERY")
                              :wildcard "%QUERY"
                              :cache false}
                     :render-display #(aget % "label")
                     :render-suggestion (fn [result]
                                          (str "<div> <div style='line-height: 1.5em;'>" (aget result "label")
                                               "<small style='float: right;'>" (aget result "id") "</small></div>"
                                               "<small style='font-style: italic;'> " (aget result "definition") "</small></div>"))
                     :on-select (fn [_ suggestion]
                                  (let [[id label] (map (partial aget suggestion) ["id" "label"])
                                        [related-id-prop related-label-prop] (map #(-> prop % keyword) [:relatedID :relatedLabel])]
                                    (swap! state update :attributes assoc
                                           property label
                                           related-id-prop id
                                           related-label-prop label)))}]
   (let [[related-id related-label] (library-utils/get-related-id+label (:attributes @state) library-schema property)]
     (when (not-any? clojure.string/blank? [related-id related-label])
       [:div {:style {:fontWeight "bold"}}
        related-label
        [:span {:style {:fontWeight "normal" :fontSize "small" :float "right"}} related-id]
        [:div {:style {:fontWeight "normal"}}
         (style/create-link {:text "Clear Selection"
                             :onClick #(apply swap! state update :attributes dissoc
                                              (library-utils/get-related-id+label-props library-schema property))})]]))])

(defn- render-populate-typeahead [{:keys [property value-nullsafe inputHint set-property]}]
  [:div {:style {:marginBottom "0.75em"}}
   [comps/AutocompleteFilter
    {:width "100%"
     :ref "text-filter"
     :placeholder inputHint
     :initial-text value-nullsafe
     :on-filter set-property
     :bloodhoundInfo {:url (str (config/api-url-root) "/api/library/populate/suggest/" (name property))
                      :cache false
                      :prepare (fn [query settings]
                                 (clj->js
                                   (assoc (js->clj settings)
                                     :headers {:Authorization (str "Bearer " (utils/get-access-token))}
                                     :type "GET"
                                     :url (str (aget settings "url") "?q=" query))))}
     :typeaheadSuggestionTemplate (fn [result]
                                    (str "<div style='textOverflow: ellipsis; overflow: hidden; font-size: smaller;'>" result  "</div>"))}]])

(defn- render-textfield [{:keys [colorize datatype prop value-nullsafe update-property]}]
  (style/create-text-field {:style (colorize {:width "100%"})
                            :type (cond (= datatype "date") "date"
                                        (= datatype "email") "email"
                                        (= type "integer") "number"
                                        :else "text")
                            :min (:minimum prop)
                            :placeholder (:inputHint prop)
                            :value value-nullsafe
                            :onChange update-property}))


(react/defc Questions
  {:validate
   (fn [{:keys [props state locals]}]
     (let [{:keys [questions library-schema required-attributes]} props
           processed-attributes (->> (:attributes @state)
                                     (utils/map-values (fn [val]
                                                         (if (string? val)
                                                           (not-empty (trim val))
                                                           val)))
                                     (utils/filter-values some?))
           {:keys [error invalid]} (or (validate-required processed-attributes questions required-attributes)
                                       (validate-numbers processed-attributes library-schema))]
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
           prop->string (fn [prop] (if (seq? prop) (join ", " prop) prop))
           get-prop (fn [prop-key] (prop->string (get attributes prop-key
                                                      (get-in library-schema [:properties prop-key :default]))))]
       {:attributes
        (reduce (fn [map prop-key] (assoc map prop-key (get-prop prop-key)))
                {}
                (map keyword questions))}))
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [library-schema questions required-attributes enumerate]} props
           {:keys [attributes invalid-properties]} @state]
       [(if enumerate :ol :div) {}
        (map
         (fn [property]
           (let [current-value (get attributes property)
                 {:keys [type enum renderHint] :as prop} (get-in library-schema [:properties property])
                 error? (contains? invalid-properties property)
                 colorize #(merge % (when error? {:borderColor (:exception-state style/colors)
                                                  :color (:exception-state style/colors)}))
                 data (merge {:prop prop :state state :property property :library-schema library-schema
                              :colorize colorize :current-value current-value
                              :value-nullsafe (or current-value "") ;; avoids warning for nil value
                              :required? (contains? required-attributes property)
                              :update-property #(swap! state update :attributes assoc property (.. % -target -value))
                              :set-property #(swap! state update :attributes assoc property %)
                              :radio (fn [{:keys [val label]}]
                                       [:label {:style (colorize {:display "inline-flex" :alignItems "center"
                                                                  :cursor "pointer" :marginRight "2em"})}
                                        [:input (merge
                                                 {:type "radio" :readOnly true
                                                  :style {:cursor "pointer"}
                                                  :onChange #(swap! state update :attributes assoc property val)}
                                                 (when (= val current-value) {:checked true}))]
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
