(ns broadfcui.page.workspace.method-configs.tab
  (:require
    [dmohs.react :as react]
    clojure.string
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.common.table :as table]
    [broadfcui.common.table-utils :refer [add-right]]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.nav :as nav]
    [broadfcui.page.method-repo.method-config-importer :refer [MethodConfigImporter]]
    [broadfcui.page.workspace.method-configs.method-config-editor :refer [MethodConfigEditor]]
    [broadfcui.utils :as utils]
    ))


(defn- config->id [config]
  {:namespace (config "namespace") :name (config "name")})


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
         [table/Table
          {:empty-message "There are no method configurations to display."
           :toolbar
           (add-right
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
                           [:div {:style {:backgroundColor "white" :padding "1ex" :width 1000}}
                            [MethodConfigImporter
                             {:workspace-id (:workspace-id props)
                              :after-import (fn [{:keys [config-id]}]
                                              (modal/pop-modal)
                                              ((:on-config-imported props) config-id))}]]}])}])
           :columns
           [{:header "Name" :starting-width 240 :as-text :name :sort-by :text
             :content-renderer
             (fn [config-id]
               (style/create-link {:text (:name config-id)
                                   :href (nav/get-link :workspace-method-config
                                                       (:workspace-id props)
                                                       config-id)}))}
            {:header "Root Entity Type" :starting-width 140}
            {:header "Method" :starting-width 800
             :content-renderer (fn [fields] (apply style/render-entity fields))}]
           :data configs
           :->row (fn [config]
                    [(config->id config)
                     (config "rootEntityType")
                     (mapv #(get-in config ["methodRepoMethod" %])
                           ["methodNamespace" "methodName" "methodVersion"])])}]
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
                    (swap! state assoc :server-response {:configs (vec (get-parsed-response false))})
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
