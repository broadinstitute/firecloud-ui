(ns broadfcui.page.workspace.summary.library-view
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.page.workspace.summary.catalog.wizard :refer [CatalogWizard]]
    [broadfcui.page.workspace.summary.library-utils :as library-utils]
    [broadfcui.utils :as utils]
    ))


(defn- render-value [value]
  (cond (sequential? value) (clojure.string/join ", " value)
        (common/attribute-list? value) (clojure.string/join ", " (common/attribute-values value))
        :else value))

(defn- render-library-row [key value]
  (when value
    [:div {:style {:display "flex" :padding "0.5em 0" :borderBottom (str "2px solid " (:line-default style/colors))}}
     [:div {:style {:flexBasis "33%" :fontWeight "bold" :paddingRight "2em"}} key]
     [:div {:style {:flexBasis "67%"}} value]]))


(defn- render-property [library-schema library-attributes property-key]
  (render-library-row
   (get-in library-schema [:properties property-key :title])
   (render-value (get library-attributes property-key))))


(defn- render-consent-code-value [value]
  (cond (true? value) "Yes"
        (false? value) "No"
        :else (library-utils/unpack-attribute-list value)))


(defn- render-consent-codes [library-schema library-attributes]
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


(react/defc LibraryView
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [library-attributes library-schema]} props
           wizard-properties (select-keys props [:library-schema :workspace :workspace-id :request-refresh])]
       [:div {}
        (style/create-section-header
          [:div {}
           [:span {} "Dataset Attributes"]
           (when (:can-edit? props)
             (style/create-link {:style {:fontSize "0.8em" :fontWeight "normal" :marginLeft "1em"}
                                 :text "Edit..."
                                 :onClick #(modal/push-modal [CatalogWizard wizard-properties])}))])
        (style/create-paragraph
          [:div {}
           (map (partial render-property library-schema library-attributes) (-> library-schema :display :primary))
           [:div {}
            (when (:expanded? @state)
              [:div {}
               (map (partial render-property library-schema library-attributes) (-> library-schema :display :secondary))
               (render-consent-codes library-schema library-attributes)])
            [:div {:style {:marginTop "0.5em"}}
             (style/create-link {:text (if (:expanded? @state) "Collapse" "See more attributes")
                                 :onClick #(swap! state update :expanded? not)})]]])]))})
