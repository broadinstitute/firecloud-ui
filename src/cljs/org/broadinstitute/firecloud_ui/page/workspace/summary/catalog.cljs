(ns org.broadinstitute.firecloud-ui.page.workspace.summary.catalog
  (:require
    [cljsjs.typeahead-bundle]
    [clojure.string :refer [join split trim]]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.config :as config]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.library-utils :as library-utils]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- get-initial-attributes [workspace]
  (utils/map-values
    library-utils/unpack-attribute-list
    (dissoc (get-in workspace [:workspace :library-attributes]) :library:published)))


(def ^:private ENUM_EMPTY_CHOICE "<select an option>")

(defn resolve-enum [value]
  (when-not (= value ENUM_EMPTY_CHOICE)
    value))


(defn- parse-attributes [attributes library-schema]
  (utils/map-kv
    (fn [k v]
      (let [property (get-in library-schema [:properties k])
            {:keys [type items enum]} property
            value (if enum
                    (resolve-enum v)
                    (case type
                      "integer" (int v)
                      "array" (let [tokens (keep (comp not-empty trim) (split v #","))]
                                (case (:type items)
                                  "integer" (map int tokens)
                                  tokens))
                      v))]
        [k value]))
    attributes))


(defn- validate-required [attributes questions]
  (let [required-props (->> questions (filter :required) (map (comp keyword :property)))
        missing-props (set (remove attributes required-props))]
    (when-not (empty? missing-props)
      {:error "Please provide all required attributes"
       :invalid missing-props})))

(defn- validate-numbers [attributes library-schema]
  (let [numeric-props (->> attributes
                           (utils/filter-keys #(= "integer" (get-in library-schema [:properties % :type])))
                           (utils/map-kv (fn [k v]
                                           [k (merge (select-keys (get-in library-schema [:properties k]) [:minimum :maximum])
                                                     {:value v})])))
        invalid-numbers (set (keep (fn [[k {:keys [value minimum maximum] :or {minimum -Infinity maximum Infinity}}]]
                                     (when-not (and (= value (str (int value)))
                                                    (<= minimum (int value) maximum))
                                       k))
                                   numeric-props))]
    (when-not (empty? invalid-numbers)
      {:error "Invalid number"
       :invalid invalid-numbers})))


(react/defc Questions
  {:validate
   (fn [{:keys [props state locals]}]
     (let [{:keys [questions library-schema]} props
           processed-attributes (->> (:attributes @state)
                                     (utils/map-values (fn [val]
                                                         (if (string? val)
                                                           (not-empty (trim val))
                                                           val)))
                                     (utils/filter-values some?))
           {:keys [error invalid]} (or (validate-required processed-attributes questions)
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
           prop->string (fn [prop] (if (seq? prop) (join ", " prop) (str prop)))
           get-prop (fn [prop-key] (prop->string (get attributes prop-key
                                                      (get-in library-schema [:properties prop-key :default]))))]
       {:attributes
        (reduce (fn [map prop-key] (assoc map prop-key (get-prop prop-key)))
                {}
                (map (comp keyword :property) questions))
        :any-required? (some :required questions)}))
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [library-schema questions enumerate]} props]
       [(if enumerate :ol :div) {}
        (map
          (fn [{:keys [property required inputHint renderHint editable]}]
            (let [property-kwd (keyword property)
                  {:keys [title type typeahead enum minimum consentCode]} (get-in library-schema [:properties property-kwd])
                  error? (contains? (:invalid-properties @state) property-kwd)
                  colorize (fn [style] (merge style (when error? {:borderColor (:exception-state style/colors)})))
                  update-property #(swap! state update :attributes assoc property-kwd (.. % -target -value))
                  disabled? (false? editable)]
              [(if enumerate :li :div) {}
               [:div {:style {:marginBottom 2}}
                title
                (when consentCode
                  (list
                    " ["
                    [:abbr {:style {:cursor "help" :whiteSpace "nowrap" :borderBottom "1px dotted"}
                            :title (get-in library-schema [:consentCodes (keyword consentCode)])}
                     consentCode]
                    "]"))
                (when required
                  [:span {:style {:fontWeight "bold"
                                  :color (when error? (:exception-state style/colors))}}
                   " (required)"])]
               (cond enum
                     (if (< (count enum) 4)
                       [:div {:style {:display "inline-block"
                                      :margin "0.75em 0 0.75em 1em"}}
                        (map (fn [enum-val]
                               [:label {:style {:display "inline-flex" :alignItems "center" :cursor "pointer" :marginRight "2em"
                                                :color (when error? (:exception-state style/colors))}}
                                [:input (merge
                                          {:type "radio" :style {:cursor "pointer"}
                                           :onClick #(swap! state update :attributes assoc property-kwd enum-val)}
                                          (when (= enum-val (get (:attributes @state) property-kwd)) {:checked true}))]
                                [:div {:style {:padding "0 0.4em" :fontWeight "500"}} enum-val]])
                             enum)]
                       (style/create-identity-select {:value (get (:attributes @state) property-kwd ENUM_EMPTY_CHOICE)
                                                      :style (colorize {})
                                                      :onChange update-property}
                                                     (cons ENUM_EMPTY_CHOICE enum)))
                     (= renderHint "text")
                     (style/create-text-area {:style (colorize {:width "100%"})
                                              :value (get (:attributes @state) property-kwd)
                                              :onChange update-property
                                              :rows 3})
                     (= typeahead "ontology")
                     [:div {}
                      (style/create-text-field {:ref property-kwd
                                                :className "typeahead"
                                                :placeholder "Select an ontology value."
                                                :style {:width "100%" :marginBottom "0px"}})
                      (let [relatedID (library-utils/get-related-id (:attributes @state) library-schema property-kwd
                                                                    (library-utils/get-related-id (:attributes props) library-schema property-kwd nil))
                            value (get (:attributes @state) property-kwd)]
                        (if (not (or (nil? value) (nil? relatedID)))
                          [:div {:style {:fontWeight "bold" :marginBottom ".75em"}}
                           value [:span {:style {:fontWeight "normal" :fontSize "small" :float "right"}} relatedID]
                           [:div {:style {:fontWeight "normal"}}
                            (style/create-link {:text "Clear Selection"
                                                :onClick #(swap! state update :attributes assoc property-kwd nil
                                                                 (library-utils/get-related-id-keyword library-schema property-kwd) nil)})]]))]
                     :else
                     (style/create-text-field {:style (colorize {:width "100%"})
                                               :type (cond (= renderHint "date") "date"
                                                           (= renderHint "email") "email"
                                                           (= type "integer") "number"
                                                           :else "text")
                                               :min minimum
                                               :disabled disabled?
                                               :placeholder inputHint
                                               :value (get (:attributes @state) property-kwd)

                                               :onChange update-property}))]))
          questions)]))
   :component-did-mount
   (fn [{:keys [props state refs]}]
     (let [{:keys [library-schema questions]} props]
       (doseq [{:keys [property]} questions]
         (let [property-kwd (keyword property)
               {:keys [typeahead relatedID]} (get-in library-schema [:properties property-kwd])
               options  (js/Bloodhound. (clj->js
                                          {:datumTokenizer js/Bloodhound.tokenizers.whitespace
                                           :queryTokenizer js/Bloodhound.tokenizers.whitespace
                                           :remote (clj->js {:url (str (config/api-url-root) "/duos/autocomplete/%QUERY")
                                                             :wildcard "%QUERY"})}))]
             (if (= typeahead "ontology")
               (do
                 (.typeahead (js/$ (@refs property))
                             (clj->js {:highlight true
                                       :hint true
                                       :minLength 3})
                             (clj->js
                               {:source options
                                :display (fn [result]
                                           (aget result "label"))
                                :templates (clj->js
                                             {:empty "<div> unable to find any matches to the current query </div>"
                                              :suggestion
                                              (fn [result]
                                                (str "<div> <div style='line-height: 1.5em;'>" (aget result "label")
                                                     "<small style='float: right;'>" (aget result "id") "</small></div>"
                                                     "<small style='font-style: italic;'> " (aget result "definition") "</small></div>"))})}))
                 (.bind (js/$ (@refs property))
                        "typeahead:select"
                        (fn [ev suggestion]
                          (swap! state update :attributes assoc property-kwd (aget suggestion "label") (keyword relatedID) (aget suggestion "id"))))))))))})


(react/defc Options
  {:validate
   (fn [{:keys [state refs]}]
     (if-not (:selected-index @state)
       "Please select an option"
       (react/call :validate (@refs "questions"))))
   :get-attributes
   (fn [{:keys [refs]}]
     (react/call :get-attributes (@refs "questions")))
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [switch]} props]
       [:div {}
        [:div {} (:title switch)]
        (interpose
          [:div {:style {:display "flex" :alignItems "center" :color (:text-lightest style/colors)}}
           [:hr {:style {:flex "1 1 auto"}}]
           [:span {:style {:flex "0 0 auto" :margin "0 0.5em" :fontSize "75%"}} "OR"]
           [:hr {:style {:flex "1 1 auto"}}]]
          (map-indexed
            (fn [index {:keys [title enumerate questions]}]
              (let [selected (= index (:selected-index @state))]
                [:div {}
                 [:label {:style {:display "flex" :alignItems "center" :cursor "pointer"}}
                  [:input (merge {:type "radio" :style {:cursor "pointer"}
                                  :onClick #(swap! state assoc :selected-index index)}
                                 (when selected {:checked true}))]
                  [:div {:style {:padding "1em"}} title]]
                 (when selected
                   [:div {:style {:marginBottom "1.5em"}}
                    [Questions (merge (select-keys props [:library-schema :attributes])
                                      {:ref "questions" :enumerate enumerate :questions questions})]])]))
            (:options switch)))]))})


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
       (cond questions [Questions (merge (select-keys props [:library-schema :attributes])
                                         {:ref "subcomponent" :enumerate enumerate :questions questions})]
             switch [Options (merge (select-keys props [:library-schema :attributes])
                                    {:ref "subcomponent" :switch switch})])))})


(defn- render-wizard-breadcrumbs [{:keys [library-schema page-num]}]
  (let [pages (:wizard library-schema)]
    [:div {:style {:flex "0 0 250px"}}
     [:ul {}
      (map-indexed
        (fn [index {:keys [title]}]
          (let [this (= index page-num)]
            [:li {:style {:margin "0.5em 0.5em 0.5em 0"
                          :fontWeight (when this "bold")
                          :color (when-not this (:text-lighter style/colors))}}
             title]))
        pages)]]))


(react/defc CatalogWizard
  {:get-initial-state
   (fn [{:keys [props]}]
     {:page 0
      :initial-attributes (get-initial-attributes (:workspace props))
      :saved-attributes []})
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
         "Catalog Data Set"
         [comps/XButton {:dismiss modal/pop-modal}]]
        [:div {:style {:padding "22px 24px 40px" :backgroundColor (:background-light style/colors)}}
         [:div {:style {:display "flex" :width 850}}
          (render-wizard-breadcrumbs {:library-schema library-schema :page-num page})
          [:div {:style {:flex "0 0 600px" :maxHeight 400 :padding "1em" :overflow "auto"
                         :border style/standard-line :boxSizing "border-box"}}
           [WizardPage {:key page
                        :ref "wizard-page"
                        :library-schema library-schema
                        :page-num page
                        :attributes (or (get-in @state [:saved-attributes page])
                                        (:initial-attributes @state))}]]]
         (when-let [error (:validation-error @state)]
           [:div {:style {:marginTop "1em" :color (:exception-state style/colors) :textAlign "center"}}
            error])
         [comps/ErrorViewer {:error (:submit-error @state)}]
         [:div {:style {:marginTop 40 :textAlign "center"}}
          [:a {:className "cancel"
               :style {:marginRight 27 :marginTop 2 :padding "0.5em"
                       :display "inline-block"
                       :fontSize "106%" :fontWeight 500 :textDecoration "none"
                       :color (:button-primary style/colors)}
               :href "javascript:;"
               :onClick modal/pop-modal
               :onKeyDown (common/create-key-handler [:space :enter] modal/pop-modal)}
           "Cancel"]
          [comps/Button {:text "Previous"
                         :onClick (fn [_] (swap! state #(-> % (update :page dec) (dissoc :validation-error))))
                         :style {:width 80 :marginRight 27}
                         :disabled? (zero? page)}]
          [comps/Button {:text (if (< page (-> library-schema :wizard count dec)) "Next" "Submit")
                         :onClick #(react/call :next-page this)
                         :style {:width 80}}]]]]))
   :next-page
   (fn [{:keys [props state refs this after-update]}]
     (swap! state dissoc :validation-error)
     (if-let [error-message (react/call :validate (@refs "wizard-page"))]
       (swap! state assoc :validation-error error-message)
       (let [attributes-from-page (react/call :get-attributes (@refs "wizard-page"))]
         (swap! state update :saved-attributes assoc (:page @state) attributes-from-page)
         (after-update
          #(if (< (:page @state) (-> props :library-schema :wizard count dec))
             (swap! state update :page inc)
             (react/call :submit this))))))
   :submit
   (fn [{:keys [props state]}]
     (swap! state assoc :submitting? true :submit-error nil)
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/save-library-metadata (:workspace-id props))
        :payload (apply merge (:saved-attributes @state))
        :headers utils/content-type=json
        :on-done (fn [{:keys [success? get-parsed-response]}]
                   (swap! state dissoc :submitting?)
                   (if success?
                     (do (modal/pop-modal)
                         ((:request-refresh props)))
                     (swap! state assoc :submit-error (get-parsed-response false))))}))})


(react/defc CatalogButton
  {:render
   (fn [{:keys [props]}]
     [comps/SidebarButton
      {:style :light :color :button-primary :margin :top
       :icon :catalog :text "Catalog Dataset..."
       :onClick #(modal/push-modal [CatalogWizard props])}])})
