(ns org.broadinstitute.firecloud-ui.page.workspace.data-tab
  (:require
    [dmohs.react :as react]
    [clojure.set :refer [union]]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.workspace.copy-data-workspaces :as copy-data-workspaces]
    [org.broadinstitute.firecloud-ui.page.workspace.import-data :as import-data]
    ))


(react/defc DataImporter
  {:render
   (fn [{:keys [state props]}]
     (let [choice? (or (:importing-from-file @state) (:copying-from-workspace @state))]
       [:div {}
        [:div {:style {:position "absolute" :top 2 :right 2}}
         [comps/Button {:icon :x :onClick #((:dismiss props))}]]
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
  {:get-initial-state
   (fn [{:keys [props]}]
     {:entities (get (:entity-map props)
                  (or (:initial-entity-type props)
                      (first (keys (:entity-map props)))))})
   :render
   (fn [{:keys [props state refs]}]
     [:div {}
      [:div {:style {:padding "0 0 0.5em 1em"}}
       (style/create-form-label "Select Entity Type")
       (style/create-select
         {:style {:width "50%" :minWidth 50 :maxWidth 200} :ref "filter"
          :defaultValue (:initial-entity-type props)
          :onChange #(let [value (common/get-text refs "filter")
                           entities (get-in props [:entity-map value])]
                      (swap! state assoc :entities entities :entity-type value))}
         (keys (:entity-map props)))]
      (let [attribute-keys (apply union (map (fn [e] (set (keys (e "attributes")))) (:entities @state)))]
        [table/Table
         {:key (:entity-type @state)
          :empty-message "There are no entities to display."
          :columns (concat
                     [{:header "Entity Type" :starting-width 100}
                      {:header "Entity Name" :starting-width 100}]
                     (map (fn [k] {:header k :starting-width 100
                                   :content-renderer
                                   (fn [maybe-uri]
                                     (if (string? maybe-uri)
                                       (if-let [converted (common/gcs-uri->download-url maybe-uri)]
                                         [:a {:href converted} maybe-uri]
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
                                                           (swap! state dissoc :entity-map)
                                                           (react/call :load-entities this entity-type))}])}])
      (cond
        (:entity-map @state)
        [:div {}
         [:div {:style {:float "right" :paddingRight "2em"}}
          [comps/Button {:text "Import Data..."
                         :onClick #(swap! state assoc :show-import? true)}]]
         [EntitiesList {:entity-map (:entity-map @state)
                        :workspace-id (:workspace-id props)
                        :initial-entity-type (:initial-entity-type @state)}]]
        (:error @state) (style/create-server-error-message (:error @state))
        :else [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading entities..."}]])])
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-entities this))
   :load-entities
   (fn [{:keys [state props]} & [entity-type]]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-entities-by-type (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc
                       :entity-map (group-by #(% "entityType") (get-parsed-response))
                       :initial-entity-type entity-type)
                     (swap! state assoc :error status-text)))}))})

(defn render [workspace]
  [WorkspaceData {:workspace-id workspace}])
