(ns broadfcui.page.workspace.summary.library-utils
  (:require
    [broadfcui.utils :as utils]
    [broadfcui.common :as common]
    [broadfcui.common.style :as style]))

(defn unpack-attribute-list [value]
  (if (map? value)
    (clojure.string/join ", " (:items value))
    value))


(defn get-related-id+label-props [library-schema property]
  (map #(keyword (get-in library-schema [:properties property %]))
       [:relatedID :relatedLabel]))

(defn get-related-id+label [attributes library-schema property]
  (map #(get attributes (keyword (get-in library-schema [:properties property %])))
       [:relatedID :relatedLabel]))

(defn validate-required [attributes questions required-attributes]
  (let [required-props (->> questions
                            (map keyword)
                            (filter (partial contains? required-attributes))
                            set)
        missing-props (clojure.set/difference required-props (->> attributes (utils/filter-values some?) keys set))]
    (when-not (empty? missing-props)
      {:error "Please provide all required attributes"
       :invalid missing-props})))

(defn render-value [value]
  (cond (sequential? value) (clojure.string/join ", " value)
        (common/attribute-list? value) (clojure.string/join ", " (common/attribute-values value))
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
    (let [code (:code error)
          unapproved (= code 400)
          not-found (= code 404)]
      (cond
        unapproved (str "Structured Data Use Limitations are not approved for " orsp-id)
        not-found (str "Structured Data Use Limitations are not available for " orsp-id)
        :else error))))

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
