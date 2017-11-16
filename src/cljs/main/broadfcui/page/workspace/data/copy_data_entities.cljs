(ns broadfcui.page.workspace.data.copy-data-entities
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [clojure.walk :as walk]
   [broadfcui.common.components :as comps]
   [broadfcui.common.modal :as modal]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.foundation-dropdown :as dropdown]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.workspace.data.entity-selector :refer [EntitySelector]]
   [broadfcui.utils :as utils]
   ))


(react/defc- EntitiesList
  {:render
   (fn [{:keys [props state this refs]}]
     (let [swid (:selected-workspace-id props)]
       [:div {:style {:margin "1em"}}
        (when (:copying? @state)
          [comps/Blocker {:banner "Copying..."}])
        [EntitySelector {:ref "EntitySelector"
                         :type (:type props)
                         :selected-workspace-bucket (:selected-workspace-bucket props)
                         :left-text (str (string/capitalize (:type props)) "s in " (:namespace swid) "/" (:name swid))
                         :right-text "To be imported"
                         :id-name (:id-name props)
                         :entities (:entity-list props)}]
        [:div {:style {:textAlign "center"}}
         (when (:selection-error @state)
           [:div {:style {:marginTop "0.5em"}}
            "Please select at least one entity to copy"])
         [:div {:style {:marginTop "1em"}}
          [buttons/Button {:text "Import"
                           :onClick #(let [selected ((@refs "EntitySelector") :get-selected-entities)]
                                       (if (empty? selected)
                                         (swap! state assoc :selection-error true)
                                         (this :perform-copy selected)))}]]]]))
   :perform-copy
   (fn [{:keys [props state this]} selected re-link?]
     (utils/multi-swap! state (assoc :copying? true) (dissoc :selection-error :server-error))
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/copy-entity-to-workspace (:workspace-id props) re-link?)
       :payload {:sourceWorkspace (:selected-workspace-id props)
                 :entityType (:type props)
                 :entityNames (map :name selected)}
       :headers utils/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (swap! state dissoc :copying?)
                  (when success?
                    ((:on-data-imported props) (:type props)))
                  (this :show-import-result (get-parsed-response false) selected))}))
   :show-import-result
   (fn [{:keys [this props]} parsed-response selected]
     (let [formatted-response (walk/postwalk
                               #(case %
                                  "entityName" "ID"
                                  "entityType" "Type"
                                  "conflicts" "Conflicting linked entities"
                                  %)
                               parsed-response)
           copied (formatted-response "entitiesCopied")
           hard-conflicts (formatted-response "hardConflicts")
           soft-conflicts (walk/postwalk
                           #(if (= (second %) []) nil %)
                           (formatted-response "softConflicts"))
           import-type (:type props)]
       (comps/push-ok-cancel-modal
        {:header (if (not-empty copied)
                   "Import Successful"
                   "Unable to Import")
         :content [:div {:style {:maxWidth 600}}
                   (when (not-empty copied)
                     [:div {}
                      [:p {} "The following " import-type "s were imported successfully."]
                      [comps/Tree {:data copied}]])
                   (when (not-empty hard-conflicts)
                     [:div {}
                      [:p {}
                       "The import did not complete because the following " import-type
                       "s have the same IDs as entities already in this workspace."]
                      [comps/Tree {:highlight-ends? true
                                   :data hard-conflicts}]])
                   (when (not-empty soft-conflicts)
                     [:div {}
                      [:p {} "The import did not complete because some of the " import-type
                       "s that you selected are linked to entities that already exist in the
                       destination workspace."]
                      [:p {} "The conflicting entities are highlighted below."]
                      [comps/Tree {:highlight-ends? true
                                   :data soft-conflicts}]
                      [:p {} "You may link the " import-type "s that you import to the existing
                      entities in the destination workspace, by clicking " [:strong {} "Re-link"] "."]
                      [:p {} "Re-linking will not import the conflicting entities from, or change the "
                       import-type "s in, the source workspace."
                       (dropdown/render-info-box
                        {:position "top"
                         :text [comps/ScrollFader
                                {:outer-style {:margin "-1rem"}
                                 :inner-style {:maxHeight 300 :padding "1rem"}
                                 :content [:div {}
                                           [:div {:style {:paddingBottom "1rem"}}
                                            "Every entity (participant, sample, etc.) has an ID. Multiple
                                            entities cannot share the same ID, they must be unique."]
                                           [:div {:style {:paddingBottom "1rem"}}
                                            "Entities can be linked by their ID. For example, a sample can be
                                             linked to a participant."]
                                           [:div {:style {:paddingBottom "1rem"}}
                                            "If you import a sample that is linked to a participant, that
                                            participant will also be imported. However, if the imported
                                            participant (\"source participant\") has the same ID as an existing
                                            participant in the workspace (\"destination participant\"), the
                                            import cannot finish. Since two entities cannot have the same ID,
                                            this would cause a conflict."]
                                           [:div {:style {:paddingBottom "1rem"}}
                                            "Re-linking allows the sample to link to the participant that was
                                            already in the workspace (\"destination participant\")."]
                                           [:div {:style {:paddingBottom "1rem"}}
                                            "Keep in mind that there may be differences between the \"source
                                            participant\" and the \"destination participant\", so you may want
                                            to confirm that they are actually the same."]
                                           [:div {} "All other attributes of the source will be imported normally."]]}]})]])]
         :show-cancel? (not-empty soft-conflicts)
         :ok-button (if (not-empty soft-conflicts)
                      {:text "Re-link"
                       :onClick (fn []
                                  (modal/pop-modal)
                                  (this :perform-copy selected true))}
                      "OK")})))})


(react/defc- Page
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
     (this :load-entities))
   :load-entities
   (fn [{:keys [state props]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-entities-of-type (:selected-workspace-id props) (:type props))
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (swap! state assoc :entity-list (get-parsed-response))
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
               [buttons/Button {:text (str type " (" count ")")
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
