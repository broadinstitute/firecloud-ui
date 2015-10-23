(ns org.broadinstitute.firecloud-ui.page.method-config-importer
  (:require
    [dmohs.react :as react]
    [clojure.string :refer [trim]]
    [org.broadinstitute.firecloud-ui.common :as common :refer [clear-both get-text]]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.methods-configs-acl :as mca]
    [org.broadinstitute.firecloud-ui.utils :as utils]))


(defn- create-import-form [state props this entity fields]
  (let [{:keys [workspace-id on-back]} props
        workspaces-list (:workspaces-list @state)]
    [:div {}
     (when (:blocking? @state)
       [comps/Blocker {:banner (if (:blocking-text @state)
                                 (:blocking-text @state) "Please wait...")}])
     [:div {:style {:paddingBottom "0.5em"}}
      (style/create-link #(on-back)
        (icons/font-icon {:style {:fontSize "70%" :marginRight "0.5em"}} :angle-left)
        "Back to table")]

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
          (fn []
            (let [conf (js/confirm "Are you sure ?")]
              (when conf
                (let [type (if (contains? entity "method")
                             "configuration" "method")
                      name (entity "name")
                      namespace (entity "namespace")
                      snapshotId (entity "snapshotId")]
                  (do
                    (swap! state assoc :blocking-text "Redacting..." :blocking? true)
                    (endpoints/call-ajax-orch
                      {:endpoint (endpoints/delete-agora-entity
                                   type namespace name snapshotId)
                       :on-done (fn [{:keys [success? status-text]}]
                                    (swap! state dissoc :blocking? :blocking-text)
                                    (if success?
                                      ((:on-delete props))
                                      (js/alert (str "Error ! Message : " status-text))))}))))))}]])
     [comps/EntityDetails {:entity entity}]
     [:div {:style {:fontSize "120%" :margin "1.5em 0 0.5em 0"}} "Save as:"]
     (map
       (fn [field]
         [:div {:style {:float "left" :marginRight "0.5em"}}
          (style/create-form-label (:label field))
          (style/create-text-field {:defaultValue (entity (:key field))
                                    :ref (:key field) :placeholder "Required"
                                    :onChange #(swap! state dissoc :bad-input)})])
       fields)
     [:div {:ref "error" :style {:float "left"}}]
     (when (:bad-input @state)
       [:div {:style {:float "left" :padding "1.6em 0 0 1em"
                      :fontWeight 500 :color (:exception-red style/colors)}}
        "All fields required"])
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
     [:div {}
      [comps/Button {:text (if workspace-id "Import" "Export")
                     :onClick #(react/call :perform-copy this)}]]]))


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
           [namespace name] (get-text refs "namespace" "name")
           workspace-id (or workspace-id
                          {:namespace (get-in (:selected-workspace @state) ["workspace" "namespace"])
                           :name (get-in (:selected-workspace @state) ["workspace" "name"])})]
       ;; TODO - implement generalized validation
       (if (some empty? [namespace name])
         (do
           (common/scroll-to-center (.getDOMNode (@refs "error")) 100)
           (swap! state assoc :bad-input true))
         (do
           (swap! state assoc :blocking? true :blocking-text "Importing...")
           (endpoints/call-ajax-orch
             {:endpoint (endpoints/copy-method-config-to-workspace workspace-id)
              :payload {"configurationNamespace" (config "namespace")
                        "configurationName" (config "name")
                        "configurationSnapshotId" (config "snapshotId")
                        "destinationNamespace" namespace
                        "destinationName" name}
              :headers {"Content-Type" "application/json"}
              :on-done (fn [{:keys [success? xhr]}]
                         (swap! state dissoc :blocking?)
                         (if success?
                           (do
                             (on-back)
                             (common/scroll-to-top)
                             (when after-import (after-import {"namespace" namespace
                                                               "name" name})))
                           (js/alert (str "Import error: " (.-responseText xhr)))))})))))
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
           [namespace name rootEntityType] (get-text refs "namespace" "name" "rootEntityType")
           workspace-id (or workspace-id
                          {:namespace (get-in (:selected-workspace @state) ["workspace" "namespace"])
                           :name (get-in (:selected-workspace @state) ["workspace" "name"])})]
       ;; TODO - implement generalized validation
       (if (some empty? [namespace name rootEntityType])
         (do
           (common/scroll-to-center (.getDOMNode (@refs "error")) 100)
           (swap! state assoc :bad-input true))
         (do
           (swap! state assoc :blocking? true :blocking-text "Importing...")
           (endpoints/call-ajax-orch
             {:endpoint (endpoints/post-workspace-method-config workspace-id)
              :payload (assoc (:template @state)
                         "namespace" namespace
                         "name" name
                         "rootEntityType" rootEntityType)
              :headers {"Content-Type" "application/json"}
              :on-done (fn [{:keys [success? xhr]}]
                         (swap! state dissoc :blocking?)
                         (if success?
                           (do
                             (on-back)
                             (common/scroll-to-top)
                             (when after-import (after-import {"namespace" namespace
                                                               "name" name})))
                           (js/alert (str "Import error: " (.-responseText xhr)))))})))))
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
  {:render
   (fn [{:keys [props state]}]
     [table/Table
      {:toolbar (fn [built-in]
                  [:div {}
                   [:div {:style {:float "left"}} built-in]
                   [:div {:style {:float "left" :marginLeft "1em" :marginTop -3}}
                    [comps/FilterBar {:data (concat (:methods props) (:configs props))
                                      :buttons [{:text "All" :filter identity}
                                                {:text "Methods Only" :filter #(= :method (:type %))}
                                                {:text "Configs Only" :filter #(= :config (:type %))}]
                                      :did-filter #(swap! state assoc :data %)}]]
                   (common/clear-both)])
       :columns [{:header "Type" :starting-width 100}
                 {:header "Namespace" :starting-width 150}
                 {:header "Name" :starting-width 200 :as-text #(% "name") :sort-by :text
                  :content-renderer
                  (fn [item]
                    (style/create-link
                      (let [func (if (= :method (:type item)) :on-method-selected :on-config-selected)]
                        #((func props) (dissoc item :type)))
                      (item "name")))}
                 {:header "Snapshot ID" :starting-width 100}
                 {:header "Synopsis" :starting-width 160}
                 (table/date-column {:header "Created"})
                 {:header "Referenced Method" :starting-width 250
                  :content-renderer (fn [fields]
                                      (if fields
                                        (apply style/render-entity fields)
                                        "N/A"))}]
       :data (map
               (fn [item]
                 [(item "entityType")
                  (item "namespace")
                  item
                  (item "snapshotId")
                  (item "synopsis")
                  (item "createDate")
                  (when (= :config (:type item))
                    (mapv #((get item "method" {}) %) ["namespace" "name" "snapshotId"]))])
               (:data @state))}])})


(react/defc MethodConfigImporter
  {:render
   (fn [{:keys [props state this]}]
     (cond
       (:selected-method @state)
       [MethodImportForm {:on-delete  #(react/call :reload-entities this)
                          :method (:selected-method @state)
                          :workspace-id (:workspace-id props)
                          :on-back #(swap! state dissoc :selected-method)
                          :after-import (:after-import props)}]
       (:selected-config @state)
       [ConfigImportForm {:on-delete  #(react/call :reload-entities this)
                          :config (:selected-config @state)
                          :workspace-id (:workspace-id props)
                          :on-back #(swap! state dissoc :selected-config)
                          :after-import (:after-import props)}]

       (and (:configs-list @state) (:methods-list @state))
       [Table {:configs (:configs-list @state)
               :methods (:methods-list @state)
               :on-config-selected #(swap! state assoc :selected-config %)
               :on-method-selected #(swap! state assoc :selected-method %)}]

       (:error-message @state) (style/create-server-error-message (:error-message @state))
       :else [comps/Spinner {:text "Loading..."}]))
   :reload-entities
   (fn [{:keys [state this]}]
     (swap! state dissoc :blocking? :selected-method :selected-config :methods-list :configs-list)
     (endpoints/call-ajax-orch
       {:endpoint endpoints/list-configurations
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :configs-list (map #(assoc % :type :config) (get-parsed-response)))
                     (swap! state assoc :error-message status-text)))})
     (endpoints/call-ajax-orch
       {:endpoint endpoints/list-methods
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :methods-list (map #(assoc % :type :method) (get-parsed-response)))
                     (swap! state assoc :error-message status-text)))}))
   :component-did-mount
   (fn [{:keys [state this]}]
     (react/call :reload-entities this))})
