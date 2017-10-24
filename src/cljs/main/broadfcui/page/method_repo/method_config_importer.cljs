(ns broadfcui.page.method-repo.method-config-importer
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.input :as input]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.entity-details :refer [EntityDetails]]
   [broadfcui.components.sticky :refer [Sticky]]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.workspace-selector :refer [WorkspaceSelector]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.page.method-repo.create-method :as create]
   [broadfcui.page.method-repo.method.common :as method-common]
   [broadfcui.page.method-repo.method-repo-table :refer [MethodRepoTable]]
   [broadfcui.page.method-repo.methods-configs-acl :as mca]
   [broadfcui.page.method-repo.synchronize :as mr-sync]
   [broadfcui.page.method-repo.redactor :refer [Redactor]]
   [broadfcui.utils :as utils]
   ))


(react/defc- Sidebar
  {:render
   (fn [{:keys [props state refs]}]
     (let [{:keys [entity config? workflow? on-delete owner? body-id]} props
           on-method-created (fn [_ id]
                               (nav/go-to-path :method id)
                               (common/scroll-to-top))]
       [:div {:style {:flex "0 0 270px" :paddingRight 30}}
        [mr-sync/SyncContainer {:ref "sync-container" :config entity}]
        (modals/show-modals
         state
         {:deleting?
          [Redactor (utils/restructure entity config? on-delete)]
          :editing-acl?
          [mca/AgoraPermsEditor
           {:save-endpoint (endpoints/persist-agora-entity-acl config? entity)
            :load-endpoint (endpoints/get-agora-entity-acl config? entity)
            :entityType (:entityType entity)
            :entityName (mca/get-ordered-name entity)
            :title (str (:entityType entity) " " (mca/get-ordered-name entity))
            :on-users-added #((@refs "sync-container") :check-synchronization %)}]
          :cloning?
          [create/CreateMethodDialog
           {:duplicate entity
            :on-created on-method-created}]
          :editing-method?
          [create/CreateMethodDialog
           {:snapshot entity
            :on-created on-method-created}]})
        [Sticky
         {:anchor body-id
          :sticky-props {:data-check-every 1}
          :contents
          [:div {:style {:width 270}}
           (when workflow?
             [buttons/SidebarButton
              {:style :light :color :button-primary
               :text "Clone..." :icon :clone :margin :bottom
               :onClick #(swap! state assoc :cloning? true)}])
           (when owner?
             (list
              (when workflow?
                [buttons/SidebarButton
                 {:style :light :color :button-primary
                  :text "Edit..." :icon :edit :margin :bottom
                  :onClick #(swap! state assoc :editing-method? true)}])
              [buttons/SidebarButton
               {:style :light :color :button-primary
                :text "Permissions..." :icon :settings :margin :bottom
                :onClick #(swap! state assoc :editing-acl? true)}]
              [buttons/SidebarButton
               {:style :light :color :state-exception
                :text "Redact" :icon :delete :margin :bottom
                :onClick #(swap! state assoc :deleting? true)}]))]}]]))})


(react/defc ConfigExporter
  {:render
   (fn [{:keys [props state refs]}]
     (let [{:keys [workspace-id entity perform-copy]} props
           {:keys [selected-workspace]} @state]
       [:div {:style {:border style/standard-line
                      :backgroundColor (:background-light style/colors)
                      :borderRadius 8 :padding "1em" :marginTop "1em"}}
        [:div {:style {:fontSize "120%" :marginBottom "0.5em"}}
         (if workspace-id "Import as:" "Export to Workspace as:")]
        (map
         (fn [field]
           (let [field-key (:key field)
                 field-name (name field-key)
                 entity-val (or (field-key entity) "")]
             [:div {:style {:float "left" :marginRight "0.5em"}}
              (style/create-form-label (:label field))
              (if (= (:type field) "identity-select")
                (style/create-identity-select {:ref field-name
                                               :data-test-id "import-root-entity-type-select"
                                               :defaultValue entity-val}
                                              (:options field))
                [input/TextField {:ref field-name
                                  :defaultValue entity-val
                                  :data-test-id (str "method-config-import-" field-name "-input")
                                  :placeholder "Required"
                                  :predicates [(input/nonempty "Fields")]}])]))
         (filterv some?
                  [{:label "Configuration Namespace" :key :namespace}
                   {:label "Configuration Name" :key :name}
                   (when-not (= "Configuration" (:entityType entity))
                     {:label "Root Entity Type" :key :rootEntityType :type "identity-select" :options common/root-entity-types})]))
        (common/clear-both)
        (when-not workspace-id
          [:div {:style {:marginBottom "1em"}}
           [:div {:style {:fontSize "120%" :margin "1em 0"}}
            "Destination Workspace:"]
           [WorkspaceSelector {:style {:width "50%"}
                               :filter #(common/access-greater-than-equal-to? (:accessLevel %) "WRITER")
                               :on-select #(swap! state assoc :selected-workspace %)}]])
        (style/create-validation-error-message (:validation-error @state))
        [comps/ErrorViewer {:error (:server-error @state)}]
        [buttons/Button {:text (if workspace-id "Import" "Export")
                         :disabled? (not (or workspace-id selected-workspace))
                         :onClick #(perform-copy selected-workspace refs)}]]))})

(defn- create-import-form [state props entity config? perform-copy]
  (let [{:keys [workspace-id on-delete]} props
        workflow? (= "Workflow" (:entityType entity))
        owner? (contains? (set (:managers entity)) (utils/get-user-email))
        any-actions? (or workflow? owner?)
        body-id (gensym "form")]
    [:div {:style {:display "flex"}}
     (when (:blocking-text @state)
       [comps/Blocker {:banner (:blocking-text @state)}])
     (when (and any-actions? (:allow-edit props))
       [Sidebar (utils/restructure entity config? workflow? on-delete owner? body-id)])
     [:div {:style {:flex "1 1 auto"} :id body-id}
      [EntityDetails {:entity entity}]
      (when config?
        (let [{:keys [method payloadObject]} entity]
          [method-common/IOView
           {:default-hidden? true
            :method-ref {:methodNamespace (:namespace method)
                         :methodName (:name method)
                         :methodVersion (:snapshotId method)}
            :values (select-keys payloadObject [:inputs :outputs])}]))
      [ConfigExporter (utils/restructure workspace-id entity perform-copy)]]]))


(react/defc- ConfigImportForm
  {:render
   (fn [{:keys [props state this]}]
     (cond
       (:loaded-config @state)
       (create-import-form state props (:loaded-config @state) true
                           (partial this :perform-copy))
       (:error @state) (style/create-server-error-message (:error @state))
       :else [comps/Spinner {:text "Loading configuration details..."}]))
   :perform-copy
   (fn [{:keys [props state]} selected-workspace refs]
     (let [{:keys [workspace-id after-import]} props
           {:keys [loaded-config]} @state
           [namespace name & fails] (input/get-and-validate refs "namespace" "name")
           workspace-id (or workspace-id
                            (select-keys (:workspace selected-workspace) [:namespace :name]))]
       (if fails
         (swap! state assoc :validation-error fails)
         (do
           (swap! state assoc :blocking-text (if (:workspace-id props) "Importing..." "Exporting..."))
           (endpoints/call-ajax-orch
            {:endpoint (endpoints/copy-method-config-to-workspace workspace-id)
             :payload {"configurationNamespace" (:namespace loaded-config)
                       "configurationName" (:name loaded-config)
                       "configurationSnapshotId" (:snapshotId loaded-config)
                       "destinationNamespace" namespace
                       "destinationName" name}
             :headers utils/content-type=json
             :on-done (fn [{:keys [success? get-parsed-response]}]
                        (swap! state dissoc :blocking-text)
                        (if success?
                          (when after-import (after-import {:config-id {:namespace namespace :name name}
                                                            :workspace-id workspace-id}))
                          (swap! state assoc :server-error (get-parsed-response false))))})))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (let [{:keys [namespace name snapshot-id]} (:id props)]
                   (endpoints/get-configuration namespace name snapshot-id true))
       :headers utils/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (if success?
                    (swap! state assoc :loaded-config (get-parsed-response))
                    (swap! state assoc :error status-text)))}))})


(react/defc- MethodImportForm
  {:render
   (fn [{:keys [props state this]}]
     (cond
       (:loaded-method @state)
       (create-import-form state props (:loaded-method @state) false
                           (partial this :perform-copy))

       (:error @state) (style/create-server-error-message (:error @state))
       :else [comps/Spinner {:text "Creating template..."}]))
   :perform-copy
   (fn [{:keys [props state]} selected-workspace refs]
     (let [{:keys [workspace-id after-import]} props
           [namespace name & fails] (input/get-and-validate refs "namespace" "name")
           rootEntityType (.-value (@refs "rootEntityType"))
           workspace-id (or workspace-id
                            (select-keys (:workspace selected-workspace) [:namespace :name]))]
       (if fails
         (swap! state assoc :validation-error fails)
         (do
           (swap! state assoc :blocking-text (if (:workspace-id props) "Importing..." "Exporting..."))
           (endpoints/call-ajax-orch
            {:endpoint endpoints/create-template
             :payload (assoc (:loaded-method @state)
                        "methodNamespace" (get-in @state [:loaded-method :namespace])
                        "methodName" (get-in @state [:loaded-method :name])
                        "methodVersion" (get-in @state [:loaded-method :snapshotId]))
             :headers utils/content-type=json
             :on-done (fn [{:keys [success? get-parsed-response]}]
                        (let [response (get-parsed-response)]
                          (if-not success?
                            (do
                              (swap! state dissoc :blocking-text)
                              (modal/pop-modal)
                              (comps/push-error (style/create-server-error-message (:message response))))
                            (endpoints/call-ajax-orch
                             {:endpoint (endpoints/post-workspace-method-config workspace-id)
                              :payload (assoc response
                                         :namespace namespace
                                         :name name
                                         :rootEntityType rootEntityType)
                              :headers utils/content-type=json
                              :on-done (fn [{:keys [success? get-parsed-response]}]
                                         (swap! state dissoc :blocking-text)
                                         (if success?
                                           (when after-import (after-import {:config-id {:namespace namespace :name name}
                                                                             :workspace-id workspace-id}))
                                           (swap! state assoc :server-error (get-parsed-response false))))}))))})))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-agora-method
                  (get-in props [:id :namespace])
                  (get-in props [:id :name])
                  (get-in props [:id :snapshot-id]))
       :headers utils/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (if success?
                    (swap! state assoc :loaded-method (get-parsed-response))
                    (swap! state assoc :error status-text)))}))})


(defn import-form [{:keys [type] :as props}]
  [(if (= type :method) MethodImportForm ConfigImportForm) props])
