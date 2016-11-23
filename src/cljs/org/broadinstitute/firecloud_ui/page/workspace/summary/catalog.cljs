(ns org.broadinstitute.firecloud-ui.page.workspace.summary.catalog
  (:require
    [clojure.string :refer [join split trim]]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.input :as input]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- get-initial-attributes [workspace]
  (utils/map-kv
    (fn [k v]
      [;; strip off the "library:" from the key
       (let [[_ _ attr] (re-find #"(.*):(.*)" (name k))]
         (keyword attr))
       ;; unpack lists and convert everything to a string
       (if (map? v)
         (join ", " (:items v))
         (str v))])
    (get-in workspace [:workspace :library-attributes])))


(def ^:private ENUM_EMPTY_CHOICE "<select an option>")

(defn resolve-enum [value]
  (when-not (= value ENUM_EMPTY_CHOICE)
    value))


(defn- parse-attributes [attributes library-schema]
  (utils/map-kv
    (fn [k v]
      (let [property (get-in library-schema [:properties k])
            {:keys [type items]} property
            value (case type
                    "integer" (int v)
                    "array" (let [tokens (keep (comp not-empty trim) (split v #","))]
                              (case (:type items)
                                "integer" (map int tokens)
                                tokens))
                    v)]
        [k value]))
    attributes))


(react/defc Questions
  {:validate
   (fn [{:keys [props state this]}]
     (let [{:keys [questions]} props
           required-props (->> questions (filter :required) (map (comp keyword :property)))
           processed-attributes (->> (:attributes @state)
                                     (utils/map-values (fn [val]
                                                         (if (string? val)
                                                           (not-empty (trim val))
                                                           val)))
                                     (utils/filter-values some?))
           missing-props (set (remove processed-attributes required-props))]
       (set! (.-processed-attributes this) processed-attributes)
       (when-not (empty? missing-props)
         (swap! state assoc :invalid-properties missing-props)
         "Please provide all required attributes")))
   :get-attributes
   (fn [{:keys [props this]}]
     (parse-attributes (.-processed-attributes this) (:library-schema props)))
   :get-initial-state
   (fn [{:keys [props]}]
     (let [{:keys [questions attributes library-schema]} props]
       {:attributes
        (reduce (fn [map prop-key] (assoc map prop-key (get attributes prop-key
                                                            (get-in library-schema [:properties prop-key :default]))))
                {}
                (map (comp keyword :property) questions))
        :any-required? (some :required questions)}))
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [library-schema questions enumerate]} props]
       [(if enumerate :ol :div) {}
        (map
          (fn [{:keys [property required inputHint]}]
            (let [property-kwd (keyword property)
                  {:keys [title type enum minimum consentCode]} (get-in library-schema [:properties property-kwd])
                  error? (contains? (:invalid-properties @state) property-kwd)
                  colorize (fn [style] (merge style (when error? {:borderColor (:exception-state style/colors)})))
                  update-property #(swap! state update :attributes assoc property-kwd (.. % -target -value))]
              [(if enumerate :li :div) {}
               [:div {}
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
                     (= type "text")
                     (style/create-text-area {:style (colorize {:width "100%"})
                                              :value (get (:attributes @state) property-kwd)
                                              :onChange update-property
                                              :rows 3})
                     :else
                     (style/create-text-field {:style (colorize {:width "100%"})
                                               :type (case type
                                                       "date" "date"
                                                       "integer" "number"
                                                       "text")
                                               :min minimum
                                               :placeholder inputHint
                                               :value (get (:attributes @state) property-kwd)
                                               :onChange update-property}))]))
          questions)]))})


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
      :attributes (get-initial-attributes (:workspace props))})
   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [library-schema]} props]
       [:div {}
        [:div {:style {:borderBottom style/standard-line
                       :padding "20px 48px 18px"
                       :fontSize "137%" :fontWeight 400 :lineHeight 1}}
         "Catalog Data Set"
         [comps/XButton {:dismiss modal/pop-modal}]]
        [:div {:style {:padding "22px 24px 40px" :backgroundColor (:background-light style/colors)}}
         [:div {:style {:display "flex" :width 850}}
          (render-wizard-breadcrumbs {:library-schema library-schema :page-num (:page @state)})
          [:div {:style {:flex "0 0 600px" :maxHeight 400 :padding "1em" :overflow "auto"
                         :border style/standard-line :boxSizing "border-box"}}
           [WizardPage {:key (:page @state)
                        :ref "wizard-page"
                        :library-schema library-schema
                        :page-num (:page @state)
                        :attributes (:attributes @state)}]]]
         (when-let [error (:validation-error @state)]
           [:div {:style {:marginTop "1em" :color (:exception-state style/colors) :textAlign "center"}}
            error])
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
                         :disabled? (zero? (:page @state))}]
          [comps/Button {:text (if (< (:page @state) (-> library-schema :wizard count dec)) "Next" "Submit")
                         :onClick #(react/call :next-page this)
                         :style {:width 80}}]]]]))
   :next-page
   (fn [{:keys [props state refs]}]
     (swap! state dissoc :validation-error)
     (if-let [error-message (react/call :validate (@refs "wizard-page"))]
       (swap! state assoc :validation-error error-message)
       (let [new-attributes (merge (:attributes @state) (react/call :get-attributes (@refs "wizard-page")))]
         (if (< (:page @state) (-> props :library-schema :wizard count dec))
           (swap! state #(-> % (update :page inc) (assoc :attributes new-attributes)))
           (utils/cljslog "TODO: submit" new-attributes)))))})


(react/defc CatalogButton
  {:render
   (fn [{:keys [props]}]
     [comps/SidebarButton
      {:style :light :color :button-primary :margin :top
       :icon :catalog :text "Catalog Dataset..."
       :onClick #(modal/push-modal [CatalogWizard props])}])})
