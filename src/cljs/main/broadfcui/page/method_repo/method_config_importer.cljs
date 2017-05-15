(ns broadfcui.page.method-repo.method-config-importer
  (:require
    [dmohs.react :as react]
    [clojure.string :refer [trim lower-case]]
    [broadfcui.common :refer [clear-both root-entity-types scroll-to-top]]
    [broadfcui.common.components :as comps]
    [broadfcui.common.flex-utils :as flex]
    [broadfcui.common.input :as input]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.common.table.style :as table-style]
    [broadfcui.common.table.table :refer [Table]]
    [broadfcui.common.table.utils :as table-utils]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.nav :as nav]
    [broadfcui.page.method-repo.create-method :as create]
    [broadfcui.page.method-repo.methods-configs-acl :as mca]
    [broadfcui.persistence :as persistence]
    [broadfcui.utils :as utils]
    ))


(react/defc Redactor
  {:render
   (fn [{:keys [props state this]}]
     [comps/OKCancelForm
      {:header "Confirm redaction"
       :content
       [:div {:style {:width 500}}
        (when (:redacting? @state)
          [comps/Blocker {:banner "Redacting..."}])
        [:div {:style {:marginBottom "1em"}}
         (str "Are you sure you want to redact this " (if (:config? props) "configuration" "method") "?")]
        [comps/ErrorViewer {:error (:error @state)
                            :expect {401 "Unauthorized"}}]]
       :ok-button {:text "Redact" :onClick #(this :redact)}}])
   :redact
   (fn [{:keys [props state]}]
     (let [{:keys [name namespace snapshotId]} (:entity props)]
       (swap! state assoc :redacting? true :error nil)
       (endpoints/call-ajax-orch
         {:endpoint (endpoints/delete-agora-entity (:config? props) namespace name snapshotId)
          :on-done (fn [{:keys [success? get-parsed-response]}]
                     (swap! state dissoc :redacting?)
                     (if success?
                       (do (modal/pop-modal) ((:on-delete props)))
                       (swap! state assoc :error (get-parsed-response false))))})))})

(defn- create-import-form [state props this locals entity config? fields]
  (let [{:keys [workspace-id]} props]
    [:div {:style {:display "flex"}}
     (when (:blocking-text @state)
       [comps/Blocker {:banner (:blocking-text @state)}])
     (when (:allow-edit props)
       [:div {:style {:flex "0 0 290px" :paddingRight "1rem"}}
        (when (= "Workflow" (:entityType entity))
          [comps/SidebarButton
           {:style :light :color :button-primary
            :text "Clone..." :icon :clone :margin :bottom
            :onClick #(modal/push-modal [create/CreateMethodDialog
                                         {:duplicate entity
                                          :on-created (fn [_ id]
                                                        (nav/go-to-path :method id)
                                                        (scroll-to-top))}])}])
        [comps/SidebarButton
         {:style :light :color :button-primary
          :text "Permissions..." :icon :settings :margin :bottom
          :onClick #(modal/push-modal
                     [mca/AgoraPermsEditor
                      {:save-endpoint (endpoints/persist-agora-method-acl entity)
                       :load-endpoint (let [{:keys [name namespace snapshotId]} entity]
                                        (endpoints/get-agora-method-acl namespace name snapshotId config?))
                       :entityType (:entityType entity)
                       :entityName (mca/get-ordered-name entity)
                       :title (str (:entityType entity) " " (mca/get-ordered-name entity))}])}]
        [comps/SidebarButton
         {:style :light :color :exception-state
          :text "Redact" :icon :delete :margin :bottom
          :onClick #(modal/push-modal [Redactor {:entity entity :config? config?
                                                 :on-delete (:on-delete props)}])}]])
     [:div {:style {:flex "1 1 auto"}}
      [comps/EntityDetails {:entity entity}]
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
                                              :defaultValue entity-val}
                                             (:options field))
               [input/TextField {:ref field-name
                                 :defaultValue entity-val
                                 :placeholder "Required"
                                 :predicates [(input/nonempty "Fields")]}])]))
        fields)
       (clear-both)
       (when-not workspace-id
         (let [sorted-ws-list (sort-by (comp (partial mapv lower-case)
                                             (juxt :namespace :name)
                                             :workspace)
                                       (:workspaces-list @state))]
           [:div {:style {:marginBottom "1em"}}
            [:div {:style {:fontSize "120%" :margin "1em 0"}}
             "Destination Workspace:"]
            (style/create-select
             {:defaultValue ""
              :ref (utils/create-element-ref-handler
                    {:store locals
                     :key :workspace-select
                     :did-mount
                     #(.on (.select2 (js/$ %)) "select2:select"
                           (fn [event]
                             (swap! state assoc :selected-workspace
                                    (nth sorted-ws-list (js/parseInt (.-value (.-target event)))))))
                     :will-unmount
                     #(.off (js/$ %))})
              :style {:width 500}}
             (map (fn [ws] (clojure.string/join "/" (replace (:workspace ws) [:namespace :name])))
                  sorted-ws-list))]))
       (style/create-validation-error-message (:validation-error @state))
       [comps/ErrorViewer {:error (:server-error @state)}]
       [comps/Button {:text (if workspace-id "Import" "Export")
                      :data-test-id (if workspace-id "import-button" "export-button")
                      :onClick #(this :perform-copy)}]]]]))


(react/defc ConfigImportForm
  {:render
   (fn [{:keys [props state this locals]}]
     (cond
       (and
         (:loaded-config @state)
         (or (:workspace-id props) (:workspaces-list @state)))
       (create-import-form state props this locals (:loaded-config @state) true
         [{:label "Configuration Namespace" :key :namespace}
          {:label "Configuration Name" :key :name}])
       (:error @state) (style/create-server-error-message (:error @state))
       :else [comps/Spinner {:text "Loading configuration details..."}]))
   :perform-copy
   (fn [{:keys [props state refs]}]
     (let [{:keys [workspace-id after-import]} props
           {:keys [loaded-config]} @state
           [namespace name & fails] (input/get-and-validate refs "namespace" "name")
           workspace-id (or workspace-id
                            (select-keys (-> @state :selected-workspace :workspace) [:namespace :name]))]
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
     (when-not (:workspace-id props)
       (endpoints/call-ajax-orch
         {:endpoint endpoints/list-workspaces
          :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                     (if success?
                       (let [ws-list (get-parsed-response)]
                         (swap! state assoc :workspaces-list ws-list :selected-workspace (first ws-list)))
                       (swap! state assoc :error status-text)))}))
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-configuration
                   (get-in props [:id :namespace])
                   (get-in props [:id :name])
                   (get-in props [:id :snapshot-id]))
        :headers utils/content-type=json
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :loaded-config (get-parsed-response))
                     (swap! state assoc :error status-text)))}))})


(react/defc MethodImportForm
  {:render
   (fn [{:keys [props state this locals]}]
     (cond
       (and
         (:loaded-method @state)
         (or (:workspace-id props) (:workspaces-list @state)))
       (create-import-form state props this locals (:loaded-method @state) false
         [{:label "Configuration Namespace" :key :namespace}
          {:label "Configuration Name" :key :name}
          {:label "Root Entity Type" :key :rootEntityType :type "identity-select" :options root-entity-types}])

       (:error @state) (style/create-server-error-message (:error @state))
       :else [comps/Spinner {:text "Creating template..."}]))
   :perform-copy
   (fn [{:keys [props state refs]}]
     (let [{:keys [workspace-id after-import]} props
           [namespace name & fails] (input/get-and-validate refs "namespace" "name")
           rootEntityType (.-value (@refs "rootEntityType"))
           workspace-id (or workspace-id
                            (select-keys (-> @state :selected-workspace :workspace) [:namespace :name]))]
       (if fails
         (swap! state assoc :validation-error fails)
         (do
           (swap! state assoc :blocking-text (if (:workspace-id props) "Importing..." "Exporting..."))
           (endpoints/call-ajax-orch
             {:endpoint (endpoints/create-template (:loaded-method @state))
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
     (when-not (:workspace-id props)
       (endpoints/call-ajax-orch
         {:endpoint endpoints/list-workspaces
          :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                     (if success?
                       (let [ws-list (get-parsed-response)]
                         (swap! state assoc :workspaces-list ws-list :selected-workspace (first ws-list)))
                       (swap! state assoc :error status-text)))}))
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


(react/defc MethodRepoTable
  (->>
   {:reload
    (fn [{:keys [this]}]
      (this :load-data))
    :render
    (fn [{:keys [props state]}]
      (cond
        (:error-message @state) (style/create-server-error-message (:error-message @state))
        (or (nil? (:methods @state)) (nil? (:configs @state)))
        [comps/Spinner {:text "Loading methods and configurations..."}]
        :else
        [Table
         {:persistence-key "method-repo-table" :v 1
          :data (or (:filtered-data @state) [])
          :body
          {:columns
           [{:header "Type" :initial-width 100
             :column-data :entityType}
            {:header "Name" :initial-width 350
             :sort-by (juxt (comp lower-case :name) (comp int :snapshotId))
             :filter-by (fn [{:keys [name snapshotId]}] (str name " " (int snapshotId)))
             :as-text (fn [{:keys [name snapshotId]}] (str name " Snapshot ID: " snapshotId))
             :render (fn [{:keys [namespace name snapshotId entityType]}]
                       (let [id {:namespace namespace
                                 :name name
                                 :snapshot-id snapshotId}
                             type (if (= entityType "Configuration") :method-config :method)]
                         (style/create-link
                          {:text (style/render-name-id name snapshotId)
                           :data-test-id (str name "-" (item "snapshotId") "-link")
                           :href (if (:in-workspace? props) "javascript:;" (nav/get-link type id))
                           :onClick (when (:in-workspace? props) #((:on-selected props) type id))})))}
            {:header "Namespace" :initial-width 160
             :sort-by (comp lower-case :namespace)
             :sort-initial :asc
             :as-text :namespace
             :render (fn [{:keys [namespace type]}]
                       (if (:in-workspace? props)
                         namespace
                         (style/create-link
                          {:text namespace
                           :onClick #(modal/push-modal
                                      [mca/AgoraPermsEditor
                                       {:save-endpoint (endpoints/post-agora-namespace-acl namespace (= :config type))
                                        :load-endpoint (endpoints/get-agora-namespace-acl namespace (= :config type))
                                        :entityType "Namespace" :entityName namespace
                                        :title (str "Namespace " namespace)}])})))}
            {:header "Synopsis" :initial-width 160 :column-data :synopsis}
            (table-utils/date-column {:header "Created" :column-data :createDate})
            {:header "Referenced Method" :initial-width 250
             :column-data (fn [item]
                            (when (= :config (:type item))
                              (mapv (get item :method {}) [:namespace :name :snapshotId])))
             :as-text (fn [[namespace name snapshotId]]
                        (if namespace
                          (str namespace "/" name " Snapshot ID: " snapshotId)
                          "N/A"))
             :render (fn [fields]
                       (if fields
                         (apply style/render-entity fields)
                         "N/A"))}]
           :style table-style/table-heavy}
          :toolbar
          {:items [[comps/FilterGroupBar
                    {:data (concat (:methods @state) (:configs @state))
                     :selected-index (:filter-group-index @state)
                     :on-change (fn [index data]
                                  (swap! state assoc
                                         :filter-group-index index
                                         :filtered-data data))
                     :filter-groups [{:text "All" :data-test-id "all-methods-and-configs-filter"}
                                     {:text "Methods Only" :pred (comp (partial = :method) :data-test-id "methods-only-filter" :type )}
                                     {:text "Configs Only" :pred (comp (partial = :config) :data-test-id "configs-only-filter" :type)}]}]
                   flex/spring
                   [comps/Button
                    {:text "Create new method..."
                     :onClick #(modal/push-modal
                                [create/CreateMethodDialog
                                 {:on-created (fn [type id]
                                                (if (:in-workspace? props)
                                                  ((:on-selected props) type id)
                                                  (nav/go-to-path :method id)))}])}]]}}]))
    :component-did-mount
    (fn [{:keys [this]}]
      (this :load-data))
    :load-data
    (fn [{:keys [state]}]
      (swap! state dissoc :configs :methods :error-message)
      (endpoints/call-ajax-orch
       {:endpoint endpoints/list-configurations
        :on-done
        (fn [{:keys [success? get-parsed-response status-text]}]
          (if success?
            (swap! state assoc :configs (map #(assoc % :type :config) (get-parsed-response)))
            (swap! state assoc :error-message status-text)))})
      (endpoints/call-ajax-orch
       {:endpoint endpoints/list-methods
        :on-done
        (fn [{:keys [success? get-parsed-response status-text]}]
          (if success?
            (swap! state assoc :methods (map #(assoc % :type :method) (get-parsed-response)))
            (swap! state assoc :error-message status-text)))}))}
   (persistence/with-state-persistence
    {:key "method-repo-table-container" :version 1
     :initial {:filter-group-index 0}
     :only [:v :filter-group-index]})))


(react/defc MethodConfigImporter
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [workspace-id]} props
           type (some :type [props @state])
           id (some :id [props @state])]
       [:div {:key (str id)}
        (when id
          [:h3 {} (str (:namespace id) "/" (:name id) " #" (:snapshot-id id))])
        (if id
          (let [form (if (= type :method) MethodImportForm ConfigImportForm)]
            [form (merge
                   (utils/restructure type id)
                   (select-keys props [:workspace-id :allow-edit :after-import])
                   {:on-delete #(nav/go-to-path :method-repo)})])
          [MethodRepoTable {:in-workspace? workspace-id
                            :on-selected #(swap! state assoc :type %1 :id %2)}])]))})
