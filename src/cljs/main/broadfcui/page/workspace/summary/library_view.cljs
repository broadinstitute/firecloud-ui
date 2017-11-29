(ns broadfcui.page.workspace.summary.library-view
  (:require
   [dmohs.react :as react]
   [broadfcui.common.links :as links]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.components.collapse :refer [Collapse]]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.workspace.summary.catalog.wizard :refer [CatalogWizard]]
   [broadfcui.page.workspace.summary.library-utils :as library-utils]
   [broadfcui.utils :as utils]
   ))




(react/defc LibraryView
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [library-attributes library-schema]} props
           wizard-properties (select-keys props [:library-schema :workspace :workspace-id :request-refresh :can-share? :owner? :curator? :writer? :catalog-with-read?])
           orsp-id (:library:orsp library-attributes)]
       [Collapse
        {:style {:marginBottom "2rem"}
         :title (style/create-section-header "Dataset Attributes")
         :title-expand
         (style/create-section-header
          (links/create-internal {:style {:fontSize "0.8em" :fontWeight "normal" :marginLeft "1em"}
                                  :onClick #(modal/push-modal [CatalogWizard wizard-properties])}
                                 "Edit..."))
         :contents
         [:div {:style {:marginTop "1rem" :fontSize "90%" :lineHeight 1.5}}
          (map (partial library-utils/render-property library-schema library-attributes) (-> library-schema :display :primary))
          (when (:expanded? @state)
            [:div {}
             (map (partial library-utils/render-property library-schema library-attributes) (-> library-schema :display :secondary))
             (if orsp-id
               (cond
                 (:consent @state) (library-utils/render-consent orsp-id (:consent @state))
                 (:consent-error @state) (library-utils/render-consent-error orsp-id (:consent-error @state))
                 :else (library-utils/render-library-row (str "Retrieving information for " orsp-id) (spinner)))
               (library-utils/render-consent-codes library-schema library-attributes))])
          [:div {:style {:marginTop "0.5em"}}
           (links/create-internal {:onClick #(swap! state update :expanded? not)}
                                  (if (:expanded? @state) "Collapse" "See more attributes"))]]}]))
   :component-did-mount
   (fn [{:keys [props state]}]
     (when-let [orsp-id (:library:orsp (:library-attributes props))]
       (endpoints/get-consent
        orsp-id
        (fn [{:keys [success? get-parsed-response]}]
          (swap! state assoc (if success? :consent :consent-error) (get-parsed-response))))))})
