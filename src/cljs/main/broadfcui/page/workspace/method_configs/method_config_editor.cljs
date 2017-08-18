(ns broadfcui.page.workspace.method-configs.method-config-editor
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.components.sticky :refer [Sticky]]
   [broadfcui.config :as config]
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

(defn- refresh-autocomplete [{:keys [engine workspace-attributes entity-types selected-entity-type]}]
  (let [workspace-datums (map (partial str "workspace.") (map name (keys workspace-attributes)))
        entity-datums (map (partial str "this.") (get-in entity-types [(keyword selected-entity-type) :attributeNames]))
        datums (concat entity-datums workspace-datums)]
    (.clear engine)
    (.add engine (clj->js datums))))

(defn- fake-inputs-outputs [data]
  (utils/log data)
  (let [method-config (:methodConfiguration data)]
    {:inputs (mapv #(identity {:name (name %)}) (keys (:inputs method-config)))
     :outputs (mapv #(identity {:name (name %)}) (keys (:outputs method-config)))}))


(react/defc- MethodDetailsViewer
  {:get-fields
   (fn [{:keys [refs]}]
     ((@refs "methodDetails") :get-fields))
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [loaded-method error]} @state]
       (cond loaded-method [comps/EntityDetails
                            (merge {:ref "methodDetails"
                                    :entity loaded-method}
                                   (select-keys props [:editing? :snapshots :wdl-parse-error :onSnapshotIdChange :redacted?]))]
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
         :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                    (if success?
                      (swap! state assoc :loaded-method (get-parsed-response))
                      (swap! state assoc :loaded-method (merge (select-keys method [:name :namespace :entityType]) {:snapshotId "-"}))))})))})


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
              [comps/SidebarButton {:color :success-state
                                    :text "Save" :icon :done
                                    :data-test-id (config/when-debug "save-editted-method-config-button")
                                    :onClick #(parent :-commit)}]
              [comps/SidebarButton {:color :exception-state :margin :top
                                    :text "Cancel Editing" :icon :cancel
                                    :data-test-id (config/when-debug "cancel-edit-method-config-button")
                                    :onClick #(parent :-cancel-editing)}])
             (list
              (when (and can-edit? (or (not redacted?) snapshots))
                [comps/SidebarButton {:style :light :color :button-primary
                                      :text "Edit Configuration" :icon :edit
                                      :disabled? (when locked? "The workspace is locked")
                                      :data-test-id (config/when-debug "edit-method-config-button")
                                      :onClick #(parent :-begin-editing snapshots)}])
              (when can-edit?
                [comps/SidebarButton {:style :light :color :exception-state :margin (when (or (not redacted?) snapshots) :top)
                                      :text "Delete" :icon :delete
                                      :disabled? (when locked? "The workspace is locked")
                                      :data-test-id (config/when-debug "delete-method-config-button")
                                      :onClick #(swap! state assoc :show-delete-dialog? true)}])
              (when (not redacted?)
                [comps/SidebarButton {:style :light :color :button-primary :margin (when can-edit? :top)
                                      :text "Publish..." :icon :share
                                      :onClick #(swap! state assoc :show-publish-dialog? true)}])))]}]]))})


(react/defc MethodConfigEditor
  {:get-initial-state
   (fn []
     {:editing? false
      :sidebar-visible? true})
   :component-will-mount
   (fn [{:keys [locals]}]
     (swap! locals assoc
            :body-id (gensym "config")
            :engine (comps/create-bloodhound-engine
                     {:local []
                      :datum-tokenizer (aget comps/Bloodhound "tokenizers" "nonword")
                      :query-tokenizer (aget comps/Bloodhound "tokenizers" "nonword")})))
   :render
   (fn [{:keys [state this]}]
     (cond (every? @state [:loaded-config :methods]) (this :-render-display)
           (:error @state) (style/create-server-error-message (:error @state))
           :else [:div {:style {:textAlign "center"}}
                  [comps/Spinner {:text "Loading Method Configuration..."}]]))
   :component-did-mount
   (fn [{:keys [state this]}]
     (this :-load-validated-method-config)
     (endpoints/call-ajax-orch
      {:endpoint endpoints/list-methods
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (let [response (get-parsed-response)]
                    (if success?
                      (swap! state assoc :methods-response response :methods (->> response
                                                                                  (map #(select-keys % [:namespace :name :snapshotId]))
                                                                                  (group-by (juxt :namespace :name))
                                                                                  (utils/map-values (partial map :snapshotId))))
                      ;; FIXME: :error-message is unused
                      (swap! state assoc :error-message status-text))))}))
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
                         {:parent this :locked? locked? :snapshots (get methods [(:methodNamespace methodRepoMethod) (:methodName methodRepoMethod)])})]
         (this :-render-main locked?)
         (common/clear-both)]]))
   :-render-main
   (fn [{:keys [state this locals props]} locked?]
     (let [{:keys [editing? loaded-config wdl-parse-error inputs-outputs entity-types methods methods-response redacted?]} @state
           config (:methodConfiguration loaded-config)
           {:keys [methodRepoMethod]} config
           {:keys [methodName methodNamespace methodVersion]} methodRepoMethod
           {:keys [body-id engine]} @locals
           workspace-attributes (get-in props [:workspace :workspace :workspace-attributes])]
       [:div {:style {:flex "1 1 auto"} :id body-id}
        (when-not editing?
          [:div {:style {:float "right"}}
           (launch/render-button {:workspace-id (:workspace-id props)
                                  :config-id (ws-common/config->id config)
                                  :root-entity-type (:rootEntityType config)
                                  :disabled? (cond locked?
                                                   "This workspace is locked."
                                                   (not (:bucket-access? props))
                                                   (str "You do not currently have access"
                                                        " to the Google Bucket associated with this workspace.")
                                                   redacted?
                                                   "The method snapshot this config references has been redacted.")
                                  :on-success (:on-submission-success props)})])
        (create-section-header "Method Configuration Name")
        (create-section
         (if editing?
           (style/create-text-field {:ref "confname" :style {:width 500}
                                     :data-test-id (config/when-debug "edit-method-config-name-input")
                                     :defaultValue (:name config)})
           [:div {:style {:padding "0.5em 0 1em 0"}
                  :data-test-id (config/when-debug "method-config-name")} (:name config)]))
        (create-section-header "Referenced Method")
        (let [matching-methods (filter #(and (= methodNamespace (:namespace %)) (= methodName (:name %))) methods-response)
              method (if (empty? matching-methods)
                       {:name methodName :namespace methodNamespace}
                       (first matching-methods))]
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
                                          :data-test-id (config/when-debug "edit-method-config-root-entity-type-select")
                                          :defaultValue (:rootEntityType config)
                                          :style {:width 500}
                                          :onChange #(refresh-autocomplete {:engine engine
                                                                            :workspace-attributes workspace-attributes
                                                                            :entity-types entity-types
                                                                            :selected-entity-type (.. % -target -value)})}
                                         common/root-entity-types)
           [:div {:style {:padding "0.5em 0 1em 0"}} (:rootEntityType config)]))
        (create-section-header "Inputs")
        (this :-render-input-output-list
              {:values (:inputs config)
               :all-values (:inputs inputs-outputs)
               :ref-prefix "in"
               :invalid-values (:invalidInputs loaded-config)})
        (create-section-header "Outputs")
        (this :-render-input-output-list
              {:values (:outputs config)
               :all-values (:outputs inputs-outputs)
               :ref-prefix "out"
               :invalid-values (:invalidOutputs loaded-config)})]))
   :-render-input-output-list
   (fn [{:keys [state locals]}
        {:keys [values ref-prefix invalid-values all-values]}]
     (let [{:keys [editing?]} @state]
       (create-section
        (map
         (fn [{:keys [name inputType outputType optional]}]
           (let [type (or inputType outputType)
                 name-kwd (keyword name)
                 field-value (get values name-kwd "")
                 error (get invalid-values name-kwd)]
             [:div {:key name :style {:marginBottom "1rem"}}
              (list
               [:div {:style {:display "inline-block"
                              :margin "0 0.5rem 0.5rem 0" :padding "0.5rem"
                              :backgroundColor (:background-light style/colors)
                              :border style/standard-line :borderRadius 2}}
                (str name ": (" (when optional "optional ") type ")")]
               (when (and error (not editing?) (not optional))
                 (icons/icon {:style {:marginRight "0.5rem" :alignSelf "center"
                                      :color (:exception-state style/colors)}}
                             :error))
               (when editing?
                 [comps/Typeahead {:ref (str ref-prefix "_" name)
                                   :field-attributes {:defaultValue field-value
                                                      :style {:width 500 :margin 0}
                                                      :data-test-id (config/when-debug (str name "-text-input"))}
                                   :engine (:engine @locals)
                                   :behavior {:minLength 1}}])
               (when-not editing?
                 (or field-value [:span {:style {:fontStyle "italic"}} "No value entered"])))
              (when (and error (not optional))
                [:div {}
                 [:div {:style {:display "inline-block"
                                :padding "0.5em" :marginBottom "0.5rem"
                                :backgroundColor (:exception-state style/colors)
                                :border style/standard-line :borderRadius 2}}
                  error]])]))
         all-values))))
   :-begin-editing
   (fn [{:keys [props state locals this]} snapshots]
     (when-not (:entities-loaded? @locals)
       (swap! locals assoc :entities-loaded? true)
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/get-entity-types (:workspace-id props))
         :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                    (if success?
                      (let [loaded-config (:loaded-config @state)
                            entity-types (get-parsed-response)
                            workspace-attributes (get-in props [:workspace :workspace :workspace-attributes])
                            selected-entity-type (get-in loaded-config [:methodConfiguration :rootEntityType])]
                        (refresh-autocomplete {:engine (:engine @locals)
                                               :workspace-attributes workspace-attributes
                                               :entity-types entity-types
                                               :selected-entity-type selected-entity-type})
                        (swap! state assoc :entity-types entity-types))
                      ;; FIXME: :data-attribute-load-error is unused
                      (swap! state assoc :data-attribute-load-error status-text)))}))
     (let [{:keys [loaded-config inputs-outputs redacted?]} @state]
       (when redacted? (this :-load-new-method-template (last snapshots)))
       (swap! state assoc :editing? true :original-config loaded-config :original-inputs-outputs inputs-outputs)))
   :-cancel-editing
   (fn [{:keys [state refs]}]
     (let [original-loaded-config (:original-config @state)
           original-inputs-outputs (:original-inputs-outputs @state)
           method-ref (-> original-loaded-config :methodConfiguration :methodRepoMethod)]
       (swap! state assoc :editing? false :loaded-config original-loaded-config :inputs-outputs original-inputs-outputs)
       ((@refs "methodDetailsViewer") :load-agora-method {:namespace (:methodNamespace method-ref)
                                                          :name (:methodName method-ref)
                                                          :snapshotId (:methodVersion method-ref)})))
   :-commit
   (fn [{:keys [props state refs]}]
     (let [{:keys [workspace-id]} props
           config (-> @state :loaded-config :methodConfiguration)
           [name root-entity-type] (common/get-text refs "confname" "rootentitytype")
           deref-vals (fn [io-key ref-prefix]
                        (->> (io-key (:inputs-outputs @state))
                             (map :name)
                             (map (juxt identity #((@refs (str ref-prefix "_" %)) :get-text)))
                             (into {})))]
       (swap! state assoc :blocker "Updating...")
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/update-workspace-method-config workspace-id (ws-common/config->id config))
         :payload (assoc config
                    :name name
                    :rootEntityType root-entity-type
                    :inputs (deref-vals :inputs "in")
                    :outputs (deref-vals :outputs "out")
                    :methodRepoMethod (merge (:methodRepoMethod config)
                                             ((@refs "methodDetailsViewer") :get-fields))
                    :workspaceName workspace-id)
         :headers utils/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :blocker :editing?)
                    (if success?
                      (do (when (:redacted? @state)
                            (swap! state assoc :redacted? false))
                          ((:on-rename props) name)
                          (swap! state assoc :loaded-config (get-parsed-response) :blocker nil))
                      (comps/push-error-response (get-parsed-response false))))})))
   :-load-validated-method-config
   (fn [{:keys [state props]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-validated-workspace-method-config (:workspace-id props) (:config-id props))
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (if success?
                    (let [response (get-parsed-response)]
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
        {:endpoint (endpoints/create-template method-ref)
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
                                                :inputs-outputs (get-parsed-response))
                                         (swap! state assoc :error (:message (get-parsed-response))))))})
                        (do
                          (swap! state assoc :blocker nil :wdl-parse-error (:message response))
                          (comps/push-error (style/create-server-error-message (:message response)))))))})))})
