(ns broadfcui.page.workspace.summary.library-utils
  (:require
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))

(def ^:private ATTRIBUTE_SEPARATOR ", ")

(defn unpack-attribute-list [value]
  (if (map? value)
    (string/join ATTRIBUTE_SEPARATOR (:items value))
    value))

(defn- split-attributes [values]
  (string/split values (re-pattern ATTRIBUTE_SEPARATOR)))

(defn- zip-comma-separated-strings [keys values]
  (if (string/blank? keys)
    {}
    (let [split-keys (split-attributes keys)
          split-values (split-attributes values)]
      (zipmap split-keys split-values))))

(defn- remove-from-comma-separated-strings [value-string item]
  (if (string/blank? value-string)
    value-string
    (let [values (split-attributes value-string)]
      (string/join ATTRIBUTE_SEPARATOR (utils/delete values (utils/index-of values item))))))

(defn- add-to-comma-separated-strings [value-string item]
  (if (string/blank? value-string)
    item
    (str value-string ATTRIBUTE_SEPARATOR item)))

(defn get-related-id+label-props [library-schema property]
  (map #(keyword (get-in library-schema [:properties property %]))
       [:relatedID :relatedLabel]))

(defn get-related-id+label [attributes library-schema property]
  (map #(get attributes (keyword (get-in library-schema [:properties property %])))
       [:relatedID :relatedLabel]))

(defn get-initial-attributes [workspace]
  (utils/map-values
   unpack-attribute-list
   (dissoc (get-in workspace [:workspace :library-attributes]) :library:published)))

(defn find-required-attributes [library-schema]
  (->> (map :required (:oneOf library-schema))
       (concat (:required library-schema))
       flatten
       (map keyword)
       set))

(defn get-questions-for-page [working-attributes library-schema page-num]
  (when (< page-num (count (:wizard library-schema)))
    (let [page-props (get-in library-schema [:wizard page-num])
          {:keys [questions enumerate optionSource options]} page-props]
      (if optionSource
        (let [option-value (get working-attributes (keyword optionSource))]
          (when-let [option-match (some->> option-value keyword (get options))]
            (map option-match [:questions :enumerate])))
        [questions enumerate]))))

(defn remove-empty-values [attributes]
  (utils/filter-values
   (fn [val]
     (if (or (coll? val) (string? val))
       (not-empty val)
       true))
   attributes))

(defn validate-required [attributes questions required-attributes]
  (let [questions (reduce (fn [prev question]
                            (into prev
                                  (if (map? question) (:items question) [question])))
                          []
                          questions)
        required-props (->> questions
                            (map keyword)
                            (filter (partial contains? required-attributes))
                            set)
        missing-props (clojure.set/difference required-props (->> attributes (utils/filter-values some?) keys set))]
    (when (seq missing-props)
      {:error "Please provide all required attributes"
       :invalid missing-props})))

(defn render-value [value]
  (cond (sequential? value) (string/join ", " value)
        (common/attribute-list? value) (string/join ", " (common/attribute-values value))
        (true? value) "Yes"
        (false? value) "No"
        :else value))


(defn render-library-row [key value]
  (when value
    [:div {:style {:display "flex" :padding "0.5em 0" :borderBottom (str "2px solid " (:line-default style/colors))}}
     [:div {:style {:flexBasis "33%" :fontWeight "bold" :paddingRight "2em"}} key]
     [:div {:style {:flexBasis "67%"}} value]]))


(defn render-property [library-schema library-attributes property-key]
  (render-library-row
   (get-in library-schema [:properties property-key :title])
   (render-value (get library-attributes property-key))))

(defn render-consent [orsp-id consent]
  (render-library-row
   (str "Structured Data Use Limitations")
   [:span {:style {:whiteSpace "pre-wrap"}} (str orsp-id "\n" (:translatedUseRestriction consent))]))

(defn render-consent-error [orsp-id error]
  (render-library-row
   (str "Structured Data Use Limitations")
   (case (:code error)
     400 (str "Structured Data Use Limitations are not approved for " orsp-id)
     404 (str "Structured Data Use Limitations are not available for " orsp-id)
     error)))

(defn render-consent-code-value [value]
  (cond (true? value) "Yes"
        (false? value) "No"
        :else (unpack-attribute-list value)))


(defn render-consent-codes [library-schema library-attributes]
  (render-library-row
   "Structured Data Use Restrictions"
   (let [get-consent-code #(get-in library-schema [:properties % :consentCode])
         consent-codes (:consentCodes library-schema)
         consent-code-order (get-in library-schema [:display :consentCodes])
         consent-attributes (->> library-attributes
                                 (keep (fn [[key value]]
                                         (when-let [consent-code (get-consent-code key)]
                                           [consent-code value])))
                                 (sort-by (comp (partial utils/index-of consent-code-order) key)))]
     [:div {} (map (fn [[consent-code value]]
                     [:div {:style {:display "inline-block" :border style/standard-line :borderRadius 3
                                    :margin "0 0.2em 0.2em 0" :padding "0 0.4em"
                                    :cursor "help"}
                            :title (get consent-codes (keyword consent-code))}
                      (str consent-code ": " (render-consent-code-value value))])
                   consent-attributes)])))
