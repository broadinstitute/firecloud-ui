(ns org.broadinstitute.firecloud-ui.page.workspace.copy-data-entities
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

(defn- labeled-field [label item]
  [:div {}
   [:div {:style {:width "100px" :display "inline-block"}} label]
   item])

(defn- confirmation-page [state props this from-entity from-ws]
  [:div {}
   [:div {:style {:position "absolute" :right 2 :top 2}}
    [comps/Button {:icon :x :onClick #(swap! state dissoc :selected-from-entity)}]]
   [:div {:style {:padding "20px 48px 18px"}}
    [:h3 {} "Copy Entity:"
     [:span {:style {:marginLeft "1.5em"}}
      (style/create-link
        #(swap! state dissoc :selected-from-entity :copy-error)
        (icons/font-icon {:style {:fontSize "70%" :marginRight "0.5em"}} :angle-left)
        "Select another entity")]]
    (labeled-field "Entity Type: " (from-entity "entityType"))
    (labeled-field "Entity Name: " (from-entity "name"))
    [:div {:style {:paddingTop "1em"}}
     [:div {:style {:float "left"}}
      [:div {:style {:fontWeight "bold"}} "From:"]
      (labeled-field "Namespace: " (get-in from-ws ["workspace" "namespace"]))
      (labeled-field "Name: " (get-in from-ws ["workspace" "name"]))]
     [:div {:style {:float "left" :padding "0 2em"}}
      "\u27A1"]
     [:div {:style {:float "left"}}
      [:div {:style {:fontWeight "bold"}} "To:"]
      (labeled-field "Namespace: " (:namespace (:workspace-id props)))
      (labeled-field "Name: " (:name (:workspace-id props)))]
     (common/clear-both)]
    [:div {:style {:paddingTop "1em" :display "inline-block"}}
     [comps/Button {:text "Copy" :onClick #(react/call :perform-copy this)}]]
    (when (:copy-error @state)
      [:span {:style {:color (:exception-red style/colors)}}
       (icons/font-icon {:style {:margin "0 0.5em"}} :status-warning)
       "Error: " (:copy-error @state)])]])

(defn- entity-listing [state props refs from-ws]
  [:div {}
   [:h3 {}
    "Select an entity to copy from "
    (get-in from-ws ["workspace" "namespace"])
    ":"
    (get-in from-ws ["workspace" "name"])
    [:span {:style {:marginLeft "1.5em"}}
     (style/create-link
       #((:back props))
       (icons/font-icon {:style {:fontSize "70%" :marginRight "0.5em"}} :angle-left)
       "Choose a different workspace")]]
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
                   {:header "Entity Name" :starting-width 100
                    :content-renderer
                    (fn [entity]
                      (style/create-link
                        #(do
                          (common/scroll-to-top)
                          (swap! state assoc :selected-from-entity entity))
                        (entity "name")))}]
                  (map (fn [k] {:header k :starting-width 100}) attribute-keys))
       :data (map (fn [m]
                    (concat
                      [(m "entityType")
                       m]
                      (map (fn [k] (get-in m ["attributes" k])) attribute-keys)))
               (:entities @state))}])])

(react/defc EntitiesList
  {:get-initial-state
   (fn [{:keys [props]}]
     {:entities (get (:entity-map props) (first (keys (:entity-map props))))})
   :render
   (fn [{:keys [props state refs this]}]
     (let [from-entity (:selected-from-entity @state)
           from-ws (:selected-from-workspace props)]
       [:div {:style {:margin "1em"}}
        (when (:copying? @state)
          [comps/Blocker {:banner "Copying..."}])
        (if from-entity
          (confirmation-page state props this from-entity from-ws)
          (entity-listing state props refs from-ws))]))
   :perform-copy
   (fn [{:keys [props state]}]
     (swap! state assoc :copying? true)
     (let [from-entity (:selected-from-entity @state)
           entity-type (from-entity "entityType")]
       (endpoints/call-ajax-orch
         {:endpoint (endpoints/copy-entity-to-workspace (:workspace-id props))
          :payload {:sourceWorkspace {:namespace (get-in props [:selected-from-workspace "workspace" "namespace"])
                                      :name (get-in props [:selected-from-workspace "workspace" "name"])}
                    :entityType entity-type
                    :entityNames [(from-entity "name")]}
          :headers {"Content-Type" "application/json"}
          :on-done (fn [{:keys [success? status-text]}]
                     (swap! state dissoc :copying?)
                     (if success?
                       (do
                         ((:reload-data-tab props) entity-type)
                         (swap! state dissoc :selected-from-entity))
                       (swap! state assoc :copy-error status-text)))})))})


(react/defc Page
  {:did-load-data? ; TODO: Fix this hack. It is necessary for the previous caller to know how to get back to it's original state. Ugh.
   (fn [{:keys [state]}]
     (:entity-map @state))
   :render
   (fn [{:keys [state props]}]
     (cond
       (:entity-map @state) [EntitiesList {:workspace-id (:workspace-id props)
                                           :selected-from-workspace (:selected-from-workspace props)
                                           :entity-map (:entity-map @state)
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
                       (swap! state assoc :entity-map (group-by #(% "entityType") (get-parsed-response)))
                       (swap! state assoc :error status-text)))})))})
