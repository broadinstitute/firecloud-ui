(ns org.broadinstitute.firecloud-ui.page.workspace.data.copy-data-entities
  (:require
    [clojure.set :refer [union]]
    [clojure.string]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.workspace.data.entity-selector :refer [EntitySelector]]
    ))


(react/defc EntitiesList
  {:render
   (fn [{:keys [props state this refs]}]
     (let [from-ws (:selected-from-workspace props)]
       [:div {:style {:margin "1em"}}
        (when (:copying? @state)
          [comps/Blocker {:banner "Copying..."}])
        [EntitySelector {:ref "EntitySelector"
                         :left-text (str "Entities in " (get-in from-ws ["workspace" "namespace"])
                                      "/" (get-in from-ws ["workspace" "name"]))
                         :right-text "To be imported"
                         :entities (:entity-list props)}]
        [:div {:style {:textAlign "center"}}
         (when (:selection-error @state)
           [:div {:style {:marginTop "0.5em"}}
            "Please select at least one entity to copy"])
         (when (:server-error @state)
           [:div {:style {:marginTop "0.5em"}}
            [comps/ErrorViewer {:error (:server-error @state)}]])
         [:div {:style {:marginTop "1em"}}
          [comps/Button {:text "Copy"
                         :onClick #(let [selected (react/call :get-selected-entities (@refs "EntitySelector"))]
                                     (if (empty? selected)
                                       (swap! state assoc :selection-error true)
                                       (react/call :perform-copy this selected)))}]]]]))
   :perform-copy
   (fn [{:keys [props state]} selected]
     (swap! state dissoc :selection-error :server-error)
     (let [grouped-entities (group-by #(% "entityType") selected)]
       (swap! state assoc :copying? true :remaining (count grouped-entities))
       (doseq
         [[type list] grouped-entities]
         (endpoints/call-ajax-orch
           {:endpoint (endpoints/copy-entity-to-workspace (:workspace-id props))
            :payload {:sourceWorkspace {:namespace (get-in props [:selected-from-workspace "workspace" "namespace"])
                                        :name (get-in props [:selected-from-workspace "workspace" "name"])}
                      :entityType type
                      :entityNames (map #(% "name") list)}
            :headers {"Content-Type" "application/json"}
            :on-done (fn [{:keys [success? get-parsed-response]}]
                       (swap! state update-in [:remaining] dec)
                       (when (zero? (:remaining @state))
                         (swap! state assoc :copying? false)
                         ((:reload-data-tab props) type))
                       (when-not success?
                         (swap! state assoc :server-error (get-parsed-response))))}))))})


(defn- get-namespace-and-name [props]
  (map #(get-in props [:selected-from-workspace "workspace" %]) ["namespace" "name"]))

(react/defc Page
  {:render
   (fn [{:keys [state props]}]
     (cond
       (:entity-list @state) [EntitiesList {:workspace-id (:workspace-id props)
                                            :selected-from-workspace (:selected-from-workspace props)
                                            :entity-list (:entity-list @state)
                                            :entity-types (:entity-types @state)
                                            :reload-data-tab (:reload-data-tab props)}]
       (:error-message @state) (style/create-server-error-message (:error-message @state))
       :else [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading entities..."}]]))
   :component-did-mount
   (fn [{:keys [props this]}]
     (react/call :load-entities this))
   :load-entities
   (fn [{:keys [state props]}]
     (let [[namespace name] (get-namespace-and-name props)]
       (endpoints/call-ajax-orch
         {:endpoint (endpoints/get-entities-by-type {:name name :namespace namespace})
          :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                     (if success?
                       (let [entities (get-parsed-response)]
                         (swap! state assoc
                           :entity-list entities
                           :entity-types (distinct (map #(% "entityType") entities))))
                       (swap! state assoc :error status-text)))})))})
