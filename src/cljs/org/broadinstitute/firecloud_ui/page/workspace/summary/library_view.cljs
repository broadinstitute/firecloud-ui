(ns org.broadinstitute.firecloud-ui.page.workspace.summary.library-view
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.catalog :refer [CatalogWizard]]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- render-value [value]
  (cond (sequential? value) (clojure.string/join ", " value)
        (common/attribute-list? value) (clojure.string/join ", " (common/attribute-values value))
        :else value))


(defn- render-property [library-schema library-attributes property-key]
  [:div {:style {:display "flex" :padding "0.5em 0" :borderBottom (str "2px solid " (:line-default style/colors))}}
   [:div {:style {:flexBasis "33%" :fontWeight "bold" :paddingRight "2em"}}
    (get-in library-schema [:properties property-key :title])]
   [:div {:style {:flexBasis "67%"}}
    (render-value (get library-attributes (keyword (str "library" property-key))))]])


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
              (map (partial render-property library-schema library-attributes) (-> library-schema :display :secondary)))
            [:div {:style {:marginTop "0.5em"}}
             (style/create-link {:text (if (:expanded? @state) "Collapse" "See more attributes")
                                 :onClick #(swap! state update :expanded? not)})]]])]))})
