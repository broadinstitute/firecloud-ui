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
    ))

(defn- entity-listing [state props this from-ws]
  [:div {}
   [:h3 {}
    "Select entities to copy from "
    (get-in from-ws ["workspace" "namespace"])
    "/"
    (get-in from-ws ["workspace" "name"])
    [:span {:style {:marginLeft "1.5em"}}
     (style/create-link
       #((:back props))
       (icons/font-icon {:style {:fontSize "70%" :marginRight "0.5em"}} :angle-left)
       "Choose a different workspace")]]
   [:div {:style {:padding "0 0 0.5em 1em"}}
    [:div {:style {:textAlign "center"}}
     [comps/FilterBar {:data (:entity-list props)
                       :buttons (mapv (fn [key] {:text key
                                                 :filter #(= key (% "entityType"))})
                                  (:entity-types props))
                       :did-filter (fn [data info]
                                     (swap! state assoc :entities data :entity-type (:text info)))}]]]
   (let [attribute-keys (apply union (map #(set (keys (% "attributes"))) (:entities @state)))]
     [table/Table
      {:empty-message "There are no entities to display."
       :toolbar (fn [built-in]
                  [:div {}
                   [:div {:style {:float "left"}} built-in]
                   (when (pos? (count (:selected-entities @state)))
                     [:div {:style {:float "right"}}
                      [comps/Button {:text "Copy" :onClick #(react/call :perform-copy this)}]])
                   (common/clear-both)])
       :columns (concat
                  [{:starting-width 40 :resizable? false :sort-by :none :filter-by :none
                    :content-renderer
                    (fn [entity]
                      (when (contains? (:selected-entities @state) entity)
                        (icons/font-icon {:style {:color (:success-green style/colors)}} :status-done)))}
                   {:header "Entity Type" :starting-width 100}
                   {:header "Entity Name" :starting-width 100
                    :content-renderer
                    (fn [entity]
                      (style/create-link
                        #(swap! state update-in [:selected-entities]
                          (if (contains? (:selected-entities @state) entity) disj conj) entity)
                        (entity "name")))}]
                  (map (fn [k] {:header k :starting-width 100}) attribute-keys))
       :data (:entities @state)
       :->row (fn [m]
                (concat
                 [m
                  (m "entityType")
                  m]
                 (map (fn [k] (get-in m ["attributes" k])) attribute-keys)))}])])

(react/defc EntitiesList
  {:get-initial-state
   (fn []
     {:selected-entities #{}})
   :render
   (fn [{:keys [props state this]}]
     (let [from-ws (:selected-from-workspace props)]
       [:div {:style {:margin "1em"}}
        (when (:copying? @state)
          [comps/Blocker {:banner "Copying..."}])
        (entity-listing state props this from-ws)]))
   :perform-copy
   (fn [{:keys [props state]}]
     (let [grouped-entities (group-by #(% "entityType") (:selected-entities @state))]
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
            :on-done (fn [{:keys [success? status-text]}]
                       (swap! state update-in [:remaining] dec)
                       (when (zero? (:remaining @state))
                         (swap! state assoc :copying? false :selected-entities #{})
                         ((:reload-data-tab props) type))
                       (when-not success?
                         (swap! state assoc :copy-error status-text)))}))))})


(react/defc Page
  {:render
   (fn [{:keys [state props]}]
     (cond
       (:entity-list @state) [EntitiesList {:workspace-id (:workspace-id props)
                                            :selected-from-workspace (:selected-from-workspace props)
                                            :entity-list (:entity-list @state)
                                            :entity-types (:entity-types @state)
                                            :reload-data-tab (:reload-data-tab props)
                                            :back (:back props)}]
       (:error-message @state) (style/create-server-error-message (:error-message @state))
       :else [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading entities..."}]]))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-entities this))
   :load-entities
   (fn [{:keys [state props]}]
     (let [name (get-in props [:selected-from-workspace "workspace" "name"])
           namespace (get-in props [:selected-from-workspace"workspace" "namespace"])]
       (endpoints/call-ajax-orch
         {:endpoint (endpoints/get-entities-by-type {:name name :namespace namespace})
          :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                     (if success?
                       (let [entities (get-parsed-response)]
                         (swap! state assoc
                           :entity-list entities
                           :entity-types (distinct (map #(% "entityType") entities))))
                       (swap! state assoc :error status-text)))})))})
