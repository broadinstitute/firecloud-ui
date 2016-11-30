(ns org.broadinstitute.firecloud-ui.page.workspace.summary.library-view
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.catalog :refer [CatalogWizard]]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.library-utils :as library-utils]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- render-value [value]
  (cond (sequential? value) (clojure.string/join ", " value)
        (common/attribute-list? value) (clojure.string/join ", " (common/attribute-values value))
        :else value))

(defn- render-library-row [key value]
  [:div {:style {:display "flex" :padding "0.5em 0" :borderBottom (str "2px solid " (:line-default style/colors))}}
   [:div {:style {:flexBasis "33%" :fontWeight "bold" :paddingRight "2em"}} key]
   [:div {:style {:flexBasis "67%"}} value]])


(defn- render-property [library-schema library-attributes property-key]
  (render-library-row
   (get-in library-schema [:properties property-key :title])
   (render-value (get library-attributes (keyword (str "library" property-key))))))


(defn- render-consent-code [[consent-code value] consent-codes]
  [:div {:style {:display "inline-block" :border style/standard-line :borderRadius 3
                 :margin "0.2em" :padding "0 0.4em"
                 :cursor "help"}
         :title (get consent-codes (keyword consent-code))}
   (str consent-code ": " value)])

(defn- render-consent-codes [library-schema library-attributes]
  (render-library-row
   "Structured Data Use Restrictions"
   (let [consent-attributes (keep (fn [[key value]]
                                    (when-let [consent-code (get-in library-schema [:properties (library-utils/strip-library-prefix key) :consentCode])]
                                      [consent-code value]))
                                  library-attributes)]
     [:div {} (map #(render-consent-code % (:consentCodes library-schema)) consent-attributes)])))


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
