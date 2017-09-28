(ns broadfcui.page.workspace.method-configs.method-config-editor
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.method.config-io :refer [IOTables]]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.sticky :refer [Sticky]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.workspace.method-configs.delete-config :as delete]
   [broadfcui.page.workspace.method-configs.launch-analysis :as launch]
   [broadfcui.page.workspace.method-configs.publish :as publish]
   [broadfcui.page.workspace.method-configs.synchronize :as mc-sync]
   [broadfcui.page.workspace.workspace-common :as ws-common]
   [broadfcui.utils :as utils]
   ))

(defn- filter-empty [coll]
  (->> coll (map string/trim) (remove string/blank?) vec))

(defn- create-section-header [text]
  [:div {:style {:fontSize "125%" :fontWeight 500}} text])

(defn- create-section [children]
  [:div {:style {:padding "1em 0 2em 0"}} children])

(defn- build-autocomplete-list [{:keys [workspace-attributes entity-types selected-entity-type]}]
  (let [workspace-datums (map (partial str "workspace.") (map name (keys workspace-attributes)))
        entity-datums (map (partial str "this.") (get-in entity-types [(keyword selected-entity-type) :attributeNames]))]
    (concat entity-datums workspace-datums)))


(react/defc- MethodDetailsViewer
  {:get-fields
   (fn [{:keys [refs]}]
     ((@refs "methodDetails") :get-fields))
   :clear-redacted-snapshot
   (fn [{:keys [refs]}]
     ((@refs "methodDetails") :clear-redacted-snapshot))
   :get-initial-state
   (fn [{:keys [props]}]
     {:redacted? (:redacted? props)})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [loaded-method error redacted?]} @state]
       (cond loaded-method [comps/EntityDetails
                            (merge {:ref "methodDetails"
                                    :entity loaded-method
                                    :redacted? redacted?}
                                   (select-keys props [:editing? :snapshots :wdl-parse-error :onSnapshotIdChange]))]
             error (style/create-server-error-message error)
             :else [comps/Spinner {:text "Loading details..."}])))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :load-agora-method))
   :load-agora-method
   (fn [{:keys [props state]} & [method-ref]]
     (let [{:keys [namespace name snapshotId]} (or method-ref props)
           method (:method props)]
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/get-agora-method namespace name snapshotId)
         :headers utils/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (if success?
                      (swap! state assoc :loaded-method (get-parsed-response) :redacted? false)
                      (swap! state assoc :loaded-method (merge (select-keys method [:name :namespace :entityType])
                                                               {:snapshotId (str snapshotId " (redacted)")}) :redacted? true)))})))})


(react/defc- Sidebar
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [access-level workspace-id after-delete redacted? snapshots
                   editing? locked? loaded-config body-id parent]} props
           can-edit? (common/access-greater-than? access-level "READER")
           config-id (ws-common/config->id (:methodConfiguration loaded-config))]
       [:div {:style {:flex "0 0 270px" :paddingRight 30}}
        (when (:show-delete-dialog? @state)
          [delete/DeleteDialog (merge (utils/restructure config-id workspace-id after-delete)
                                      {:dismiss #(swap! state dissoc :show-delete-dialog?)})])
        (when (:show-publish-dialog? @state)
          [publish/PublishDialog (merge (utils/restructure config-id workspace-id)
                                        {:dismiss #(swap! state dissoc :show-publish-dialog?)})])
        [Sticky
         {:anchor body-id
          :sticky-props {:data-check-every 1}
          :contents
          [:div {:style {:width 270}}
           (if editing?
             (list
              [buttons/SidebarButton
               {:data-test-id "save-edited-method-config-button"
                :color :success-state
                :text "Save" :icon :done
                :disabled? (when redacted? "Choose an available snapshot")
                :onClick #(parent :-commit)}]
              [buttons/SidebarButton
               {:data-test-id "cancel-edit-method-config-button"
                :color :exception-state :margin :top
                :text "Cancel Editing" :icon :cancel
                :onClick #(parent :-cancel-editing)}])
             (list
              (when can-edit?
                [buttons/SidebarButton
                 {:data-test-id "edit-method-config-button"
                  :style :light :color :button-primary
                  :text "Edit Configuration" :icon :edit
                  :disabled? (cond locked? "The workspace is locked"
                                   (and redacted? (empty? snapshots)) "There are no available method snapshots.")
                  :onClick #(parent :-begin-editing snapshots)}])
              (when can-edit?
                [buttons/SidebarButton
                 {:data-test-id "delete-method-config-button"
                  :style :light :color :exception-state :margin :top
                  :text "Delete" :icon :delete
                  :disabled? (when locked? "The workspace is locked")
                  :onClick #(swap! state assoc :show-delete-dialog? true)}])
              (when-not redacted?
                [buttons/SidebarButton
                 {:style :light :color :button-primary :margin (when can-edit? :top)
                  :text "Publish..." :icon :share
                  :onClick #(swap! state assoc :show-publish-dialog? true)}])))]}]]))})


(react/defc MethodConfigEditor
  {:get-initial-state
   (fn []
     {:editing? false
      :sidebar-visible? true})
   :component-will-mount
   (fn [{:keys [locals]}]
     (swap! locals assoc :body-id (gensym "config")))
   :render
   (fn [{:keys [state this]}]
     (cond (every? @state [:loaded-config :methods]) (this :-render-display)
           (:error @state) (style/create-server-error-message (:error @state))
           :else [:div {:style {:textAlign "center"}}
                  [comps/Spinner {:text "Loading Method Configuration..."}]]))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :-load-validated-method-config))
   :component-did-update
   (fn [{:keys [state]}]
     (let [{:keys [methods loaded-config]} @state]
       (when (and (not methods) loaded-config)
         (let [{:keys [methodName methodNamespace]} (get-in loaded-config [:methodConfiguration :methodRepoMethod])]
           (endpoints/call-ajax-orch
            {:endpoint (endpoints/list-method-snapshots methodNamespace methodName)
             :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                        (let [response (get-parsed-response)]
                          (if success?
                            (swap! state assoc
                                   :methods-response response
                                   :methods {[methodNamespace methodName] (mapv #(:snapshotId %) response)})
                            ;; FIXME: :error-message is unused
                            (swap! state assoc :error-message status-text))))})))))
   :-render-display
   (fn [{:keys [props state locals this]}]
     (let [locked? (get-in props [:workspace :workspace :isLocked])
           methods (:methods @state)
           methodRepoMethod (get-in @state [:loaded-config :methodConfiguration :methodRepoMethod])]
       [:div {}
        [comps/Blocker {:banner (:blocker @state)}]
        [mc-sync/SyncContainer (select-keys props [:workspace-id :config-id])]
        [:div {:style {:padding "1em 2em" :display "flex"}}
         [Sidebar (merge (select-keys props [:access-level :workspace-id :after-delete])
                         (select-keys @state [:editing? :loaded-config :redacted?])
                         (select-keys @locals [:body-id])
                         {:parent this :locked? locked? :snapshots (get methods (replace methodRepoMethod [:methodNamespace :methodName]))})]
         (this :-render-main locked?)]]))
   :-render-main
   (fn [{:keys [state this locals props]} locked?]
     (let [{:keys [editing? loaded-config wdl-parse-error inputs-outputs entity-types methods methods-response redacted?]} @state
           config (:methodConfiguration loaded-config)
           {:keys [methodRepoMethod rootEntityType]} config
           {:keys [methodName methodNamespace methodVersion]} methodRepoMethod
           {:keys [body-id]} @locals
           workspace-attributes (get-in props [:workspace :workspace :workspace-attributes])
           can-compute (get-in props [:workspace :canCompute])]
       [:div {:style {:flex "1 1 auto" :minWidth 0} :id body-id}
        (when-not editing?
          [:div {:style {:float "right"}}
           (launch/render-button {:workspace-id (:workspace-id props)
                                  :config-id (ws-common/config->id config)
                                  :root-entity-type rootEntityType
                                  :disabled? (cond locked?
                                                   "This workspace is locked."
                                                   (not can-compute)
                                                   "You do not have access to run analysis."
                                                   (not (:bucket-access? props))
                                                   (str "You do not currently have access"
                                                        " to the Google Bucket associated with this workspace.")
                                                   redacted?
                                                   "The method snapshot this config references has been redacted.")
                                  :on-success (:on-submission-success props)})])
        (create-section-header "Method Configuration Name")
        (create-section
         (if editing?
           (style/create-text-field {:ref "confname" :style {:maxWidth 500}
                                     :data-test-id "edit-method-config-name-input"
                                     :defaultValue (:name config)})
           [:div {:style {:padding "0.5em 0 1em 0"}
                  :data-test-id "method-config-name"} (:name config)]))
        (create-section-header "Referenced Method")
        (let [method (if (empty? methods-response)
                       {:name methodName :namespace methodNamespace :entityType "Workflow"}
                       (first methods-response))]
          (create-section [MethodDetailsViewer
                           (merge {:ref "methodDetailsViewer"
                                   :name methodName
                                   :namespace methodNamespace
                                   :snapshotId methodVersion
                                   :onSnapshotIdChange #(this :-load-new-method-template %)
                                   :method method
                                   :snapshots (get methods [(:methodNamespace methodRepoMethod) (:methodName methodRepoMethod)])}
                                  (utils/restructure redacted? config methods editing? wdl-parse-error))]))
        (create-section-header "Root Entity Type")
        (create-section
         (if editing?
           (style/create-identity-select {:ref "rootentitytype"
                                          :data-test-id "edit-method-config-root-entity-type-select"
                                          :defaultValue rootEntityType
                                          :style {:maxWidth 500}
                                          :onChange #(swap! state assoc :autocomplete-list
                                                            (build-autocomplete-list
                                                             {:workspace-attributes workspace-attributes
                                                              :entity-types entity-types
                                                              :selected-entity-type (.. % -target -value)}))}
                                         common/root-entity-types)
           [:div {:style {:padding "0.5em 0 1em 0"}} rootEntityType]))
        (create-section-header "Connections")
        (create-section [IOTables {:ref "IOTables"
                                   :inputs-outputs inputs-outputs
                                   :values (select-keys config [:inputs :outputs])
                                   :invalid-values {:inputs (:invalidInputs loaded-config)
                                                    :outputs (:invalidOutputs loaded-config)}
                                   :data (:autocomplete-list @state)}])]))
   :-begin-editing
   (fn [{:keys [props state locals refs this]}]
     (if-not (:entities-loaded? @locals)
       (do (swap! state assoc :blocker "Loading attributes...")
           (endpoints/call-ajax-orch
            {:endpoint (endpoints/get-entity-types (:workspace-id props))
             :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                        (if success?
                          (let [loaded-config (:loaded-config @state)
                                entity-types (get-parsed-response)
                                workspace-attributes (get-in props [:workspace :workspace :workspace-attributes])
                                selected-entity-type (get-in loaded-config [:methodConfiguration :rootEntityType])]
                            (swap! locals assoc :entities-loaded? true)
                            (swap! state assoc
                                   :blocker nil
                                   :entity-types entity-types
                                   :autocomplete-list (build-autocomplete-list
                                                       (utils/restructure workspace-attributes entity-types selected-entity-type)))
                            (this :-begin-editing))
                          ;; FIXME: :data-attribute-load-error is unused
                          (swap! state assoc :data-attribute-load-error status-text)))}))
       (let [{:keys [loaded-config inputs-outputs redacted?]} @state]
         ((@refs "IOTables") :start-editing)
         (swap! state assoc :editing? true :original-config loaded-config :original-inputs-outputs inputs-outputs :original-redacted? redacted?))))
   :-cancel-editing
   (fn [{:keys [state refs]}]
     ((@refs "IOTables") :cancel-editing)
     (let [{:keys [original-inputs-outputs original-redacted? original-config]} @state
           method-ref (-> original-config :methodConfiguration :methodRepoMethod)]
       (swap! state assoc :editing? false :loaded-config original-config :inputs-outputs original-inputs-outputs :redacted? original-redacted?)
       ((@refs "methodDetailsViewer") :load-agora-method {:namespace (:methodNamespace method-ref)
                                                          :name (:methodName method-ref)
                                                          :snapshotId (:methodVersion method-ref)})))
   :-commit
   (fn [{:keys [props state refs]}]
     (let [{:keys [workspace-id]} props
           config (-> @state :loaded-config :methodConfiguration)
           [name root-entity-type] (common/get-text refs "confname" "rootentitytype")
           selected-values ((@refs "IOTables") :save)]
       (swap! state assoc :blocker "Updating...")
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/update-workspace-method-config workspace-id (ws-common/config->id config))
         :payload (merge config
                         selected-values
                         {:name name
                          :rootEntityType root-entity-type
                          :methodRepoMethod (merge (:methodRepoMethod config)
                                                   ((@refs "methodDetailsViewer") :get-fields))
                          :workspaceName workspace-id})
         :headers utils/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :blocker :editing?)
                    (if success?
                      (do (swap! state dissoc :redacted?)
                          ((:on-rename props) name)
                          ((@refs "methodDetailsViewer") :clear-redacted-snapshot)
                          (swap! state assoc :loaded-config (get-parsed-response) :blocker nil))
                      (comps/push-error-response (get-parsed-response false))))})))
   :-load-validated-method-config
   (fn [{:keys [state props]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-validated-workspace-method-config (:workspace-id props) (:config-id props))
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (if success?
                    (let [response (get-parsed-response)
                          fake-inputs-outputs (fn [data]
                                                (let [method-config (:methodConfiguration data)]
                                                  {:inputs (mapv (fn [k] {:name (name k)}) (keys (:inputs method-config)))
                                                   :outputs (mapv (fn [k] {:name (name k)}) (keys (:outputs method-config)))}))]
                      (endpoints/call-ajax-orch
                       {:endpoint endpoints/get-inputs-outputs
                        :payload (get-in response [:methodConfiguration :methodRepoMethod])
                        :headers utils/content-type=json
                        :on-done (fn [{:keys [success? get-parsed-response]}]
                                   (if success?
                                     (swap! state assoc :loaded-config response :inputs-outputs (get-parsed-response) :redacted? false)
                                     (swap! state assoc :loaded-config response :inputs-outputs (fake-inputs-outputs response) :redacted? true)))}))
                    (swap! state assoc :error status-text)))}))
   :-load-new-method-template
   (fn [{:keys [state refs]} new-snapshot-id]
     (let [[method-namespace method-name] (map (fn [key]
                                                 (get-in (:loaded-config @state)
                                                         [:methodConfiguration :methodRepoMethod key]))
                                               [:methodNamespace :methodName])
           config-namespace+name (select-keys (get-in @state [:loaded-config :methodConfiguration])
                                              [:namespace :name])
           method-ref {:methodNamespace method-namespace
                       :methodName method-name
                       :methodVersion new-snapshot-id}]
       (swap! state assoc :blocker "Updating...")
       ((@refs "methodDetailsViewer") :load-agora-method {:namespace method-namespace
                                                          :name method-name
                                                          :snapshotId new-snapshot-id})
       (endpoints/call-ajax-orch
        {:endpoint endpoints/create-template
         :payload method-ref
         :headers utils/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (let [response (get-parsed-response)]
                      (if success?
                        (endpoints/call-ajax-orch
                         {:endpoint endpoints/get-inputs-outputs
                          :payload (:methodRepoMethod response)
                          :headers utils/content-type=json
                          :on-done (fn [{:keys [success? get-parsed-response]}]
                                     (swap! state dissoc :blocker :wdl-parse-error)
                                     (let [template {:methodConfiguration (merge response config-namespace+name)}]
                                       (if success?
                                         (swap! state assoc
                                                :loaded-config (assoc template
                                                                 :invalidInputs {}
                                                                 :validInputs {}
                                                                 :invalidOutputs {}
                                                                 :validOutputs {})
                                                :inputs-outputs (get-parsed-response)
                                                :redacted? false)
                                         (swap! state assoc :error (:message (get-parsed-response))))))})
                        (do
                          (swap! state assoc :redacted? true :blocker nil :wdl-parse-error (:message response))
                          (comps/push-error (style/create-server-error-message (:message response)))))))})))})
