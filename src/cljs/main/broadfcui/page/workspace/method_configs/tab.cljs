(ns broadfcui.page.workspace.method-configs.tab
  (:require
    [dmohs.react :as react]
    clojure.string
    [broadfcui.common.components :as comps]
    [broadfcui.common.flex-utils :as flex]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.common.table.table :refer [Table]]
    [broadfcui.common.table.style :as table-style]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.nav :as nav]
    [broadfcui.page.method-repo.method-config-importer :refer [MethodConfigImporter]]
    [broadfcui.page.workspace.method-configs.method-config-editor :refer [MethodConfigEditor]]
    [broadfcui.utils :as utils]
    ))


(defn- config->id [config]
  (select-keys config [:namespace :name]))


(react/defc MethodConfigurationsList
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
         [Table
          {:data configs
           :body {:empty-message "There are no method configurations to display."
                  :style table-style/table-heavy
                  :columns [{:header "Name" :initial-width 240
                             :column-data config->id
                             :as-text :name :sort-by :text
                             :render (fn [config-id]
                                       (style/create-link {:text (:name config-id)
                                                           :href (nav/get-link :workspace-method-config
                                                                               (:workspace-id props)
                                                                               config-id)}))}
                            {:header "Root Entity Type" :initial-width 140
                             :column-data :rootEntityType}
                            {:header "Method" :initial-width 800
                             :column-data (comp (juxt :methodNamespace :methodName :methodVersion) :methodRepoMethod)
                             :as-text (partial clojure.string/join "/")
                             :render (partial apply style/render-entity)}]}
           :toolbar {:items [flex/spring
                             [comps/Button
                              {:text "Import Configuration..."
                               :disabled? (case locked?
                                            nil "Looking up workspace status..."
                                            true "This workspace is locked."
                                            false)
                               :onClick #(modal/push-modal
                                          [comps/OKCancelForm
                                           {:header "Import Method Configuration"
                                            :content
                                            [:div {:style {:backgroundColor "white" :padding "1rem"}}
                                             [MethodConfigImporter
                                              {:workspace-id (:workspace-id props)
                                               :after-import (fn [{:keys [config-id]}]
                                                               (modal/pop-modal)
                                                               ((:on-config-imported props) config-id))}]]}])}]]}}]
         :else [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading configurations..."}]])))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load this))
   :load
   (fn [{:keys [props state]}]
     ((:request-refresh props))
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/list-workspace-method-configs (:workspace-id props))
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (if success?
                    (swap! state assoc :server-response {:configs (vec (get-parsed-response))})
                    (swap! state assoc :server-response {:error-message status-text})))}))})


(react/defc Page
  {:refresh
   (fn [{:keys [props refs]}]
     (when-not (:config-id props)
       (react/call :reload (@refs "method-config-list"))))
   :render
   (fn [{:keys [this props]}]
     (let [{:keys [config-id workspace-id]} props]
       [:div {:style {:padding "1rem 1.5rem"}}
        (if config-id
          [MethodConfigEditor
           (merge (select-keys props [:workspace-id :bucket-access? :on-submission-success])
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
                   :on-config-imported #(nav/go-to-path :workspace-method-config
                                                        workspace-id
                                                        %)})])]))})
