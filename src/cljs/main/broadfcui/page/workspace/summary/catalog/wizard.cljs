(ns broadfcui.page.workspace.summary.catalog.wizard
  (:require
    [clojure.string :refer [join split trim]]
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.flex-utils :as flex]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.page.workspace.summary.catalog.options :refer [Options]]
    [broadfcui.page.workspace.summary.catalog.questions :refer [Questions]]
    [broadfcui.page.workspace.summary.library-utils :as library-utils]
    [broadfcui.utils :as utils]
    ))


(react/defc WizardPage
  {:validate
   (fn [{:keys [refs]}]
     (react/call :validate (@refs "subcomponent")))
   :get-attributes
   (fn [{:keys [refs]}]
     (react/call :get-attributes (@refs "subcomponent")))
   :render
   (fn [{:keys [props]}]
     (let [{:keys [library-schema page-num]} props
           page (get-in library-schema [:wizard page-num])
           {:keys [questions enumerate switch]} page]
       (cond questions [Questions (merge (select-keys props [:library-schema :attributes :required-attributes])
                                         {:ref "subcomponent" :enumerate enumerate :questions questions})]
             switch [Options (merge (select-keys props [:library-schema :attributes :required-attributes])
                                    {:ref "subcomponent" :switch switch})])))})


(defn- render-wizard-breadcrumbs [{:keys [library-schema page-num]}]
  (let [pages (:wizard library-schema)]
    [:div {:style {:flex "0 0 250px" :backgroundColor "white" :border style/standard-line}}
     [:ul {}
      (map-indexed
       (fn [index {:keys [title]}]
         (let [this (= index page-num)]
           [:li {:style {:margin "0.5em 0.5em 0.5em 0"
                         :fontWeight (when this "bold")
                         :color (when-not this (:text-lighter style/colors))}}
            title]))
       pages)]]))


(defn- find-required-attributes [library-schema]
  (->> (map :required (:oneOf library-schema))
       (concat (:required library-schema))
       flatten
       (map keyword)
       set))


(defn- get-initial-attributes [workspace]
  (utils/map-values
   library-utils/unpack-attribute-list
   (dissoc (get-in workspace [:workspace :library-attributes]) :library:published)))


(react/defc CatalogWizard
  {:get-initial-state
   (fn [{:keys [props]}]
     (let [{:keys [library-schema]} props
           {:keys [versions]} library-schema]
       {:page 0
        :initial-attributes (get-initial-attributes (:workspace props))
        :version-attributes (->> versions
                                 (map keyword)
                                 (map (fn [version]
                                        [version (get-in library-schema [:properties version :default])]))
                                 (into {}))
        :attributes-from-pages []
        :required-attributes (find-required-attributes library-schema)}))
   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [library-schema]} props
           {:keys [page]} @state]
       [:div {}
        (when (:submitting? @state)
          [comps/Blocker {:banner "Submitting..."}])
        [:div {:style {:borderBottom style/standard-line
                       :padding "20px 48px 18px"
                       :fontSize "137%" :fontWeight 400 :lineHeight 1}}
         "Catalog Dataset"
         [comps/XButton {:dismiss modal/pop-modal}]]
        [:div {:style {:padding "22px 24px 40px" :backgroundColor (:background-light style/colors)}}
         [:div {:style {:display "flex" :width 850 :height 400}}
          (render-wizard-breadcrumbs {:library-schema library-schema :page-num page})
          [comps/ScrollFader
           {:outer-style {:flex "1 1 auto"
                          :border style/standard-line :boxSizing "border-box"
                          :backgroundColor "white"}
            :inner-style {:padding "1rem" :boxSizing "border-box" :height "100%"}
            :content
            (react/create-element
             [WizardPage {:key page
                          :ref "wizard-page"
                          :library-schema library-schema
                          :page-num page
                          :attributes (or (get-in @state [:attributes-from-pages page])
                                          (:initial-attributes @state))
                          :required-attributes (:required-attributes @state)}])}]]
         (when-let [error (:validation-error @state)]
           [:div {:style {:marginTop "1em" :color (:exception-state style/colors) :textAlign "center"}}
            error])
         [comps/ErrorViewer {:error (:submit-error @state)}]
         (flex/flex-box {:style {:marginTop 40}}
          (flex/flex-strut 80)
          flex/flex-spacer
          [comps/Button {:text "Previous"
                         :onClick (fn [_] (swap! state #(-> % (update :page dec) (dissoc :validation-error))))
                         :style {:width 80}
                         :disabled? (zero? page)}]
          (flex/flex-strut 27)
          [comps/Button {:text "Next"
                         :onClick #(react/call :next-page this)
                         :disabled? (= page (-> library-schema :wizard count dec))
                         :style {:width 80}}]
          flex/flex-spacer
          [comps/Button {:text "Submit"
                         :onClick #(react/call :next-page this)
                         :disabled? (< page (-> library-schema :wizard count dec))
                         :style {:width 80}}])]]))
   :next-page
   (fn [{:keys [props state refs this after-update]}]
     (swap! state dissoc :validation-error)
     (if-let [error-message (react/call :validate (@refs "wizard-page"))]
       (swap! state assoc :validation-error error-message)
       (let [attributes-from-page (react/call :get-attributes (@refs "wizard-page"))]
         (swap! state update :attributes-from-pages assoc (:page @state) attributes-from-page)
         (after-update
          #(if (< (:page @state) (-> props :library-schema :wizard count dec))
             (swap! state update :page inc)
             (react/call :submit this))))))
   :submit
   (fn [{:keys [props state]}]
     (swap! state assoc :submitting? true :submit-error nil)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/save-library-metadata (:workspace-id props))
       :payload (apply merge (:version-attributes @state) (:attributes-from-pages @state))
       :headers utils/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (swap! state dissoc :submitting?)
                  (if success?
                    (do (modal/pop-modal)
                        ((:request-refresh props)))
                    (swap! state assoc :submit-error (get-parsed-response false))))}))})
