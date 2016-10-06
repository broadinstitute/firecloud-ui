(ns org.broadinstitute.firecloud-ui.page.workspace.summary.library
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


(defn- calculate-display-properties [library-schema]
  (->> (:properties library-schema)
       keys
       (utils/sort-match (:propertyOrder library-schema))
       (filter (comp not :hidden #(get-in library-schema [:properties %])))))


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

           ;; Shim while saving library attributes isn't working:
           library-attributes
           {:datasetName "Test"
            :datasetDescription "Description"
            :indication "Test indication"
            :numSubjects 50}

           primary-properties [:indication :numSubjects :datatype :dataUseRestriction] ;; TODO replace with new field from schema
           secondary-properties (->> (calculate-display-properties library-schema)
                                     (remove (partial contains? (set primary-properties))))]
       [:div {}
        (style/create-section-header "Dataset Attributes")
        (style/create-paragraph
          (if library-attributes
            [:div {}
             (map (partial render-property library-schema library-attributes) primary-properties)
             (when secondary-properties
               [:div {}
                (when (:expanded? @state)
                  (map (partial render-property library-schema library-attributes) secondary-properties))
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

(def ^:private ENUM_EMPTY_CHOICE "<select an option>")

(defn resolve-enum [value]
  (when-not (= value ENUM_EMPTY_CHOICE)
    value))

(defn- resolve-field [value {:keys [type items]}]
  (case type
    "string" (not-empty value)
    "integer" (int value)
    "array" (not-empty
              (let [parsed (clojure.string/split value #"\s*,\s*")]
                (case (:type items)
                  "string" (remove empty? parsed)
                  "integer" (map int parsed)
                  (do (utils/log "unknown array type: " (:type items))
                      parsed))))
    (do (utils/log "unknown type: " type)
        value)))

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
                (let [{:keys [hidden title inputHint enum type items minimum default]} (get-in library-schema [:properties property-key])
                      required? (contains? (:required-props @state) property-key)
                      pk-str (name property-key)]
                  (when-not hidden
                    [:div {}
                     (style/create-form-label
                       [:div {:style {:display "flex" :alignItems "baseline"}}
                        [:div {:style {:flex "0 0 auto"}}
                         title
                         (when required?
                           [:b {:style {:marginLeft "1ex"}} "(required)"])]
                        [:div {:style {:flex "1 1 auto"}}]
                        (when-not enum
                          [:i {:style {:flex "0 0 auto"}}
                           (if (= type "array")
                             (str "Array of " (:type items) "s")
                             (clojure.string/capitalize type))])])
                     (if enum
                       (style/create-identity-select {:ref pk-str} (cons ENUM_EMPTY_CHOICE enum))
                       [input/TextField {:ref pk-str
                                         :style {:width "100%"}
                                         :placeholder inputHint
                                         :defaultValue default
                                         :predicates [(when required?
                                                        (input/nonempty pk-str))
                                                      (when (= type "integer")
                                                        (input/integer pk-str :min minimum))]}])])))
              (calculate-display-properties library-schema))]
        (style/create-validation-error-message (:validation-error @state))
        [comps/ErrorViewer {:error (:server-error @state)}]]))
   :on-ok
   (fn [{:keys [props state refs]}]
     (swap! state dissoc :validation-error :server-error)
     (let [validation-errors (atom [])
           field-data (->> props :library-schema :properties
                           (keep (fn [[property-key {:keys [hidden enum] :as property}]]
                                   (let [pk-str (name property-key)
                                         value (cond hidden (resolve-hidden property-key (:workspace props))
                                                     enum (resolve-enum (common/get-text refs pk-str))
                                                     :else (do (swap! validation-errors into (input/validate refs pk-str))
                                                               (resolve-field (input/get-text refs pk-str) property)))]
                                     (when value
                                       [(str "library:" pk-str) value]))))
                           (into {}))]
       (if-not (empty? @validation-errors)
         (swap! state assoc :validation-error @validation-errors)
         (do (swap! state assoc :saving? true)
             (utils/cljslog field-data)
             (endpoints/call-ajax-orch
               {:endpoint (endpoints/save-library-metadata (:workspace-id props))
                :payload field-data
                :on-done (fn [{:keys [success? get-parsed-response]}]
                           (swap! state dissoc :saving?)
                           (if success?
                             (do (modal/pop-modal)
                                 ((:request-refresh props)))
                             (swap! state assoc :server-error (get-parsed-response))))})))))})


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
