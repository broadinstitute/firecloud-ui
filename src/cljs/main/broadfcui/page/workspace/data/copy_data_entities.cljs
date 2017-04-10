(ns broadfcui.page.workspace.data.copy-data-entities
  (:require
    [clojure.set :refer [union]]
    [clojure.string :refer [capitalize]]
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.page.workspace.data.entity-selector :refer [EntitySelector]]
    [broadfcui.utils :as utils]
    ))


(react/defc EntitiesList
  {:render
   (fn [{:keys [props state this refs]}]
     (let [swid (:selected-workspace-id props)]
       [:div {:style {:margin "1em"}}
        (when (:copying? @state)
          [comps/Blocker {:banner "Copying..."}])
        [EntitySelector {:ref "EntitySelector"
                         :type (:type props)
                         :selected-workspace-bucket (:selected-workspace-bucket props)
                         :left-text (str (capitalize (:type props)) "s in " (:namespace swid) "/" (:name swid))
                         :right-text "To be imported"
                         :id-name (:id-name props)
                         :entities (:entity-list props)}]
        [:div {:style {:textAlign "center"}}
         (when (:selection-error @state)
           [:div {:style {:marginTop "0.5em"}}
            "Please select at least one entity to copy"])
         [:div {:style {:marginTop "1em"}}
          [comps/Button {:text "Import"
                         :onClick #(let [selected (react/call :get-selected-entities (@refs "EntitySelector"))]
                                     (if (empty? selected)
                                       (swap! state assoc :selection-error true)
                                       (react/call :perform-copy this selected)))}]]]]))
   :perform-copy
   (fn [{:keys [props state this]} selected re-link?]
     (swap! state assoc :selection-error nil :server-error nil :copying? true)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/copy-entity-to-workspace (:workspace-id props) re-link?)
       :payload {:sourceWorkspace (:selected-workspace-id props)
                 :entityType (:type props)
                 :entityNames (map #(% "name") selected)}
       :headers utils/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (swap! state dissoc :copying?)
                  (when success?
                    ((:on-data-imported props) (:type props)))
                  (react/call :show-import-result this (get-parsed-response false) selected))}))
   :show-import-result
   (fn [{:keys [this props]} parsed-response selected]
     (let [copied (parsed-response "entitiesCopied")
           hard-conflicts (parsed-response "hardConflicts")
           soft-conflicts (clojure.walk/postwalk
                           (fn [x]
                             (cond
                               (= (second x) []) nil
                               (= x "entityName") "ID"
                               (= (first x) "entityType") ["Type" (clojure.string/replace (second x) "_" " ")]
                               (= x "conflicts") "Conflicting linked entities"
                               :else x))
                           (parsed-response "softConflicts"))
           import-type (:type props)]
       (comps/push-ok-cancel-modal
        {:header (if (not-empty copied)
                   "Import Successful"
                   "Unable to Import")
         :content [:div {:style {:maxWidth 600}}
                   (when (not-empty copied)
                     [comps/Tree {:data copied}])
                   (when (not-empty hard-conflicts)
                     [:div {}
                      [:p {}
                       "The import could not be completed because the following " import-type
                       "s have the same IDs as entities already in this workspace."]
                      [comps/Tree {:highlight-ends? true
                                   :data hard-conflicts}]])
                   (when (not-empty soft-conflicts)
                     [:div {}
                      [:p {} "The import could not be completed because some of the " import-type
                       "s that you selected are connected to entities that already exist in the destination workspace."]
                      [:p {} "The conflicting entities are highlighted below."]
                      [comps/Tree {:highlight-ends? true
                                   :data soft-conflicts}]
                      [:p {} "You may connect the " import-type "s that you import to the existing
                      entities in the destination workspace, by clicking " [:strong {} "Re-link"] "."]
                      [:p {} "Re-linking will not import the conflicting entities from, or change the "
                       import-type "s in, the source workspace."
                       (common/render-info-box
                        {:position "top"
                         :text (react/create-element
                                [:span {}
                                 [:div {:style {:paddingBottom "1rem"}} "Let's say you have a \"Sample 1\" with " [:code {} "Participant 1"] " and " [:code {} "Gender"] " attributes already set in Workspace A, and you want to copy just the sample data over to Workspace B. Workspace B already has the " [:code {} "Participant 1"] " and " [:code {} "Gender"] " attributes set, so if you tried to copy over \"Sample 1\", Workspace B would tell you you have a conflict because " [:strong {} "copying"] " brings over all the attributes (" [:code {} "Participant 1"] " and " [:code {} "Gender"] ") with the sample data."]
                                 [:div {} "Instead, you'll want to click re-link. This copies over the \"Sample 1\" data into Workspace B " [:strong {} "without"] " bringing along any attributes that were tied to it in Workspace A. Be aware that \"Participant 1\" may not be the same participant in Workspace A & B or they could be the same but have different versions of attributes."]])})]])]
         :show-cancel? (not-empty soft-conflicts)
         :ok-button (if (not-empty soft-conflicts)
                      {:text "Re-link"
                       :onClick (fn []
                                  (modal/pop-modal)
                                  (react/call :perform-copy this selected true))}
                      "OK")})))})


(react/defc Page
  {:render
   (fn [{:keys [state props]}]
     (cond
       (:entity-list @state) [EntitiesList
                              (merge
                               (select-keys props [:workspace-id :selected-workspace-id :type
                                                   :selected-workspace-bucket :id-name
                                                   :on-data-imported])
                               (select-keys @state [:entity-list]))]
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
                     (swap! state assoc :entity-list (get-parsed-response false))
                     (swap! state assoc :server-error (get-parsed-response false))))}))})


(react/defc SelectType
  {:render
   (fn [{:keys [props state]}]
     (let [selected-type (:text (first (:crumbs props)))]
       (cond
         selected-type
         [Page (merge props {:type selected-type
                             :id-name (get-in (:entity-types @state) [selected-type "idName"])})]

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

         (:server-error @state)
         [comps/ErrorViewer {:error (:server-error @state)}]

         :else
         [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading entity types..."}]])))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-entity-types (:selected-workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response]}]
                   (swap! state assoc (if success? :entity-types :server-error) (get-parsed-response false)))}))})
