(ns broadfcui.page.method-repo.method-config-importer
  (:require
    [dmohs.react :as react]
    [clojure.string :refer [trim lower-case]]
    [broadfcui.common :refer [clear-both root-entity-types]]
    [broadfcui.common.components :as comps]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.input :as input]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.common.table :as table]
    [broadfcui.common.table-utils :refer [add-right]]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.page.method-repo.create-method :as create]
    [broadfcui.page.method-repo.methods-configs-acl :as mca]
    [broadfcui.utils :as utils]))


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
       :ok-button {:text "Redact" :onClick #(react/call :redact this)}}])
   :redact
   (fn [{:keys [props state]}]
     (let [[name namespace snapshotId] (map (:entity props) ["name" "namespace" "snapshotId"])]
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
    [:div {}
     (when (:blocking-text @state)
       [comps/Blocker {:banner (:blocking-text @state)}])
     [comps/EntityDetails {:entity entity}]
     (when (:allow-edit props)
       [:div {:style {:margin "1em 0"}}
        [:div {:style {:float "left" :width 290 :paddingRight "1em"}}
         [comps/SidebarButton {:style :light :color :button-primary
                               :text "Permissions..." :icon :settings
                               :onClick #(modal/push-modal [mca/AgoraPermsEditor {:save-endpoint (endpoints/persist-agora-method-acl entity)
                                                                                  :load-endpoint (let [[name nmsp sid] (map entity ["name" "namespace" "snapshotId"])]
                                                                                                   (endpoints/get-agora-method-acl nmsp name sid config?))
                                                                                  :entityType (entity "entityType") :entityName (mca/get-ordered-name entity)
                                                                                  :title (str (entity "entityType") " " (mca/get-ordered-name entity))}])}]]
        [:div {:style {:float "left" :width 290}}
         [comps/SidebarButton {:style :light :color :exception-state
                               :text "Redact" :icon :delete
                               :onClick #(modal/push-modal [Redactor {:entity entity :config? config?
                                                                      :on-delete (:on-delete props)}])}]]
        (clear-both)])
     [:div {:style {:border style/standard-line
                    :backgroundColor (:background-light style/colors)
                    :borderRadius 8 :padding "1em" :marginTop "1em"}}
      [:div {:style {:fontSize "120%" :marginBottom "0.5em"}}
       (if workspace-id "Import as:" "Export to Workspace as:")]
      (map
        (fn [field]
          [:div {:style {:float "left" :marginRight "0.5em"}}
           (style/create-form-label (:label field))
           (if (= (:type field) "identity-select")
             (style/create-identity-select {:ref (:key field)
                                            :defaultValue (or (entity (:key field)) "")}
                                           (:options field))
             [input/TextField {:defaultValue (entity (:key field))
                               :ref (:key field) :placeholder "Required"
                               :predicates [(input/nonempty "Fields")]}])])
        fields)
      (clear-both)
      (when-not workspace-id
        (let [sorted-ws-list (sort-by (juxt #(lower-case (get-in % ["workspace" "namespace"]))
                                            #(lower-case (get-in % ["workspace" "name"])))
                                      (:workspaces-list @state))]
          [:div {:style {:marginBottom "1em"}}
           [:div {:style {:fontSize "120%" :margin "1em 0"}} "Destination Workspace:"]
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
            (map
             (fn [ws] (str (get-in ws ["workspace" "namespace"]) "/" (get-in ws ["workspace" "name"])))
             sorted-ws-list))]))
      (style/create-validation-error-message (:validation-error @state))
      [comps/ErrorViewer {:error (:server-error @state)}]
      [comps/Button {:text (if workspace-id "Import" "Export")
                     :onClick #(react/call :perform-copy this)}]]]))


(react/defc ConfigImportForm
  {:render
   (fn [{:keys [props state this locals]}]
     (cond
       (and
         (:loaded-config @state)
         (or (:workspace-id props) (:workspaces-list @state)))
       (create-import-form state props this locals (:loaded-config @state) true
         [{:label "Configuration Namespace" :key "namespace"}
          {:label "Configuration Name" :key "name"}])

       (:error @state) (style/create-server-error-message (:error @state))
       :else [comps/Spinner {:text "Loading configuration details..."}]))
   :perform-copy
   (fn [{:keys [props state refs]}]
     (let [{:keys [workspace-id config after-import]} props
           [namespace name & fails] (input/get-and-validate refs "namespace" "name")
           workspace-id (or workspace-id
                          {:namespace (get-in (:selected-workspace @state) ["workspace" "namespace"])
                           :name (get-in (:selected-workspace @state) ["workspace" "name"])})]
       (if fails
         (swap! state assoc :validation-error fails)
         (do
           (swap! state assoc :blocking-text (if (:workspace-id props) "Importing..." "Exporting..."))
           (endpoints/call-ajax-orch
             {:endpoint (endpoints/copy-method-config-to-workspace workspace-id)
              :payload {"configurationNamespace" (config "namespace")
                        "configurationName" (config "name")
                        "configurationSnapshotId" (config "snapshotId")
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
                       (let [ws-list (get-parsed-response false)]
                         (swap! state assoc :workspaces-list ws-list :selected-workspace (first ws-list)))
                       (swap! state assoc :error status-text)))}))
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-configuration
                    (get-in props [:config "namespace"])
                    (get-in props [:config "name"])
                    (get-in props [:config "snapshotId"]))
        :headers utils/content-type=json
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :loaded-config (get-parsed-response false))
                     (swap! state assoc :error status-text)))}))})


(react/defc MethodImportForm
  {:render
   (fn [{:keys [props state this locals]}]
     (cond
       (and
         (:loaded-method @state)
         (or (:workspace-id props) (:workspaces-list @state)))
       (create-import-form state props this locals (:loaded-method @state) false
         [{:label "Configuration Namespace" :key "namespace"}
          {:label "Configuration Name" :key "name"}
          {:label "Root Entity Type" :key "rootEntityType" :type "identity-select" :options root-entity-types}])

       (:error @state) (style/create-server-error-message (:error @state))
       :else [comps/Spinner {:text "Creating template..."}]))
   :perform-copy
   (fn [{:keys [props state refs]}]
     (let [{:keys [workspace-id after-import]} props
           [namespace name & fails] (input/get-and-validate refs "namespace" "name")
           rootEntityType (.-value (@refs "rootEntityType"))
           workspace-id (or workspace-id
                          {:namespace (get-in (:selected-workspace @state) ["workspace" "namespace"])
                           :name (get-in (:selected-workspace @state) ["workspace" "name"])})]
       (if fails
         (swap! state assoc :validation-error fails)
         (do
           (swap! state assoc :blocking-text (if (:workspace-id props) "Importing..." "Exporting..."))
           (endpoints/call-ajax-orch
             {:endpoint (endpoints/create-template (:method props))
              :payload (assoc (:method props)
                         "methodNamespace" (get-in props [:method "namespace"])
                         "methodName" (get-in props [:method "name"])
                         "methodVersion" (get-in props [:method "snapshotId"]))
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
                       (let [ws-list (get-parsed-response false)]
                         (swap! state assoc :workspaces-list ws-list :selected-workspace (first ws-list)))
                       (swap! state assoc :error status-text)))}))
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-agora-method
                    (get-in props [:method "namespace"])
                    (get-in props [:method "name"])
                    (get-in props [:method "snapshotId"]))
        :headers utils/content-type=json
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :loaded-method (get-parsed-response false))
                     (swap! state assoc :error status-text)))}))})


(react/defc Table
 {:reload
  (fn [{:keys [this]}]
    (react/call :load-data this))
  :render
  (fn [{:keys [props state this]}]
    (cond
      (:hidden? props) nil
      (:error-message @state) (style/create-server-error-message (:error-message @state))
      (or (nil? (:methods @state)) (nil? (:configs @state)))
      [comps/Spinner {:text "Loading methods and configurations..."}]
      :else
      [table/Table
       {:columns [{:header "Type" :starting-width 100}
                  {:header "Name" :starting-width 350
                   :sort-by (fn [m]  [(clojure.string/lower-case (m "name")) (int (m "snapshotId"))])
                   :filter-by (fn [m] [(m "name") (str (m "snapshotId"))])
                   :as-text (fn [item] (str (item "namespace") "\n" (item "name") "\nSnapshot ID: " (item "snapshotId")))
                   :content-renderer
                   (fn [item]
                     (style/create-link {:text (style/render-name-id (item "name") (item "snapshotId"))
                                         :onClick #((:on-item-selected props) item)}))}
                  {:header "Namespace" :starting-width 160
                   :sort-by (fn [m] (clojure.string/lower-case (m "namespace")))
                   :sort-initial :asc
                   :as-text (fn [m] (m "namespace"))
                   :content-renderer (fn [item]
                                       (if (:in-workspace? props)
                                         (item "namespace")
                                         (style/create-link
                                           {:text (str (item "namespace"))
                                            :onClick #(modal/push-modal
                                                        [mca/AgoraPermsEditor
                                                         {:save-endpoint (endpoints/post-agora-namespace-acl (item "namespace") (= :config (:type item)))
                                                          :load-endpoint (endpoints/get-agora-namespace-acl (item "namespace") (= :config (:type item)))
                                                          :entityType "Namespace" :entityName (item "namespace")
                                                          :title (str "Namespace " (item "namespace"))}])})))}
                  {:header "Synopsis" :starting-width 160}
                  (table/date-column {:header "Created"})
                  {:header "Referenced Method" :starting-width 250
                   :content-renderer (fn [fields]
                                       (if fields
                                         (apply style/render-entity fields)
                                         "N/A"))}]
        :toolbar (add-right
                  [comps/Button
                   {:text "Create new method..."
                    :onClick #(modal/push-modal
                               [create/CreateMethodDialog
                                {:on-success (fn [new-method]
                                               (react/call :reload this)
                                               ((:on-item-selected props) (assoc new-method :type :method)))}])}])
        :filter-groups [{:text "All" :pred (constantly true)}
                        {:text "Methods Only" :pred #(= :method (:type %))}
                        {:text "Configs Only" :pred #(= :config (:type %))}]
        :data (concat (:methods @state) (:configs @state))
        :->row (fn [item]
                 [(item "entityType")
                  item
                  item
                  (item "synopsis")
                  (item "createDate")
                  (when (= :config (:type item))
                    (mapv (get item "method" {}) ["namespace" "name" "snapshotId"]))])}]))
  :component-did-mount
  (fn [{:keys [this]}]
    (react/call :load-data this))
  :load-data
  (fn [{:keys [state]}]
    (swap! state dissoc :configs :methods :error-message)
    (endpoints/call-ajax-orch
      {:endpoint endpoints/list-configurations
       :on-done
       (fn [{:keys [success? get-parsed-response status-text]}]
         (if success?
           (swap! state assoc :configs (map #(assoc % :type :config) (get-parsed-response false)))
           (swap! state assoc :error-message status-text)))})
    (endpoints/call-ajax-orch
      {:endpoint endpoints/list-methods
       :on-done
       (fn [{:keys [success? get-parsed-response status-text]}]
         (if success?
           (swap! state assoc :methods (map #(assoc % :type :method) (get-parsed-response false)))
           (swap! state assoc :error-message status-text)))}))})


(react/defc MethodConfigImporter
  {:render
   (fn [{:keys [props state refs]}]
     [:div {}
      (when-let [item (:selected-item @state)]
        ;; TODO allow nav
        [:div {:style {:marginBottom "1rem" :fontSize "1.1rem"}}
         [comps/Breadcrumbs
          {:crumbs
           [{:text "Methods" :onClick #(swap! state dissoc :selected-item)}
            {:text [:span {} (item "namespace") "/" (item "name")
                    [:span {:style {:marginLeft "1rem" :fontWeight "normal"}} "#" (item "snapshotId")]]}]}]])
      (when (:selected-item @state)
        (let [item-type (:type (:selected-item @state))
              form (if (= item-type :method) MethodImportForm ConfigImportForm)]
          [form {:on-delete (fn []
                              (swap! state dissoc :selected-item)
                              (react/call :reload (@refs "table")))
                 item-type (:selected-item @state)
                 :workspace-id (:workspace-id props)
                 :allow-edit (:allow-edit props)
                 :after-import (:after-import props)}]))
      [Table {:ref "table"
              :in-workspace? (:workspace-id props)
              :hidden? (:selected-item @state)
              :on-item-selected #(swap! state assoc :selected-item %)}]])})
