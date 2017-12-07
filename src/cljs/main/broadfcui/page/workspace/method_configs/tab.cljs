(ns broadfcui.page.workspace.method-configs.tab
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.page.workspace.method-configs.import-config :as import-config]
   [broadfcui.page.workspace.method-configs.method-config-editor :refer [MethodConfigEditor]]
   [broadfcui.page.workspace.method-configs.synchronize :as mc-sync]
   [broadfcui.page.workspace.workspace-common :as ws-common]
   [broadfcui.utils :as utils]
   ))

(defn- add-redacted-attribute [config methods]
  (let [methodRepoMethod (:methodRepoMethod config)
        {:keys [methodName methodNamespace methodVersion]} methodRepoMethod
        snapshots (set (get methods [methodNamespace methodName]))]
    (if (contains? snapshots methodVersion)
      (assoc config :redacted false)
      (assoc config :redacted true))))

(react/defc- MethodConfigurationsList
  {:reload
   (fn [{:keys [state this props]}]
     (swap! state dissoc :server-response)
     ((:request-refresh props))
     (this :load))
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [server-response methods importing?]} @state
           {:keys [configs error-message]} server-response
           locked? (get-in props [:workspace :workspace :isLocked])]
       [:div {}
        (when importing?
          [import-config/ConfigImporter
           {:workspace-id (:workspace-id props)
            :data-test-id "import-method-configuration-modal"
            :dismiss #(swap! state dissoc :importing?)
            :after-import (fn [{:keys [config-id]}]
                            (mc-sync/flag-synchronization)
                            ((:on-config-imported props) config-id))}])
        (cond
          error-message (style/create-server-error-message error-message)
          (and configs methods)
          (ws-common/method-config-selector
           {:configs (map #(add-redacted-attribute % methods) configs)
            :render-name (fn [config]
                           (links/create-internal
                            {:data-test-id (str "method-config-" (:name config) "-link")
                             :href (nav/get-link :workspace-method-config
                                                 (:workspace-id props)
                                                 (ws-common/config->id config))}
                            (:name config)))
            :toolbar-items
            [flex/spring
             [buttons/Button
              {:data-test-id "import-config-button"
               :text "Import Configuration..."
               :disabled? (cond
                            (nil? locked?) "Looking up workspace status..."
                            locked? "This workspace is locked."
                            (not (common/access-greater-than-equal-to? (get-in props [:workspace :accessLevel]) "WRITER"))
                            "You do not have access to Import Configurations.")

               :onClick #(swap! state assoc :importing? true)}]]})
          :else [:div {:style {:textAlign "center"}}
                 (spinner "Loading configurations...")])]))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :load))
   :load
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint endpoints/list-methods
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (when success?
                    (swap! state assoc :methods (->> (get-parsed-response)
                                                     (map #(select-keys % [:namespace :name :snapshotId]))
                                                     (group-by (juxt :namespace :name))
                                                     (utils/map-values (partial map :snapshotId))))))})
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/list-workspace-method-configs (:workspace-id props))
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (swap! state assoc :server-response
                         (if success? {:configs (vec (get-parsed-response))}
                                      {:error-message status-text})))}))})


(react/defc Page
  {:refresh
   (fn [{:keys [props refs]}]
     (when-not (:config-id props)
       ((@refs "method-config-list") :reload)))
   :render
   (fn [{:keys [props]}]
     (let [{:keys [config-id workspace-id]} props]
       [:div {:style {:padding "1rem 1.5rem"}}
        (if config-id
          [MethodConfigEditor
           (merge (select-keys props [:workspace-id :workspace :bucket-access? :on-submission-success])
                  {:key config-id
                   :config-id config-id
                   :access-level (get-in props [:workspace :accessLevel])
                   :on-rename #(nav/go-to-path :workspace-method-config
                                               workspace-id
                                               (assoc config-id :name %))
                   :after-delete #(nav/go-to-path :workspace-method-configs workspace-id)})]
          [MethodConfigurationsList
           (merge (select-keys props [:workspace-id :workspace :request-refresh])
                  {:ref "method-config-list"
                   :on-config-imported #(nav/go-to-path :workspace-method-config workspace-id %)})])]))})
