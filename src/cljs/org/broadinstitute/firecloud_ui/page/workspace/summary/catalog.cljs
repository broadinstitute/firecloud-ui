(ns org.broadinstitute.firecloud-ui.page.workspace.summary.catalog
  (:require
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


(defn- render-questions-list [{:keys [library-schema questions enumerate attributes]}]
  [:div {}
   (map-indexed
    (fn [index {:keys [property required inputHint footerHint]}]
      (let [property-kwd (keyword property)
            {:keys [title type enum items default minimum]} (get-in library-schema [:properties property-kwd])]
        [:div {}
         (when enumerate
           (str (inc index) ". "))
         title
         (if enum
           (if (< (count enum) 4)
             [:div {:style {:margin "0.75em 0"}}
              (map (fn [enum-val]
                     [:label {:style {:cursor "pointer" :marginRight "1em"}}
                      [:input {:type "radio" :style {:cursor "pointer"}}
                       [:div {:style {:display "inline-block" :padding "0 1em" :width 60 :fontWeight "500"}} enum-val]]])
                   enum)]
             (style/create-identity-select {:ref property
                                            :defaultValue (get attributes property-kwd ENUM_EMPTY_CHOICE)}
                                           (cons ENUM_EMPTY_CHOICE enum)))
           [input/TextField {:ref property
                             :style {:width "100%"}
                             :placeholder inputHint
                             :defaultValue (render-value (get attributes property-kwd default))
                             :predicates [(when required
                                            (input/nonempty property))
                                          (when (= type "integer")
                                            (input/integer property :min minimum))]}])]))
    questions)])


(defn- render-wizard-page [{:keys [library-schema page-num attributes]}]
  (let [page (get-in library-schema [:wizard page-num])
        {:keys [questions enumerate switch]} page]
    (cond questions
          (render-questions-list {:library-schema library-schema :enumerate enumerate
                                  :questions questions :attributes attributes})
          switch
          [:div {}
           [:div {} (:title switch)]
           (interpose
             [:hr]
             (map (fn [{:keys [title enumerate questions]}]
                    [:div {}
                     [:div {} title]
                     (render-questions-list {:library-schema library-schema :enumerate enumerate
                                             :questions questions :attributes attributes})])
                  (:options switch)))])))


(defn- render-wizard-breadcrumbs [{:keys [library-schema page-num go-to-page]}]
  (let [pages (:wizard library-schema)]
    [:div {:style {:flex "0 0 auto"}}
     (map-indexed
       (fn [index {:keys [title]}]
         (let [prev (< index page-num)
               this (= index page-num)]
           [:div {:style {:margin "0.5em"
                          :cursor (when prev "pointer")
                          :color (when-not this (:text-lighter style/colors))}
                  :onClick (when prev #(go-to-page index))}
            [:span {:style {:margin "0 0.5em"}} "•"]
            [:span {:style {:fontWeight (when this "bold")}} title]
            ;; hidden div to reserve space for bold
            [:div {:style {:fontWeight "bold" :visibility "hidden" :height 0}}
             [:span {:style {:margin "0 0.5em"}} "•"]
             title]]))
       pages)]))


(react/defc CatalogWizard
  {:get-initial-state
   (fn [{:keys [props]}]
     {:page 1
      :attributes (get-initial-attributes (:workspace props))})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [library-schema]} props]
       [:div {}
        [:div {:style {:borderBottom style/standard-line
                       :padding "20px 48px 18px"
                       :fontSize "137%" :fontWeight 400 :lineHeight 1}}
         "Catalog Data Set"
         [comps/XButton {:dismiss modal/pop-modal}]]
        [:div {:style {:padding "22px 24px 40px" :backgroundColor (:background-light style/colors)}}
         [:div {:style {:display "flex"}}
          (render-wizard-breadcrumbs {:library-schema library-schema :page-num (:page @state)
                                      :go-to-page #(swap! state assoc :page %)})
          [:div {:style {:width 700 :maxHeight 400 :padding "1em" :overflow "auto" :border style/standard-line}}
           (render-wizard-page {:library-schema library-schema :page-num (:page @state) :attributes (:attributes @state)})]]
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
          [comps/Button {:text "Submit" :ref "ok-button" :class-name "ok-button" :onClick modal/pop-modal}]]]]))})


(react/defc CatalogButton
  {:render
   (fn [{:keys [props]}]
     [comps/SidebarButton
      {:style :light :color :button-primary :margin :top
       :icon :catalog :text "Catalog Dataset..."
       :onClick #(modal/push-modal [CatalogWizard props])}])})
