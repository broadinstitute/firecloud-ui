(ns broadfcui.page.workspace.method-configs.method-config-editor
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.input :as input]
   [broadfcui.common.links :as links]
   [broadfcui.common.method.config-io :refer [IOTables]]
   [broadfcui.common.style :as style]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.checkbox :refer [Checkbox]]
   [broadfcui.components.entity-details :refer [EntityDetails]]
   [broadfcui.components.foundation-dropdown :as dropdown]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.components.sticky :refer [Sticky]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.workspace.method-configs.delete-config :as delete]
   [broadfcui.page.workspace.method-configs.launch-analysis :refer [LaunchAnalysisButton]]
   [broadfcui.page.workspace.method-configs.publish :as publish]
   [broadfcui.page.workspace.method-configs.synchronize :as mc-sync]
   [broadfcui.page.workspace.workspace-common :as ws-common]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
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

(defn- fix-validated-method-config [config]
  (let
   [munged-missing (map (fn [missing-input] {(keyword missing-input) "This input is declared in the method and missing in your method configuration."}) (:missingInputs config))
    munged-extras (map (fn [extra-input] {(keyword extra-input) "This input is declared in your method configuration but not listed in the method."}) (:extraInputs config))
    new-inputs (into (:invalidInputs config) (concat munged-missing munged-extras))]
    (assoc config :invalidInputs new-inputs)))

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
       (cond loaded-method [EntityDetails
                            (merge {:ref "methodDetails"
                                    :entity loaded-method
                                    :redacted? redacted?}
                                   (select-keys props [:editing? :snapshots :wdl-parse-error :onSnapshotIdChange]))]
             error (style/create-server-error-message error)
             :else (spinner "Loading details..."))))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :load-method-from-repo))
   :load-method-from-repo
   (fn [{:keys [props state]} & [method-ref]]
     (let [method (or method-ref (:methodRepoMethod props))]
       (let [repo (:sourceRepo method)]
         (assert repo "Caller must specify source repo for method")
         (case repo
           "agora"
           (let [{:keys [methodNamespace methodName methodVersion]} method]
             (endpoints/call-ajax-orch
              {:endpoint (endpoints/get-agora-method methodNamespace methodName methodVersion)
               :headers ajax/content-type=json
               :on-done (fn [{:keys [success? get-parsed-response]}]
                          (if success?
                            (swap! state assoc
                                   :loaded-method (assoc (get-parsed-response)
                                                    :sourceRepo repo
                                                    :repoLabel "FireCloud")
                                   :redacted? false)
                            (swap! state assoc
                                   :loaded-method {:namespace methodNamespace
                                                   :name methodName
                                                   :snapshotId (str methodVersion " (redacted)")
                                                   :sourceRepo repo
                                                   :repoLabel "FireCloud"}
                                   :redacted? true)))}))
           "dockstore"
           (let [{:keys [methodPath methodVersion]} method]
             (endpoints/dockstore-get-wdl
              methodPath methodVersion
              (fn [{:keys [success? get-parsed-response]}]
                (if success?
                  (swap! state assoc :loaded-method {:sourceRepo repo
                                                     :repoLabel "Dockstore"
                                                     :methodPath (js/decodeURIComponent methodPath)
                                                     :methodVersion methodVersion
                                                     :entityType "Workflow"
                                                     :payload (:descriptor (get-parsed-response))} :redacted? false)
                  (swap! state assoc :loaded-method nil :redacted? true)))))))))})


(react/defc- Sidebar
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [workspace-id after-delete redacted? name-validation-errors? snapshots
                   editing? locked? loaded-config body-id parent]} props
           config-id (ws-common/config->id (:methodConfiguration loaded-config))
           source-repo (get-in loaded-config [:methodConfiguration :methodRepoMethod :sourceRepo])]
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
                :color :state-success
                :text "Save" :icon :done
                :disabled? (cond redacted? "Choose an available snapshot"
                                 ; We have a single validation error
                                 name-validation-errors? (first name-validation-errors?))
                :onClick #(parent :-commit)}]
              [buttons/SidebarButton
               {:data-test-id "cancel-edit-method-config-button"
                :color :state-exception :margin :top
                :text "Cancel Editing" :icon :cancel
                :onClick #(parent :-cancel-editing)}])
             (list
              (when (parent :-can-edit?)
                [buttons/SidebarButton
                 {:data-test-id "edit-method-config-button"
                  :style :light :color :button-primary
                  :text "Edit Configuration" :icon :edit
                  :disabled? (parent :-edit-disabled?)
                  :onClick #(parent :-begin-editing snapshots)}])
              (when (parent :-can-edit?)
                [buttons/SidebarButton
                 {:data-test-id "delete-method-config-button"
                  :style :light :color :state-exception :margin :top
                  :text "Delete" :icon :delete
                  :disabled? (when locked? "The workspace is locked")
                  :onClick #(swap! state assoc :show-delete-dialog? true)}])
              (when (and (not redacted?) (= source-repo "agora"))
                [buttons/SidebarButton
                 {:style :light :color :button-primary :margin (when (parent :-can-edit?) :top)
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
                  (spinner "Loading Method Configuration...")]))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :-load-method-config))
   :-render-display
   (fn [{:keys [props state locals this]}]
     (let [locked? (get-in props [:workspace :workspace :isLocked])
           methods (:methods @state)
           methodRepoMethod (get-in @state [:loaded-config :methodConfiguration :methodRepoMethod])]
       [:div {}
        (blocker (:blocker @state))
        (when (:showing-error-popup? @state)
          (modals/render-error {:text (:wdl-parse-error @state) :dismiss #(swap! state dissoc :showing-error-popup?)}))
        (when-let [error-response (:error-response @state)]
          (modals/render-error-response {:error error-response :dismiss #(swap! state dissoc :error-response)}))
        (if (= (:sourceRepo methodRepoMethod) "agora")
          [mc-sync/SyncContainer (select-keys props [:workspace-id :config-id])])
        [:div {:style {:padding "1em 2em" :display "flex"}}
         [Sidebar (merge (select-keys props [:workspace-id :after-delete])
                         (select-keys @state [:editing? :loaded-config :redacted? :name-validation-errors?])
                         (select-keys @locals [:body-id])
                         {:parent this :locked? locked? :snapshots (get methods (replace methodRepoMethod [:methodNamespace :methodName]))})]
         (this :-render-main locked?)]]))
   :-render-main
   (fn [{:keys [state this locals props refs]} locked?]
     (let [{:keys [editing? loaded-config wdl-parse-error inputs-outputs entity-types methods methods-response redacted? entity-type? selected-entity-type]} @state
           config (:methodConfiguration loaded-config)
           {:keys [methodRepoMethod rootEntityType]} config
           entity-type-options (merge (if rootEntityType {(keyword rootEntityType) {}} {}) entity-types)
           {:keys [methodName methodNamespace]} methodRepoMethod
           {:keys [body-id]} @locals
           workspace-attributes (get-in props [:workspace :workspace :workspace-attributes])
           can-compute (get-in props [:workspace :canCompute])]
       [:div {:style {:flex "1 1 auto" :minWidth 0} :id body-id}
        (when-not editing?
          [:div {:style {:float "right"}}
           [LaunchAnalysisButton {:workspace-id (:workspace-id props)
                                  :config-id (ws-common/config->id config)
                                  :column-defaults (:workspace-column-defaults workspace-attributes)
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
                                  :on-success (:on-submission-success props)}]])
        (create-section-header "Method Configuration Name")
        (create-section
         (if editing?
           [:div {}
            [input/TextField {:data-test-id "edit-method-config-name-input"
                              :ref "confname" :style {:maxWidth 500 :width "100%" :fontSize "100%"}
                              :defaultValue (:name config)
                              :predicates [(input/nonempty-alphanumeric_-period "Config name")]
                              :onChange #(swap! state assoc :name-validation-errors? ((@refs "confname") :validate))}]
            (style/create-textfield-hint input/hint-alphanumeric_-period)]
           [:div {:style {:padding "0.5em 0 1em 0"}
                  :data-test-id "method-config-name"} (:name config)]))
        (create-section-header "Referenced Method")
        (let [method (if (empty? methods-response)
                       {:name methodName :namespace methodNamespace :entityType "Workflow"}
                       (first methods-response))]
          (create-section [MethodDetailsViewer
                           (merge {:ref "methodDetailsViewer"
                                   :methodRepoMethod methodRepoMethod
                                   :onSnapshotIdChange #(this :-load-new-method-template %)
                                   :method method
                                   :snapshots (get methods [(:methodNamespace methodRepoMethod) (:methodName methodRepoMethod)])}
                                  (utils/restructure redacted? config methods editing? wdl-parse-error))]))
        (when (or (this :-can-edit?) entity-type?)
          (create-section
           (style/create-detail-well
            [:div {:style {:display "flex" :font-size "90%"}}
             [:div {:style {:flex "1 1 60%"}}
              (when (this :-can-edit?)
                [:div {} [Checkbox {:data-test-id "data-model-checkbox"
                                    :label [:span {:style {:font-weight 500 :color "#000"}}
                                            "Configure inputs/outputs using the Workspace Data Model"
                                            (dropdown/render-info-box {:text (links/create-external {:href "https://software.broadinstitute.org/firecloud/documentation/quickstart?page=data" :style {:white-space "nowrap"}} "Learn more about Workspace data model")})]
                                    :checked? entity-type?
                                    :disabled? (or (this :-edit-disabled?) (not (seq entity-type-options)))
                                    :on-change (fn [new-value]
                                                 (let [selected-entity-type (or (:selected-entity-type @state) (name (first (keys entity-type-options))))]
                                                   (swap! locals assoc :autocomplete-list (this :-build-autocomplete-list (if new-value selected-entity-type nil)))
                                                   (if editing?
                                                     (swap! state assoc :entity-type? new-value :selected-entity-type selected-entity-type)
                                                     (do
                                                       ; Can't modify state here. Must wait until actually entering edit mode in order to correctly remember original value to restore on cancel.
                                                       (swap! locals assoc :toggle-entity-type? true)
                                                       (this :-begin-editing)))))}]])
              (if entity-type?
                [:div {:style {:padding "1rem 1rem 1rem 2rem"}}
                 [:div {}
                  [:span {:style {:font-weight 500}} "Entity type for input/output referencing: "]
                  (when-not editing? rootEntityType)]
                 (when editing?
                   (if (seq entity-type-options)
                     (style/create-identity-select {:data-test-id "edit-method-config-root-entity-type-select"
                                                    :defaultValue selected-entity-type
                                                    :style {:maxWidth 300}
                                                    :onChange #(let [new-entity-type (.. % -target -value)]
                                                                 (swap! locals assoc :autocomplete-list (this :-build-autocomplete-list new-entity-type))
                                                                 (swap! state assoc :selected-entity-type new-entity-type))}
                       (mapv #(name (first %)) entity-type-options))))])
              (when-not (seq entity-type-options)
                [:div {:style {:padding "1rem 1rem 1rem 2rem" :font-style "italic"}} "No entities in workspace. Import some in the Data tab."])]
             [:div {:style {:flex "1 1 40%"}}
              [:div {:style {:fontWeight 500}} "FireCloud Tip"]
              [:div {} "You can either change the inputs/outputs below or upload a pre-populated .json file. After upload you can always edit manually."]]])))
        (create-section-header "Connections")
        (create-section [IOTables {:ref "IOTables"
                                   :inputs-outputs inputs-outputs
                                   :entity-type? entity-type?
                                   :can-edit? (and (this :-can-edit?) (string/blank? (this :-edit-disabled?)))
                                   :begin-editing #(this :-begin-editing)
                                   :values (select-keys config [:inputs :outputs])
                                   :invalid-values {:inputs (:invalidInputs loaded-config)
                                                    :outputs (:invalidOutputs loaded-config)}
                                   :data (:autocomplete-list @locals)}])]))
   :-begin-editing
   (fn [{:keys [state locals refs this]}]
     (if-not (:entities-loaded? @locals)
       (when (this :-load-entities)
         (this :-begin-editing))
       (let [{:keys [loaded-config inputs-outputs redacted? entity-type? entity-types]} @state
             {:keys [toggle-entity-type?]} @locals
             new-entity-type? (if-not toggle-entity-type? entity-type? (not entity-type?))
             selected-entity-type (if new-entity-type? (or (-> loaded-config :methodConfiguration :rootEntityType) (name (first (keys entity-types)))) nil)]
         ((@refs "IOTables") :start-editing)
         (swap! locals assoc :autocomplete-list (this :-build-autocomplete-list selected-entity-type))
         (swap! state assoc
                :editing? true
                :entity-type? new-entity-type?
                :selected-entity-type selected-entity-type
                :original-config loaded-config
                :original-inputs-outputs inputs-outputs
                :original-redacted? redacted?
                :original-entity-type? entity-type?)
         (swap! locals assoc :toggle-entity-type? false))))
   :-cancel-editing
   (fn [{:keys [state refs]}]
     ((@refs "IOTables") :cancel-editing)
     (let [{:keys [original-inputs-outputs original-redacted? original-config original-entity-type?]} @state
           method-ref (-> original-config :methodConfiguration :methodRepoMethod)]
       (swap! state assoc
              :editing? false
              :loaded-config original-config
              :inputs-outputs original-inputs-outputs
              :redacted? original-redacted?
              :entity-type? original-entity-type?)
       ((@refs "methodDetailsViewer") :load-method-from-repo method-ref)))
   :-commit
   (fn [{:keys [props state refs]}]
     (let [{:keys [workspace-id]} props
           config (get-in @state [:loaded-config :methodConfiguration])
           name (common/get-trimmed-text refs "confname")
           root-entity-type (if (:entity-type? @state) (:selected-entity-type @state) nil)
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
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :blocker :editing?)
                    (if success?
                      (let [response (get-parsed-response)]
                        ((:on-rename props) name)
                        ((@refs "methodDetailsViewer") :clear-redacted-snapshot)
                        (swap! state assoc :entity-type? (boolean (-> response :methodConfiguration :rootEntityType)))
                        (utils/multi-swap! state (assoc :loaded-config (fix-validated-method-config response))
                                                 (dissoc :redacted?)))
                      (swap! state assoc :error-response (get-parsed-response false))))})))
   :-build-autocomplete-list
   (fn [{:keys [props state]} selected-entity-type]
     (let [{:keys [entity-types]} @state
           workspace-attributes (get-in props [:workspace :workspace :workspace-attributes])]
       (build-autocomplete-list
        (utils/restructure workspace-attributes entity-types selected-entity-type))))
   :-load-method-config
   (fn [{:keys [state props this]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-workspace-method-config (:workspace-id props) (:config-id props))
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (if success?
                    (let [unvalidated-mc (get-parsed-response)]
                      (this :-fetch-method-versions {:methodConfiguration unvalidated-mc})
                      (this :-load-inputs-outputs unvalidated-mc))
                    (swap! state assoc :error status-text)))}))
   :-load-inputs-outputs
   (fn [{:keys [state this]} unvalidated-mc]
     (endpoints/call-ajax-orch
      {:endpoint endpoints/get-inputs-outputs
       :payload (:methodRepoMethod unvalidated-mc)
       :headers ajax/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (this :-load-validated-method-config (get-parsed-response))
                    (let [fake-inputs-outputs (fn [method-config]
                                                {:inputs (mapv (fn [k] {:name (name k)}) (keys (:inputs method-config)))
                                                 :outputs (mapv (fn [k] {:name (name k)}) (keys (:outputs method-config)))})]
                      (swap! state assoc
                             :loaded-config {:methodConfiguration unvalidated-mc}
                             :entity-type? (boolean (:rootEntityType unvalidated-mc))
                             :inputs-outputs (fake-inputs-outputs unvalidated-mc)
                             :redacted? true))))}))
   :-load-validated-method-config
   (fn [{:keys [props state]} inputs-outputs]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-validated-workspace-method-config (:workspace-id props) (:config-id props))
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (if success?
                    (let [validated-mc (fix-validated-method-config (get-parsed-response))]
                      (swap! state assoc
                             :loaded-config validated-mc
                             :entity-type? (boolean (-> validated-mc :methodConfiguration :rootEntityType))
                             :inputs-outputs inputs-outputs
                             :redacted? false))
                    ;TODO: why is get-validated-mc returning errors but get-unvalidated not??? shouldn't get here
                    (swap! state assoc :error status-text)))}))
   :-load-new-method-template
   (fn [{:keys [state refs]} new-snapshot-id]
     (let [[method-namespace method-name method-path source-repo] (map (fn [key]
                                                 (get-in (:loaded-config @state)
                                                         [:methodConfiguration :methodRepoMethod key]))
                                               [:methodNamespace :methodName :methodPath :sourceRepo])
           config-namespace+name (select-keys (get-in @state [:loaded-config :methodConfiguration])
                                              [:namespace :name :rootEntityType])
           method-ref {:sourceRepo source-repo
                       :methodPath method-path
                       :methodNamespace method-namespace
                       :methodName method-name
                       :methodVersion new-snapshot-id}]
       (swap! state assoc :blocker "Updating...")
       ((@refs "methodDetailsViewer") :load-method-from-repo method-ref)
       (endpoints/call-ajax-orch
        {:endpoint endpoints/create-template
         :payload method-ref
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (let [response (get-parsed-response)]
                      (if-not success?
                        (utils/multi-swap! state (assoc :redacted? true
                                                        :showing-error-popup? true
                                                        :wdl-parse-error (:message response))
                                                 (dissoc :blocker))
                        (endpoints/call-ajax-orch
                         {:endpoint endpoints/get-inputs-outputs
                          :payload (:methodRepoMethod response)
                          :headers ajax/content-type=json
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
                                         (swap! state assoc :error (:message (get-parsed-response))))))}))))})))
   ; Inputs loaded-config (:entities-loaded? @locals)
   ; Outputs (:methods-response @state) (:methods @state) (:error-message @state)
   :-fetch-method-versions
   (fn [{:keys [locals state this]} loaded-config]
     (let [{:keys [methodName methodNamespace]} (get-in loaded-config [:methodConfiguration :methodRepoMethod])
           repo (get-in loaded-config [:methodConfiguration :methodRepoMethod :sourceRepo])]
       (case repo
         "agora"
         (endpoints/call-ajax-orch
          {:endpoint (endpoints/list-method-snapshots methodNamespace methodName)
           :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                      (let [response (get-parsed-response)]
                        (if success?
                          (swap! state assoc
                                 :methods-response response
                                 :methods {[methodNamespace methodName] (mapv :snapshotId response)})
                          ;; FIXME: :error-message is unused
                          (swap! state assoc :methods {} :error-message status-text)))
                      (if-not (:entities-loaded? @locals)
                        (this :-load-entities)))})
         "dockstore"
         (let [path (get-in loaded-config [:methodConfiguration :methodRepoMethod :methodPath])]
           (endpoints/dockstore-get-versions
            path
            (fn [{:keys [success? get-parsed-response status-text]}]
              (if success?
                (swap! state assoc
                       ;; vector only used when checking redaction, N/A for Dockstore
                       :methods {[nil nil] (mapv :name (get-parsed-response))})
                (swap! state assoc :methods {} :error-message status-text))))))))
   ; Inputs (:workspace-id props) (:workspace props) (:loaded-config @state)
   ; Outputs (:entity-types @state) (:entities-loaded? @locals) (:blocker @state) (:data-attribute-load-error @state)
   :-load-entities
   (fn [{:keys [locals props state]}]
     (swap! state assoc :blocker "Loading attributes...")
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-entity-types (:workspace-id props))
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (if success?
                    (let [loaded-config (:loaded-config @state)
                          entity-types (get-parsed-response)
                          workspace-attributes (get-in props [:workspace :workspace :workspace-attributes])
                          selected-entity-type (get-in loaded-config [:methodConfiguration :rootEntityType])]
                      (swap! locals assoc :entities-loaded? true)
                      (utils/multi-swap! state
                        (assoc :entity-types entity-types)
                        (dissoc :blocker))
                      success?)
                    ;; FIXME: :data-attribute-load-error is unused
                    (swap! state assoc :data-attribute-load-error status-text)))}))
   :-can-edit?
   (fn [{:keys [props]}]
     (common/access-greater-than? (:access-level props) "READER"))
   :-edit-disabled?
   (fn [{:keys [props state]}]
     (let [locked? (get-in props [:workspace :workspace :isLocked])
           {:keys [redacted? methods]} @state
           methodRepoMethod (get-in @state [:loaded-config :methodConfiguration :methodRepoMethod])
           snapshots (get methods (replace methodRepoMethod [:methodNamespace :methodName]))]
       (cond locked? "The workspace is locked"
             (and redacted? (empty? snapshots)) "There are no available method snapshots.")))})
