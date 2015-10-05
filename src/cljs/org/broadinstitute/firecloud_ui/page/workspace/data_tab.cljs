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


(react/defc EntitiesList
  {:get-initial-state
   (fn [{:keys [props]}]
     {:entities (get (:entity-map props) (first (keys (:entity-map props))))})
   :render
   (fn [{:keys [props state refs]}]
     [:div {}
      [:div {:style {:padding "0 0 0.5em 1em"}}
       (style/create-form-label "Select Entity Type")
       (style/create-select
         {:style {:width "50%" :minWidth 50 :maxWidth 200} :ref "filter"
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

; TODO: Need a much better way to get back to the original state. This tri-state process is hacky and ugly.
(defn- get-back-link [state refs]
  [:div {:style {:margin "1em 0 0 1em"}}
   (style/create-link
     #(swap! state merge
       {:show-import? false :show-copy? false}
       (when (react/call :did-load-data? (@refs "data-import"))
         {:entity-map false}))
     (icons/font-icon {:style {:fontSize "70%" :marginRight "1em"}} :angle-left)
     "Back to Data List")])

(react/defc WorkspaceData
  {:render
   (fn [{:keys [props state refs]}]
     [:div {:style {:marginTop "1em"}}
      (cond
        (:show-import? @state)
        [:div {}
         (get-back-link state refs)
         [import-data/Page {:ref "data-import" :workspace-id (:workspace-id props)}]]
        (:show-copy? @state)
        [:div {}
         (get-back-link state refs)
         [copy-data-workspaces/Page {:ref "data-import" :workspace-id (:workspace-id props)}]]
        (:entity-map @state) [EntitiesList {:entity-map (:entity-map @state)}]
        (:error @state) (style/create-server-error-message (:error @state))
        :else [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading entities..."}]])
      (when-not (or (:show-import? @state) (:show-copy? @state))
        [:div {:style {:margin "1em 0 0 1em"}}
         [comps/Button {:text "Import Data..."
                        :onClick #(swap! state assoc :show-import? true :show-copy? false)}]
         [:span {:style {:margin ".5em"}} " "]
         [comps/Button {:text "Copy Data..."
                        :onClick #(swap! state assoc :show-import? false :show-copy? true)}]])])
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-entities this))
   :component-did-update
   (fn [{:keys [this state]}]
     (when-not (or (:entity-map @state) (:error @state))
       (react/call :load-entities this)))
   :load-entities
   (fn [{:keys [state props]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-entities-by-type (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :entity-map (group-by #(% "entityType") (get-parsed-response)))
                     (swap! state assoc :error status-text)))}))})

(defn render [workspace]
  [WorkspaceData {:workspace-id workspace}])
