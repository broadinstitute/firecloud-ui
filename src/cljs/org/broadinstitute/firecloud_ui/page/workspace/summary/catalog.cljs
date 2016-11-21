(ns org.broadinstitute.firecloud-ui.page.workspace.summary.catalog
  (:require
    [clojure.string :refer [trim]]
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
  (->> workspace :workspace :library-attributes
       (map (fn [[k v]]
              [;; strip off the "library:" from the key
               (let [[_ _ attr] (re-find #"(.*):(.*)" (name k))]
                 (keyword attr))
               ;; unpack list type
               (if (map? v)
                 (:items v)
                 v)]))
       (into {})))


(defn- render-value [value]
  (cond (sequential? value) (clojure.string/join ", " value)
        (common/attribute-list? value) (clojure.string/join ", " (common/attribute-values value))
        :else value))

(def ^:private ENUM_EMPTY_CHOICE "<select an option>")

(defn resolve-enum [value]
  (when-not (= value ENUM_EMPTY_CHOICE)
    value))


(react/defc Questions
  {:validate
   (fn [{:keys []}]
     (utils/log "validating questions")
     nil)
   :get-attributes
   (fn [{:keys [state]}]
     (->> (:attributes @state)
          (filter (fn [[_ v]] (some? v)))
          (into {})))
   :get-initial-state
   (fn [{:keys [props]}]
     {:attributes
      (let [{:keys [questions attributes library-schema]} props]
        (reduce (fn [m k] (assoc m k (get attributes k
                                          (get-in library-schema [:properties k :default]))))
                {}
                (map (comp keyword :property) questions)))})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [library-schema questions enumerate]} props]
       [:div {}
        (map-indexed
          (fn [index {:keys [property required inputHint footerHint]}]
            (let [property-kwd (keyword property)
                  {:keys [title type enum items minimum]} (get-in library-schema [:properties property-kwd])]
              [:div {}
               (when enumerate
                 (str (inc index) ". "))
               title
               (cond enum
                     (if (< (count enum) 4)
                       [:div {:style {:margin "0.75em 0 0.75em 1em"}}
                        (map (fn [enum-val]
                               [:label {:style {:display "inline-flex" :alignItems "center" :cursor "pointer" :marginRight "2em"}}
                                [:input (merge
                                          {:type "radio" :style {:cursor "pointer"}
                                           :onClick #(swap! state update :attributes assoc property-kwd enum-val)}
                                          (when (= enum-val (get (:attributes @state) property-kwd)) {:checked true}))]
                                [:div {:style {:padding "0 0.4em" :fontWeight "500"}} enum-val]])
                             enum)]
                       (style/create-identity-select {:ref property
                                                      :value (get (:attributes @state) property-kwd ENUM_EMPTY_CHOICE)
                                                      :onChange #(swap! state update :attributes assoc property-kwd (.. % -target -value))}
                                                     (cons ENUM_EMPTY_CHOICE enum)))

                     (= type "text")
                     (style/create-text-area {:style {:width "100%"}
                                              :value (get (:attributes @state) property-kwd)
                                              :onChange #(swap! state update :attributes assoc property-kwd (.. % -target -value))
                                              :rows 3})

                     :else
                     [input/TextField {:ref property
                                       :style {:width "100%"}
                                       :placeholder inputHint
                                       :value (render-value (get (:attributes @state) property-kwd))
                                       :onChange #(swap! state update :attributes assoc property-kwd (.. % -target -value))
                                       :predicates [(when required
                                                      (input/nonempty property))
                                                    (when (= type "integer")
                                                      (input/integer property :min minimum))]}])]))
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
                  [:input (merge
                            {:type "radio" :style {:cursor "pointer"}
                             :onClick #(swap! state assoc :selected-index index)}
                            (when selected {:checked true}))]
                  [:div {:style {:padding "1em"}} title]]
                 (when selected
                   [:div {:style {:marginBottom "1.5em"}}
                    [Questions (merge
                                 (select-keys props [:library-schema :attributes])
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
       (cond questions [Questions (merge
                                    (select-keys props [:library-schema :attributes])
                                    {:ref "subcomponent" :enumerate enumerate :questions questions})]
             switch [Options (merge
                               (select-keys props [:library-schema :attributes])
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
  {:get-attributes
   (fn [{:keys [state]}]
     (:attributes @state))
   :get-initial-state
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
          [:div {:style {:flex "0 0 600px" :maxHeight 400 :padding "1em" :overflow "auto" :border style/standard-line :boxSizing "border-box"}}
           [WizardPage {:key (:page @state)
                        :ref "wizard-page"
                        :library-schema library-schema
                        :page-num (:page @state)
                        :attributes (:attributes @state)}]]]
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
                         :onClick #(swap! state update :page dec)
                         :style {:width 80 :marginRight 27}
                         :disabled? (zero? (:page @state))}]
          [comps/Button {:text (if (< (:page @state) (-> library-schema :wizard count dec)) "Next" "Submit")
                         :onClick #(react/call :next-page this)
                         :style {:width 80}}]]]]))
   :next-page
   (fn [{:keys [props state refs]}]
     (if-let [error-message (react/call :validate (@refs "wizard-page"))]
       (utils/log error-message)
       (do (swap! state update :attributes merge (react/call :get-attributes (@refs "wizard-page")))
           (if (< (:page @state) (-> props :library-schema :wizard count dec))
             (swap! state update :page inc)
             (utils/log "TODO: submit")))))})


(react/defc CatalogButton
  {:render
   (fn [{:keys [props]}]
     [comps/SidebarButton
      {:style :light :color :button-primary :margin :top
       :icon :catalog :text "Catalog Dataset..."
       :onClick #(modal/push-modal [CatalogWizard props])}])})
