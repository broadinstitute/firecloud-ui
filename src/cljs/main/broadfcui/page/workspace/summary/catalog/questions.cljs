(ns broadfcui.page.workspace.summary.catalog.questions
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.links :as links]
   [broadfcui.common.markdown :as markdown]
   [broadfcui.common.style :as style]
   [broadfcui.components.autosuggest :refer [Autosuggest]]
   [broadfcui.components.ontology-autosuggest :as ontology]
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
                             (keep (fn [[k {:keys [value minimum maximum] :or {minimum js/-Infinity maximum js/Infinity}}]]
                                     (when-not (and (or
                                                     (= value (str (int value)))
                                                     (= value (int value)))
                                                    (<= minimum (int value) maximum))
                                       k)))
                             set)]
    (when (seq invalid-numbers)
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
                             :backgroundColor (cond disabled (:state-disabled style/colors) selected? (:button-primary style/colors))
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

(defn- render-markdown [{:keys [value-nullsafe set-property prop]}]
  [:div {:style {:marginTop "0.5rem"}}
   (style/create-textfield-hint (:inputHint prop))
   [markdown/MarkdownEditor {:value value-nullsafe
                             :on-change set-property
                             :initial-slider-position 250}]])

;; Needed to handle DS labels before adding the DS_URL field
(defn- handle-differences [[related-id related-label]]
  (if (or (nil? related-id) (nil? related-label))
    ["" "" true]
    [related-id related-label false]))

(defn- render-ontology-typeahead [{:keys [refs prop value-nullsafe set-property state property library-schema disabled]}]
  (let [[related-id-prop related-label-prop] (library-utils/get-related-id+label-props library-schema property)
        [related-id related-label clear-fields] (handle-differences (library-utils/get-related-id+label (:attributes @state) library-schema property))
        clear (fn []
                (apply swap! state update :attributes dissoc property [related-id-prop related-label-prop])
                ((@refs (name property)) :set-value ""))]
    [:div {:style {:marginBottom "0.75em"}}
     (if (= (:type prop) "array")
       [:div {}
        (ontology/render-multiple-ontology-selections
         {:on-delete (fn [{:keys [id label]}]
                       (utils/multi-swap! state
                         (update-in [:attributes related-id-prop] library-utils/remove-from-comma-separated-strings id)
                         (update-in [:attributes related-label-prop] library-utils/remove-from-comma-separated-strings label)))
          :selection-map (library-utils/zip-comma-separated-strings related-id related-label)})
        (ontology/create-ontology-autosuggest
         {:on-suggestion-selected
          (fn [{:keys [id label]}]
            (if clear-fields
              (swap! state update :attributes assoc related-id-prop id related-label-prop label)
              (utils/multi-swap! state
                (update-in [:attributes related-id-prop] library-utils/add-to-comma-separated-strings id)
                (update-in [:attributes related-label-prop] library-utils/add-to-comma-separated-strings label))))
          :selected-ids (library-utils/split-attributes related-id)})]
       [:div {}
        (ontology/create-ontology-autosuggest
         {:ref (name property)
          :value value-nullsafe
          :on-suggestion-selected
          (fn [{:keys [id label]}]
            (swap! state update :attributes assoc
                   related-id-prop id
                   related-label-prop label))
          :input-props {:placeholder (:inputHint prop)
                        :data-test-id property
                        :disabled disabled}
          :on-change set-property
          :on-submit #(when (empty? %) (clear))})

        (when (not-any? string/blank? [related-id related-label])
          [:div {}
           [:span {:style {:fontWeight "bold"}} related-label]
           [:span {:style {:fontSize "small" :float "right"}} related-id]
           [:div {}
            (when-not disabled
              (links/create-internal {:onClick clear}
                "Clear Selection"))]])])]))

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
        (reduce (fn [prev prop]
                  (if (map? prop)
                    (reduce (fn [prev-inner prop-inner]
                              (let [prop-inner-key (keyword prop-inner)]
                                (assoc prev-inner prop-inner-key (get-prop prop-inner-key))))
                            prev
                            (:items prop))
                    (let [prop-key (keyword prop)]
                      (assoc prev prop-key (get-prop prop-key)))))
                {}
                questions)}))
   :render
   (fn [{:keys [props this]}]
     (let [{:keys [questions enumerate]} props]
       [(if enumerate :ol :div) {}
        (map
         #(this :-render-question %)
         questions)]))
   :-render-question
   (fn [{:keys [props refs state this]} property & [nested?]]
     (let [{:keys [library-schema missing-properties required-attributes enumerate editable?]} props
           {:keys [attributes invalid-properties]} @state]
       (if (map? property)
         (let [{:keys [requireGroup items]} property
               {:keys [required renderHint] :as prop} (get-in library-schema [:requireGroups (keyword requireGroup)])
               error? (when (not-any?
                             (fn [item]
                               (let [value (get attributes item)]
                                 ((cond
                                    (string? value) string/blank?
                                    (array? value) empty?
                                    :else boolean)) value))
                             (map keyword items))
                        true)
               colorize #(merge % (when error? {:borderColor (:state-exception style/colors)
                                                :color (:state-exception style/colors)}))]
           [(if enumerate :li :div) {}
            [:div {}
             (render-header {:prop prop :required? required :colorize colorize})
             [:small {:style (when error? {:color (:state-exception style/colors)})} (:wording renderHint)]
             [:ul {:style {:border (str ((if error? :state-exception :line-default) style/colors) " solid 1px") :borderRadius 3
                           :margin "0.5rem 0 1rem" :padding "0.75rem"}}
              (map #(this :-render-question % true) items)]]])
         (let [property (keyword property)
               current-value (get attributes property)
               {:keys [type enum renderHint] :as prop} (get-in library-schema [:properties property])
               error? (or (contains? invalid-properties property) (contains? missing-properties property))
               colorize #(merge % (when error? {:borderColor (:state-exception style/colors)
                                                :color (:state-exception style/colors)}))
               data (merge (utils/restructure prop refs state property library-schema colorize current-value)
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
             [(if (or enumerate nested?) :li :div) {:style (when nested? {:display "block"})}
              (render-header data)
              (cond enum (render-enum data)
                    (= type "boolean") (render-boolean data)
                    (= (:datatype renderHint) "freetext") (render-freetext data)
                    (= (:datatype renderHint) "markdown") (render-markdown data)
                    (= (:typeahead prop) "ontology") (render-ontology-typeahead data)
                    (= (:typeahead prop) "populate") (render-populate-typeahead data)
                    :else (render-textfield data))])))))})
