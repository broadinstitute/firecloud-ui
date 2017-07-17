(ns broadfcui.page.workspace.method-configs.tab
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.page.workspace.method-configs.import-config :as import-config]
   [broadfcui.page.workspace.method-configs.method-config-editor :refer [MethodConfigEditor]]
   [broadfcui.page.workspace.method-configs.synchronize :as sync]
    [broadfcui.page.workspace.workspace-common :as ws-common]
   [broadfcui.utils :as utils]
   ))


(react/defc- MethodConfigurationsList
  {:reload
   (fn [{:keys [state this]}]
     (swap! state dissoc :server-response)
     (react/call :load this))
   :render
   (fn [{:keys [props state]}]
     (let [server-response (:server-response @state)
           {:keys [configs error-message]} server-response
           locked? (get-in props [:workspace :workspace :isLocked])]
       (cond
         error-message (style/create-server-error-message error-message)
         configs
         (ws-common/method-config-selector
          {:configs configs
           :render-name (fn [config]
                          (style/create-link {:text (:name config)
                                              :href (nav/get-link :workspace-method-config
                                                                  (:workspace-id props)
                                                                  (ws-common/config->id config))}))
           :toolbar-items
           [flex/spring
            [comps/Button
             {:text "Import Configuration..."
              :disabled? (case locked?
                           nil "Looking up workspace status..."
                           true "This workspace is locked."
                           false)
              :onClick #(modal/push-modal
                         [import-config/ConfigImporter
                          {:workspace-id (:workspace-id props)
                           :after-import (fn [{:keys [config-id]}]
                                           (modal/pop-modal)
                                           (sync/flag-synchronization)
                                           ((:on-config-imported props) config-id))}])}]]})
         :else [:div {:style {:textAlign "center"}}
                [comps/Spinner {:text "Loading configurations..."}]])))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load this))
   :load
   (fn [{:keys [props state]}]
     ((:request-refresh props))
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
