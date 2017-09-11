(ns broadfcui.page.workspace.method-configs.tab
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.links :as links]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.config :as config]
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
     (react/call :load this))
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [server-response methods]} @state
           {:keys [configs error-message]} server-response
           locked? (get-in props [:workspace :workspace :isLocked])]
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
            [comps/Button
             {:text "Import Configuration..."
              :disabled? (case locked?
                           nil "Looking up workspace status..."
                           true "This workspace is locked."
                           false)
              :data-test-id "import-config-button"
              :onClick #(modal/push-modal
                         [import-config/ConfigImporter
                          {:workspace-id (:workspace-id props)
                           :data-test-id "import-method-configuration-modal"
                           :after-import (fn [{:keys [config-id]}]
                                           (modal/pop-modal)
                                           (mc-sync/flag-synchronization)
                                           ((:on-config-imported props) config-id))}])}]]})
         :else [:div {:style {:textAlign "center"}}
                [comps/Spinner {:text "Loading configurations..."}]])))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load this))
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
       (react/call :reload (@refs "method-config-list"))))
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
