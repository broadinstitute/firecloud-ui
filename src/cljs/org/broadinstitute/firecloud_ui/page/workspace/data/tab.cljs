(ns org.broadinstitute.firecloud-ui.page.workspace.data.tab
  (:require
    [dmohs.react :as react]
    [clojure.set :refer [union]]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.workspace.data.copy-data-workspaces :as copy-data-workspaces]
    [org.broadinstitute.firecloud-ui.page.workspace.data.import-data :as import-data]
    ))


(react/defc DataImporter
  {:render
   (fn [{:keys [state props]}]
     (let [choice? (or (:importing-from-file @state) (:copying-from-workspace @state))]
       [:div {}
        [comps/XButton {:dismiss (:dismiss props)}]
        (when choice?
          [:div {:style {:padding "0.5em"}}
           (style/create-link
             #(swap! state dissoc :importing-from-file :copying-from-workspace)
             (icons/font-icon {:style {:fontSize "70%" :marginRight "1em"}} :angle-left)
             "Back")])
        (when (:importing-from-file @state)
          [:div {:style {:padding "1em"}}
           [import-data/Page (select-keys props [:workspace-id :reload-data-tab])]])
        (when (:copying-from-workspace @state)
          [:div {:style {:padding "1em"}}
           [copy-data-workspaces/Page (select-keys props [:workspace-id :reload-data-tab])]])
        (when-not choice?
          (let [style {:width 240 :margin "auto" :textAlign "center" :cursor "pointer"
                       :backgroundColor (:button-blue style/colors)
                       :color "#fff" :padding "1em" :borderRadius 8}]
            [:div {:style {:padding "2em"}}
             [:div {:onClick #(swap! state assoc :importing-from-file true) :style style}
              "Import from file"]
             [:div {:style {:height "1em"}}]
             [:div {:onClick #(swap! state assoc :copying-from-workspace true)
                    :style style}
              "Copy from another workspace"]]))]))
   :component-did-mount
   (fn []
     (common/scroll-to-top 100))})


(react/defc EntitiesList
  {:render
   (fn [{:keys [props state]}]
     [:div {}
      [:div {:style {:padding "0 0 0.5em 1em"}}
       [:div {:style {:textAlign "center"}}
        [comps/FilterBar {:data (:entity-list props)
                          :buttons (mapv (fn [key] {:text key
                                                    :filter #(= key (% "entityType"))})
                                     (if-let [type (:initial-entity-type props)]
                                       (cons type (filter #(not= % type) (:entity-types props)))
                                       (:entity-types props)))
                          :did-filter (fn [data info]
                                        (swap! state assoc :entities data :entity-type (:text info)))}]]]
      (let [attribute-keys (apply union (map #(set (keys (% "attributes"))) (:entities @state)))]
        [table/Table
         {:key (:entity-type @state)
          :empty-message "There are no entities to display."
          :toolbar (fn [built-in]
                     [:div {}
                      [:div {:style {:float "left"}} built-in]
                      (when-let [selected-entity-type (:entity-type @state)]
                        [:a {:style {:textDecoration "none" :float "left" :margin "5px 0 0 1em"}
                             :href (str "/service/api/workspaces/" (:namespace (:workspace-id props)) "/"
                                     (:name (:workspace-id props)) "/" selected-entity-type "/tsv")
                             :target "_blank"}
                         (str "Download '" selected-entity-type "' data")])
                      [:div {:style {:float "right" :paddingRight "2em"}}
                       [comps/Button {:text "Import Data..."
                                      :disabled? (if (:locked? @state) "This workspace is locked")
                                      :onClick (:show-import props)}]]
                      (common/clear-both)])
          :columns (concat
                     [{:header "Entity Type" :starting-width 100}
                      {:header "Entity Name" :starting-width 100}]
                     (map (fn [k] {:header k :starting-width 100
                                   :content-renderer
                                   (fn [maybe-uri]
                                     (if (string? maybe-uri)
                                       (if-let [parsed (common/parse-gcs-uri maybe-uri)]
                                         [comps/GCSFilePreviewLink (assoc parsed
                                                                     :gcs-uri maybe-uri)]
                                         maybe-uri)
                                       maybe-uri))})
                       attribute-keys))
          :data (map (fn [m]
                       (concat
                         [(m "entityType")
                          (m "name")]
                         (map (fn [k] (get-in m ["attributes" k])) attribute-keys)))
                  (:entities @state))}])])})


(react/defc WorkspaceData
  {:render
   (fn [{:keys [props state this]}]
     [:div {:style {:marginTop "1em"}}
      (when (:show-import? @state)
        [comps/Dialog {:dismiss-self #(swap! state dissoc :show-import?)
                       :width "80%"
                       :content
                       (react/create-element
                         [DataImporter {:dismiss #(swap! state dissoc :show-import?)
                                        :workspace-id (:workspace-id props)
                                        :reload-data-tab (fn [entity-type]
                                                           (swap! state dissoc :entity-list :entity-types)
                                                           (react/call :load this entity-type))}])}])
      (cond
        (and (:entity-list @state) (contains? @state :locked?))
        [EntitiesList {:entity-list (:entity-list @state)
                       :entity-types (:entity-types @state)
                       :workspace-id (:workspace-id props)
                       :initial-entity-type (:initial-entity-type @state)
                       :show-import #(swap! state assoc :show-import? true)}]
        (:error @state) (style/create-server-error-message (:error @state))
        :else [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading entities..."}]])])
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load this))
   :load
   (fn [{:keys [state props]} & [entity-type]]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :locked? (get-in (get-parsed-response) ["workspace" "isLocked"]))
                     (swap! state assoc :error status-text)))})
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-entities-by-type (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (let [entities (get-parsed-response)]
                       (swap! state assoc
                         :entity-list entities
                         :entity-types (distinct (map #(% "entityType") entities))
                         :initial-entity-type entity-type))
                     (swap! state assoc :error status-text)))}))})

(defn render [workspace]
  [WorkspaceData {:workspace-id workspace}])
