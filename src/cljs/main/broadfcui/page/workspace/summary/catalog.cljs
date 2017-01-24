(ns broadfcui.page.workspace.summary.catalog
  (:require
    [clojure.string :refer [join split trim]]
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.config :as config]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.page.workspace.summary.library-utils :as library-utils]
    [broadfcui.utils :as utils]
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
  (let [numeric-props (->> attributes
                           (utils/filter-keys #(= "integer" (get-in library-schema [:properties % :type])))
                           (utils/map-kv (fn [k v]
                                           [k (merge (select-keys (get-in library-schema [:properties k]) [:minimum :maximum])
                                                     {:value v})])))
        invalid-numbers (set (keep (fn [[k {:keys [value minimum maximum] :or {minimum -Infinity maximum Infinity}}]]
                                     (when-not (and (or
                                                      (= value (str (int value)))
                                                      (= value (int value)))
                                                    (<= minimum (int value) maximum))
                                       k))
                                   numeric-props))]
    (when-not (empty? invalid-numbers)
      {:error "Invalid number"
       :invalid invalid-numbers})))


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
           (let [{:keys [title type typeahead enum minimum consentCode hidden renderHint inputHint relatedID relatedLabel]} (get-in library-schema [:properties property])
                 {:keys [wording datatype emptyChoice]} renderHint
                 required? (contains? required-attributes property)
                 error? (contains? invalid-properties property)
                 colorize (fn [style] (merge style (when error? {:borderColor (:exception-state style/colors)})))
                 update-property #(swap! state update :attributes assoc property (.. % -target -value))
                 checkbox (fn [{:keys [val label]}]
                            [:label {:style {:display "inline-flex" :alignItems "center" :cursor "pointer" :marginRight "2em"
                                             :color (when error? (:exception-state style/colors))}}
                             [:input (merge
                                      {:type "radio" :style {:cursor "pointer"}
                                       :onClick #(swap! state update :attributes assoc property val)}
                                      (when (= val (get attributes property)) {:checked true}))]
                             [:div {:style {:padding "0 0.4em" :fontWeight "500"}} (or label (str val))]])]
             (when-not hidden
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
                 (when required?
                   [:span {:style {:fontWeight "bold" :color (when error? (:exception-state style/colors))}}
                    " (required)"])]
                (cond enum
                      (if (< (count enum) 4)
                        [:div {:style {:display "inline-block" :margin "0.75em 0 0.75em 1em"}}
                         (map #(checkbox {:val %}) enum)]
                        (style/create-identity-select {:value (get attributes property ENUM_EMPTY_CHOICE)
                                                       :style (colorize {})
                                                       :onChange update-property}
                                                      (cons ENUM_EMPTY_CHOICE enum)))
                      (= type "boolean")
                      [:div {:style {:display "inline-block" :margin "0.75em 0 0.75em 1em"}}
                       (checkbox {:val true :label (case wording "yes/no" "Yes" "True")})
                       (checkbox {:val false :label (case wording "yes/no" "No" "False")})
                       (when-not required?
                         (checkbox {:val nil :label (or emptyChoice "N/A")}))]
                      (= datatype "freetext")
                      (style/create-text-area {:style (colorize {:width "100%"})
                                               :value (get attributes property)
                                               :onChange update-property
                                               :rows 3})
                      (= typeahead "ontology")
                      [:div {:style {:marginBottom "0.75em"}}
                       [comps/Typeahead {:field-attributes {:placeholder "Select an ontology value."
                                                            :style (colorize {:width "100%" :marginBottom "0px"})
                                                            :value (get attributes property)
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
                                                      (swap! state update :attributes assoc
                                                             property (aget suggestion "label")
                                                             (keyword relatedLabel) (aget suggestion "label")
                                                             (keyword relatedID) (aget suggestion "id")))}]
                       (let [relatedID (library-utils/get-related-value attributes library-schema property true)
                             relatedLabel (library-utils/get-related-value attributes library-schema property false)]
                         (if (not (or (clojure.string/blank? relatedID) (clojure.string/blank? relatedLabel)))
                           [:div {:style {:fontWeight "bold"}}
                            relatedLabel [:span {:style {:fontWeight "normal" :fontSize "small" :float "right"}} relatedID]
                            [:div {:style {:fontWeight "normal"}}
                             (style/create-link {:text "Clear Selection"
                                                 :onClick #(swap! state update :attributes dissoc
                                                                  (library-utils/get-related-label-keyword library-schema property)
                                                                  (library-utils/get-related-id-keyword library-schema property))})]]))]
                      :else
                      (style/create-text-field {:style (colorize {:width "100%"})
                                                :type (cond (= datatype "date") "date"
                                                            (= datatype "email") "email"
                                                            (= type "integer") "number"
                                                            :else "text")
                                                :min minimum
                                                :placeholder inputHint
                                                :value (get attributes property)
                                                :onChange update-property}))])))
         (map keyword questions))]))})

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
                    [Questions (merge (select-keys props [:library-schema :attributes :required-attributes])
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
       (cond questions [Questions (merge (select-keys props [:library-schema :attributes :required-attributes])
                                         {:ref "subcomponent" :enumerate enumerate :questions questions})]
             switch [Options (merge (select-keys props [:library-schema :attributes :required-attributes])
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


(defn- find-required-attributes [library-schema]
  (->> (map :required (:oneOf library-schema))
       (concat (:required library-schema))
       flatten
       (map keyword)
       set))


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
                        :attributes (or (get-in @state [:attributes-from-pages page])
                                        (:initial-attributes @state))
                        :required-attributes (:required-attributes @state)}]]]
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


(react/defc CatalogButton
  {:render
   (fn [{:keys [props]}]
     [comps/SidebarButton
      {:style :light :color :button-primary :margin :top
       :icon :catalog :text "Catalog Dataset..."
       :onClick #(modal/push-modal [CatalogWizard props])}])})
