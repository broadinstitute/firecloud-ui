(ns org.broadinstitute.firecloud-ui.page.workspace.data.copy-data-entities
  (:require
    [clojure.set :refer [union]]
    [clojure.string]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.workspace.data.entity-selector :refer [EntitySelector]]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(react/defc EntitiesList
  {:render
   (fn [{:keys [props state this refs]}]
     (let [swid (:selected-workspace-id props)]
       [:div {:style {:margin "1em"}}
        (when (:copying? @state)
          [comps/Blocker {:banner "Copying..."}])
        [EntitySelector {:ref "EntitySelector"
                         :left-text (str "Entities in " (:namespace swid) "/" (:name swid))
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
     (swap! state assoc :selection-error nil :server-error nil :copying? true)
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/copy-entity-to-workspace (:workspace-id props))
        :payload {:sourceWorkspace (:selected-workspace-id props)
                  :entityType (:type props)
                  :entityNames (map #(% "name") selected)}
        :headers {"Content-Type" "application/json"}
        :on-done (fn [{:keys [success? get-parsed-response]}]
                   (swap! state dissoc :copying?)
                   (if success?
                     ((:reload-data-tab props) (:type props))
                     (swap! state assoc :server-error (get-parsed-response))))}))})


(react/defc Page
  {:render
   (fn [{:keys [state props]}]
     (cond
       (:entity-list @state) [EntitiesList {:workspace-id (:workspace-id props)
                                            :selected-workspace-id (:selected-workspace-id props)
                                            :entity-list (:entity-list @state)
                                            :type (:type props)
                                            :reload-data-tab (:reload-data-tab props)}]
       (:server-error @state) [comps/ErrorViewer {:error (:server-error @state)}]
       :else [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading entities..."}]]))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-entities this))
   :load-entities
   (fn [{:keys [state props]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-entities-of-type (:selected-workspace-id props) (:type props))
        :on-done (fn [{:keys [success? get-parsed-response]}]
                   (if success?
                     (swap! state assoc :entity-list (get-parsed-response))
                     (swap! state assoc :server-error (get-parsed-response))))}))})


(react/defc SelectType
  {:render
   (fn [{:keys [props state]}]
     (let [selected-type (:text (first (:crumbs props)))]
       (cond
         selected-type [Page (merge props {:type selected-type})]

         (:entity-types @state)
         [:div {:style {:textAlign "center"}}
          [:h3 {} "Choose type:"]
          [:div {}
           (map
             (fn [[type {:strs [count]}]]
               [:div {:style {:display "inline-block" :margin "0px 1em"}}
                [comps/Button {:text (str type " (" count ")")
                               :onClick #((:add-crumb props) {:text type})}]])
             (:entity-types @state))]]

         (:server-error @state) [comps/ErrorViewer {:error (:server-error @state)}]
         :else [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading entity types..."}]])))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-entity-types (:selected-workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response]}]
                   (swap! state assoc (if success? :entity-types :server-error) (get-parsed-response)))}))})
