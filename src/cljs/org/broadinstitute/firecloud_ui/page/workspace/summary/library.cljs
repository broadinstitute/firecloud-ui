(ns org.broadinstitute.firecloud-ui.page.workspace.summary.library
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.input :as input]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.utils :as utils]))


(defn- calculate-display-properties [library-schema]
  (->> (:properties library-schema)
       keys
       (utils/sort-match (:propertyOrder library-schema))
       (filter (comp not :hidden #(get-in library-schema [:properties %])))))

(defn- count-required-not-hidden [library-schema]
  (->> (:required library-schema)
       (filter (comp not :hidden #(get-in library-schema [:properties %])))
       count))

(defn- split-fold [library-schema display-properties]
  (let [required-count (count-required-not-hidden library-schema)
        above-fold-count (if (< required-count 5) required-count 5)]
    (split-at above-fold-count display-properties)))

(defn- render-value [value]
  (cond (sequential? value) (clojure.string/join ", " value)
        :else value))

(defn- render-property [library-schema library-attributes property-key]
  [:div {:style {:display "flex" :padding "0.5em 0" :borderBottom (str "2px solid " (:line-gray style/colors))}}
   [:div {:style {:flexBasis "33%" :fontWeight "bold" :paddingRight "2em"}}
    (get-in library-schema [:properties property-key :title])]
   [:div {:style {:flexBasis "67%"}}
    (render-value (get library-attributes property-key))]])

(react/defc LibraryAttributeViewer
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [library-attributes library-schema]} props
           display-properties (calculate-display-properties library-schema)
           [above-fold below-fold] (split-fold library-schema display-properties)
           below-fold (not-empty below-fold)]
       [:div {}
        (style/create-section-header "Dataset Attributes")
        (style/create-paragraph
          (if library-attributes
            [:div {}
             (map (partial render-property library-schema library-attributes) above-fold)
             (when below-fold
               [:div {}
                (when (:expanded? @state)
                  (map (partial render-property library-schema library-attributes) below-fold))
                [:div {:style {:marginTop "0.5em"}}
                 (style/create-link {:text (if (:expanded? @state) "Collapse" "See more attributes")
                                     :onClick #(swap! state update :expanded? not)})]])]
            [:div {:style {:fontStyle "italic"}} "No attributes provided"]))]))})


(defn- resolve-hidden [property-key workspace]
  (case property-key
    :workspaceId (get-in workspace [:workspace :workspaceId])
    :workspaceNamespace (get-in workspace [:workspace :namespace])
    :workspaceName (get-in workspace [:workspace :name])
    nil))

(react/defc LibraryAttributeForm
  {:get-initial-state
   (fn [{:keys [props]}]
     {:required-props (-> props :library-schema :required set)})
   :render
   (fn [{:keys [this]}]
     [modal/OKCancelForm
      {:header "Define Dataset Attributes"
       :content (react/create-element (react/call :form this))
       :ok-button #(react/call :on-ok this)}])
   :form
   (fn [{:keys [props state]}]
     (let [library-schema (:library-schema props)]
       [:div {:style {:width "50vw"}}
        [:div {:style {:maxHeight 400 :overflowY "auto"
                       :padding "0.5em" :border style/standard-line
                       :marginBottom "1em"}}
         (map (fn [property-key]
                (let [{:keys [hidden title inputHint enum]} (get-in library-schema [:properties property-key])
                      required? (contains? (:required-props @state) property-key)
                      pk-str (name property-key)]
                  (when-not hidden
                    [:div {}
                     (style/create-form-label (str title (when required? " (required)")))
                     (cond enum (style/create-identity-select {:ref pk-str} enum)
                           required? [input/TextField {:ref pk-str
                                                       :style {:width "100%"}
                                                       :placeholder inputHint
                                                       :predicates [(when required? (input/nonempty pk-str))]}]
                           :else (style/create-text-field {:ref (name property-key)
                                                           :style {:width "100%"}
                                                           :placeholder inputHint}))])))
              (calculate-display-properties library-schema))]
        (style/create-validation-error-message (:validation-error @state))]))
   :on-ok
   (fn [{:keys [props state refs]}]
     (let [field-data (doall
                        (map (fn [[property-key {:keys [hidden enum]}]]
                               (let [pk-str (name property-key)
                                     required? (contains? (:required-props @state) property-key)]
                                 [property-key
                                  {:required? required?
                                   :value (not-empty
                                            (cond hidden (resolve-hidden property-key (:workspace props))
                                                  enum (common/get-text refs pk-str)
                                                  required? (do (input/validate refs pk-str)
                                                              (input/get-text refs pk-str))
                                                  :else (common/get-text refs pk-str)))}]))
                             (-> props :library-schema :properties)))
           missing-fields (not-empty
                            (keep (fn [[property-key {:keys [required? value]}]]
                                    (when (and required? (not value)) property-key))
                                  field-data))]
       (if missing-fields
         (swap! state assoc :validation-error ["Please fill out all required fields"])
         (let [field-data-map (->> field-data
                                   (keep (fn [[property-key {:keys [value]}]]
                                           (when value
                                             [(name property-key) value])))
                                   (into {}))]
           (swap! state dissoc :validation-error)
           ;; TODO: update properties
           (utils/cljslog field-data-map)))))})


(react/defc CatalogButton
  {:render
   (fn [{:keys [props]}]
     [comps/SidebarButton
      {:style :light :color :button-blue :margin :top
       :icon :catalog :text "Catalog Dataset"
       :onClick #(modal/push-modal
                  [LibraryAttributeForm props])}])})


(react/defc PublishButton
  {:render
   (fn [{:keys [props]}]
     [comps/SidebarButton
      {:style :light :color :button-blue :margin :top
       :icon :library :text "Publish in Library"
       :disabled? (or (:disabled? props)
                    "Publish not available yet" ;; TODO remove
                    )
       :onClick #(utils/log "todo: publish in library")}])})
