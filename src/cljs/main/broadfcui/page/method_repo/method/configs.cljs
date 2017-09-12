(ns broadfcui.page.method-repo.method.configs
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.components :as comps]
   [broadfcui.common.links :as links]
   [broadfcui.common.method.config-io :as config-io]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.sticky :refer [Sticky]]
   [broadfcui.components.tab-bar :as tab-bar]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.net :as net]
   [broadfcui.page.method-repo.methods-configs-acl :as mca]
   [broadfcui.page.method-repo.redactor :refer [Redactor]]
   [broadfcui.page.method-repo.synchronize :as mr-sync]
   [broadfcui.utils :as utils]
   [broadfcui.page.method-repo.method-config-importer :as mci]
   [broadfcui.common.input :as input]
   [broadfcui.page.workspace.method-configs.synchronize :as mc-sync]
   ))


(react/defc IOView
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [error inputs-outputs]} @state]
       (cond error [comps/ErrorViewer (:error error)]
             inputs-outputs [config-io/IOTables {:default-hidden? true
                                                 :style {:marginTop "1rem"}
                                                 :inputs-outputs inputs-outputs
                                                 :values (:values props)}]
             :else [comps/Spinner {:text "Loading inputs/outputs..."}])))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint endpoints/get-inputs-outputs
       :payload (:method-ref props)
       :headers utils/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (swap! state assoc :inputs-outputs (get-parsed-response))
                    (swap! state assoc :error (get-parsed-response false))))}))})


(defn render-config-table [{:keys [make-config-link-props configs]}]
  [Table
   {:data configs
    :body {:empty-message "You don't have access to any published configurations for this method."
           :style table-style/table-light
           :behavior {:reorderable-columns? false}
           :columns [{:header "Configuration" :initial-width 400
                      :as-text (fn [{:keys [name namespace snapshotId]}]
                                 (str namespace "/" name " snapshot " snapshotId))
                      :sort-by #(replace % [:namespace :name :snapshotId])
                      :render (fn [{:keys [name namespace snapshotId] :as config}]
                                (links/create-internal
                                 (make-config-link-props config)
                                 (style/render-name-id (str namespace "/" name) snapshotId)))}
                     {:header "Method Snapshot" :initial-width 135 :filterable? false
                      :column-data #(get-in % [:payloadObject :methodRepoMethod :methodVersion])}
                     {:header "Synopsis" :initial-width :auto
                      :column-data :synopsis}]}}])

(react/defc ConfigViewer
  {:component-will-mount
   (fn [{:keys [props state locals]}]
     (swap! locals assoc :body-id (gensym "config"))
     (swap! state dissoc :configs :configs-error)
     (let [{:keys [config-id config-snapshot-id]} props]
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/get-configuration (:namespace config-id) (:name config-id) config-snapshot-id true)
         :on-done (net/handle-ajax-response
                   (fn [{:keys [success? parsed-response]}]
                     (if success?
                       (swap! state assoc :config parsed-response)
                       (swap! state assoc :configs-error (:message parsed-response)))))})))
   :render
   (fn [{:keys [state this props]}]
     (let [{:keys [config config-error]} @state
           owner? (contains? (set (:managers config)) (utils/get-user-email))]
       [:div {:style {:margin "2.5rem 1.5rem"}}
        [:div {:style {:marginBottom "2rem"}}
         (let [{:keys [namespace name]} (:config-id props)]
           [:div {:style {:display "flex" :marginLeft (when owner? "300px")}}
            (tab-bar/render-title
             "CONFIGURATION"
             (str namespace "/" name))
            [:div {:style {:paddingLeft "2rem"}}
             (tab-bar/render-title
              "SNAPSHOT"
              (:config-snapshot-id props))]])]
        (cond
          config-error
          [:div {:style {:textAlign "center" :color (:exception-state style/colors)}}
           "Error loading config: " config-error]
          (not config)
          [:div {:style {:textAlign "center" :padding "1rem"}}
           [comps/Spinner {:text "Loading config..."}]]
          :else
          [:div {:style {:display "flex"}}
           (when owner?
             [mr-sync/SyncContainer (merge {:ref "sync-container"} config)]
             (this :-render-sidebar))
           (this :-render-main)])]))
   :-render-sidebar
   (fn [{:keys [state locals refs]}]
     (let [{:keys [config]} @state
           {:keys [body-id]} @locals]
       [:div {:style {:flex "0 0 270px" :paddingRight 30}}
        (modals/show-modals
         state
         {:deleting?
          [Redactor {:entity config :config? true :on-delete #(nav/go-to-path :method-repo)}]
          :sharing?
          [mca/AgoraPermsEditor
           {:save-endpoint (endpoints/persist-agora-entity-acl true config)
            :load-endpoint (endpoints/get-agora-entity-acl true config)
            :entityType (:entityType config)
            :entityName (mca/get-ordered-name config)
            :title (str (:entityType config) " " (mca/get-ordered-name config))
            :on-users-added #((@refs "sync-container") :check-synchronization %)}]})
        [Sticky
         {:sticky-props {:data-check-every 1
                         :data-anchor body-id}
          :contents
          [:div {:style {:width 270}}
           [comps/SidebarButton
            {:style :light :color :button-primary
             :text "Permissions..." :icon :settings :margin :bottom
             :onClick #(swap! state assoc :sharing? true)}]
           [comps/SidebarButton
            {:style :light :color :exception-state
             :text "Redact" :icon :delete :margin :bottom
             :onClick #(swap! state assoc :deleting? true)}]]}]]))
   :-render-main
   (fn [{:keys [state locals this]}]
     (let [{:keys [config exported-config-id exported-workspace-id blocking-text]} @state
           {:keys [managers method entityType payloadObject]} config
           {:keys [body-id]} @locals]
       [:div {:style {:flex "1 1 auto" :overflow "hidden"} :id body-id}
        [:div {:style {:display "flex"}}
         (style/create-summary-block (str "Config Owner" (when (> (count managers) 1) "s"))
                                     (string/join ", " managers))
         (style/create-summary-block "Designed For" (str "Method Snapshot " (:snapshotId method)))]

        (style/create-summary-block "Entity Type" entityType)

        (style/create-section-header "Connections")
        [IOView {:method-ref {:methodNamespace (:namespace method)
                              :methodName (:name method)
                              :methodVersion (:snapshotId method)}
                 :values (select-keys payloadObject [:inputs :outputs])}]

        (cond
          blocking-text
          [comps/Blocker {:banner blocking-text}]
          exported-config-id
          [modals/OKCancelForm
           {:header "Export successful"
            :content "Would you like to go to the edit page now?"
            :cancel-text "No, stay here"
            :dismiss #(swap! state dissoc :exported-workspace-id :exported-config-id)
            :ok-button
            {:text "Yes"
             :onClick #(mc-sync/flag-synchronization)
             :href (nav/get-link :workspace-method-config exported-workspace-id exported-config-id)}}])

        [mci/ConfigExporter {:entity config :perform-copy (partial this :-perform-copy)}]]))
   :-perform-copy
   (fn [{:keys [props state]} selected-workspace refs]
     (let [{:keys [workspace-id]} props
           {:keys [config]} @state
           [namespace name & fails] (input/get-and-validate refs "namespace" "name")
           workspace-id (or workspace-id
                            (select-keys (:workspace selected-workspace) [:namespace :name]))]
       (if fails
         (swap! state assoc :validation-error fails)
         (do
           (swap! state assoc :blocking-text (if (:workspace-id props) "Importing..." "Exporting..."))
           (endpoints/call-ajax-orch
            {:endpoint (endpoints/copy-method-config-to-workspace workspace-id)
             :payload {"configurationNamespace" (:namespace config)
                       "configurationName" (:name config)
                       "configurationSnapshotId" (:snapshotId config)
                       "destinationNamespace" namespace
                       "destinationName" name}
             :headers utils/content-type=json
             :on-done (fn [{:keys [success? get-parsed-response]}]
                        (swap! state dissoc :blocking-text)
                        (if success?
                          (swap! state assoc :exported-config-id {:namespace namespace :name name}
                                 :exported-workspace-id workspace-id)
                          (swap! state assoc :server-error (get-parsed-response false))))})))))})

(react/defc Configs
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [method-id snapshot-id config-id config-snapshot-id]} props
           {:keys [configs configs-error]} @state
           make-config-link-props (fn [{:keys [namespace name snapshotId]}]
                                    {:href (nav/get-link :method-config-viewer
                                                         method-id snapshot-id
                                                         {:config-ns namespace :config-name name} snapshotId)})]
       [:div {:style {:margin "2.5rem 1.5rem"}}
        (cond
          configs-error [:div {:style {:textAlign "center" :color (:exception-state style/colors)}}
                         "Error loading configs: " configs-error]
          (not configs) [:div {:style {:textAlign "center" :padding "1rem"}}
                         [comps/Spinner {:text "Loading configs..."}]]
          config-id [ConfigViewer (utils/restructure config-id config-snapshot-id)]
          :else (render-config-table (utils/restructure make-config-link-props configs)))]))
   :component-will-mount
   (fn [{:keys [props this]}]
     (when-not (:config-id props)
       (this :refresh)))
   :refresh
   (fn [{:keys [props state]}]
     (swap! state dissoc :configs :configs-error)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-agora-method-configs (:method-id props))
       :on-done (net/handle-ajax-response
                 (fn [{:keys [success? parsed-response]}]
                   (if success?
                     (let [configs (map #(assoc % :payload (utils/parse-json-string (:payload %) true)) parsed-response)]
                       (swap! state assoc :configs configs))
                     (swap! state assoc :configs-error (:message parsed-response)))))}))})
