(ns org.broadinstitute.firecloud-ui.page.method-config-importer
  (:require
    [dmohs.react :as react]
    [clojure.string :refer [trim]]
    [org.broadinstitute.firecloud-ui.common :as common :refer [clear-both get-text]]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.input :as input]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.methods-configs-acl :as mca]
    [org.broadinstitute.firecloud-ui.utils :as utils]))


(defn- create-import-form [state props this entity fields]
  (let [{:keys [workspace-id on-back]} props
        workspaces-list (:workspaces-list @state)]
    [:div {}
     (when (:blocking-text @state)
       [comps/Blocker {:banner (:blocking-text @state)}]) 
     (let [config? (contains? entity "method")]
       [:div {:style {:marginBottom "1em" :width 290}}
        (when (:show-perms-overlay? @state)
          [mca/AgoraPermsEditor
           {:is-conf config?
            :selected-entity entity
            :dismiss-self #(swap! state dissoc :show-perms-overlay?)}])
        [comps/SidebarButton {:style :light :margin :top :color :button-blue
                              :text "Permissions..." :icon :gear
                              :onClick #(swap! state assoc :show-perms-overlay? true)}]
        [comps/SidebarButton
         {:style :light :margin :top :color :exception-red
          :text "Redact" :icon :trash-can
          :onClick
          #(when (js/confirm "Are you sure?")
            (let [name (entity "name")
                  namespace (entity "namespace")
                  snapshotId (entity "snapshotId")]
              (swap! state assoc :blocking-text "Redacting...")
              (endpoints/call-ajax-orch
                {:endpoint (endpoints/delete-agora-entity
                             config? namespace name snapshotId)
                 :on-done (fn [{:keys [success? status-text]}]
                            (swap! state dissoc :blocking-text)
                            (if success?
                              ((:on-delete props))
                              (js/alert (str "Error: " status-text))))})))}]])
     [comps/EntityDetails {:entity entity}]
     [:div {:style {:fontSize "120%" :margin "1.5em 0 0.5em 0"}} "Save as:"]
     (map
       (fn [field]
         [:div {:style {:float "left" :marginRight "0.5em"}}
          (style/create-form-label (:label field))
          [input/TextField {:defaultValue (entity (:key field))
                            :ref (:key field) :placeholder "Required"
                            :predicates [(input/nonempty "Fields")]}]])
       fields)
     (clear-both)

     (when-not workspace-id
       [:div {:style {:marginBottom "1em"}}
        [:div {:style {:fontSize "120%" :margin "1em 0"}} "Destination Workspace:"]
        (style/create-select
          {:ref "workspace-id"
           :style {:width 300}
           :onChange (fn [event]
                       (swap! state assoc :selected-workspace
                                   (nth workspaces-list (js/parseInt (.-value (.-target event))))))}
          (map
            (fn [ws] (str (get-in ws ["workspace" "namespace"]) "/" (get-in ws ["workspace" "name"])))
            workspaces-list))])
     (style/create-validation-error-message (:validation-error @state))
     [comps/ErrorViewer {:error (:server-error @state)}]
     [comps/Button {:text (if workspace-id "Import" "Export")
                    :onClick #(react/call :perform-copy this)}]]))


(react/defc ConfigImportForm
  {:render
   (fn [{:keys [props state this]}]
     (cond
       (and (:loaded-config @state)
         (or (:workspace-id props) (:workspaces-list @state)))
       (create-import-form state props this (:loaded-config @state)
         [{:label "Configuration Namespace" :key "namespace"}
          {:label "Configuration Name" :key "name"}])

       (:error @state) (style/create-server-error-message (:error @state))
       :else [comps/Spinner {:text "Loading configuration details..."}]))
   :perform-copy
   (fn [{:keys [props state refs]}]
     (let [{:keys [workspace-id config after-import on-back]} props
           [namespace name & fails] (input/get-and-validate refs "namespace" "name")
           workspace-id (or workspace-id
                          {:namespace (get-in (:selected-workspace @state) ["workspace" "namespace"])
                           :name (get-in (:selected-workspace @state) ["workspace" "name"])})]
       (if fails
         (swap! state assoc :validation-error fails)
         (do
           (swap! state assoc :blocking-text "Importing...")
           (endpoints/call-ajax-orch
             {:endpoint (endpoints/copy-method-config-to-workspace workspace-id)
              :payload {"configurationNamespace" (config "namespace")
                        "configurationName" (config "name")
                        "configurationSnapshotId" (config "snapshotId")
                        "destinationNamespace" namespace
                        "destinationName" name}
              :headers {"Content-Type" "application/json"}
              :on-done (fn [{:keys [success? get-parsed-response]}]
                         (swap! state dissoc :blocking-text)
                         (if success?
                           (do
                             (on-back)
                             (common/scroll-to-top)
                             (when after-import (after-import {"namespace" namespace
                                                               "name" name})))
                           (swap! state assoc :server-error (get-parsed-response))))})))))
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
                    (get-in props [:config "namespace"])
                    (get-in props [:config "name"])
                    (get-in props [:config "snapshotId"]))
        :headers {"Content-Type" "application/json"}
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :loaded-config (get-parsed-response))
                     (swap! state assoc :error status-text)))}))})


(react/defc MethodImportForm
  {:render
   (fn [{:keys [props state this]}]
     (cond
       (and (:template @state) (:loaded-method @state)
         (or (:workspace-id props) (:workspaces-list @state)))
       (create-import-form state props this (:loaded-method @state)
         [{:label "Configuration Namespace" :key "namespace"}
          {:label "Configuration Name" :key "name"}
          {:label "Root Entity Type" :key "rootEntityType"}])

       (:error @state) (style/create-server-error-message (:error @state))
       :else [comps/Spinner {:text "Creating template..."}]))
   :perform-copy
   (fn [{:keys [props state refs]}]
     (let [{:keys [workspace-id after-import on-back]} props
           [namespace name rootEntityType & fails] (input/get-and-validate refs "namespace" "name" "rootEntityType")
           workspace-id (or workspace-id
                          {:namespace (get-in (:selected-workspace @state) ["workspace" "namespace"])
                           :name (get-in (:selected-workspace @state) ["workspace" "name"])})]
       (if fails
         (swap! state assoc :validation-error fails)
         (do
           (swap! state assoc :blocking-text "Importing...")
           (endpoints/call-ajax-orch
             {:endpoint (endpoints/post-workspace-method-config workspace-id)
              :payload (assoc (:template @state)
                         "namespace" namespace
                         "name" name
                         "rootEntityType" rootEntityType)
              :headers {"Content-Type" "application/json"}
              :on-done (fn [{:keys [success? get-parsed-response]}]
                         (swap! state dissoc :blocking-text)
                         (if success?
                           (do
                             (on-back)
                             (common/scroll-to-top)
                             (when after-import (after-import {"namespace" namespace
                                                               "name" name})))
                           (swap! state assoc :server-error (get-parsed-response))))})))))
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
                    (get-in props [:method "namespace"])
                    (get-in props [:method "name"])
                    (get-in props [:method "snapshotId"]))
        :headers {"Content-Type" "application/json"}
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :loaded-method (get-parsed-response))
                     (swap! state assoc :error status-text)))})
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/create-template (:method props))
        :payload (assoc (:method props)
                   "methodNamespace" (get-in props [:method "namespace"])
                   "methodName" (get-in props [:method "name"])
                   "methodVersion" (get-in props [:method "snapshotId"]))
        :headers {"Content-Type" "application/json"}
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :template (get-parsed-response))
                     (swap! state assoc :error status-text)))}))})


(react/defc Table
 {:reload
  (fn [{:keys [state]}]
    (swap! state dissoc :methods :configs))
  :render
  (fn [{:keys [props state]}]
    (cond
      (:hidden? props) nil
      (:error-message @state) (style/create-server-error-message (:error-message @state))
      (or (nil? (:methods @state)) (nil? (:configs @state)))
      [comps/Spinner {:text "Loading methods and configurations..."}]
      :else
      [table/Table
       {:columns [{:header "Type" :starting-width 100}
                  {:header "Item" :starting-width 450 :as-text #(% "name")
                   :sort-by (fn [m]
                              [(m "namespace") (m "name") (int (m "snapshotId"))])
                   :sort-initial :asc
                   :content-renderer
                   (fn [item]
                     (style/create-link {:text (style/render-entity (item "namespace") (item "name") (item "snapshotId"))
                                         :onClick #((:on-item-selected props) item)}))}
                  {:header "Synopsis" :starting-width 160}
                  (table/date-column {:header "Created"})
                  {:header "Referenced Method" :starting-width 250
                   :content-renderer (fn [fields]
                                       (if fields
                                         (apply style/render-entity fields)
                                         "N/A"))}]
        :filters [{:text "All" :pred (constantly true)}
                  {:text "Methods Only" :pred #(= :method (:type %))}
                  {:text "Configs Only" :pred #(= :config (:type %))}]
        :data (concat (:methods @state) (:configs @state))
        :->row (fn [item]
                 [(item "entityType")
                  item
                  (item "synopsis")
                  (item "createDate")
                  (when (= :config (:type item))
                    (mapv #((get item "method" {}) %) ["namespace" "name" "snapshotId"]))])}]))
  :component-did-mount #(react/call :load-data (:this %))
  :component-did-update #(react/call :load-data (:this %))
  :load-data
  (fn [{:keys [this state]}]
    (when-not (or (:configs @state) (:methods @state))
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
            (swap! state assoc :error-message status-text)))})))})


(react/defc MethodConfigImporter
  {:render
   (fn [{:keys [this props state refs]}]
     [:div {}
      (if-let [item (:selected-item @state)]
        [:div {}
         (style/create-link {:text "Methods"
                             :onClick #(swap! state dissoc :selected-item)})
         (icons/font-icon {:style {:verticalAlign "middle" :margin "0 1ex 0 1ex"}} :angle-right)
         [:h2 {:style {:display "inline-block"}} (item "namespace") "/" (item "name")
          [:span {:style {:marginLeft "1ex" :fontWeight "normal"}} "#" (item "snapshotId")]]]
        [:h2 {} "Methods"])
      (when (:selected-item @state)
        (let [item-type (:type (:selected-item @state))
              form (if (= item-type :method) MethodImportForm ConfigImportForm)]
          [form {:on-delete (fn []
                              (swap! state dissoc :selected-item)
                              (react/call :reload (@refs "table")))
                 item-type (:selected-item @state)
                 :workspace-id (:workspace-id props)
                 :on-back #(swap! state dissoc :selected-item)
                 :after-import (:after-import props)}]))
      [Table {:ref "table"
              :hidden? (:selected-item @state)
              :on-item-selected #(swap! state assoc :selected-item %)}]])})
