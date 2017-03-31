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
            [comps/Button {:text "Import Configuration..."
                           :disabled? (case locked?
                                        nil "Looking up workspace status..."
                                        true "This workspace is locked."
                                        false)
                           :onClick #(modal/push-modal
                                      [comps/OKCancelForm
                                       {:header "Import Method Configuration"
                                        :content
                                        [:div {:style {:backgroundColor "white" :padding "1ex" :width 1000}}
                                         [MethodConfigImporter {:workspace-id (:workspace-id props)
                                                                :after-import (fn [{:keys [config-id]}]
                                                                                (modal/pop-modal)
                                                                                ((:on-config-imported props) config-id))}]]}])}])
           :columns
           [{:header "Name" :starting-width 240 :as-text :name :sort-by :text
             :content-renderer
             (fn [config-id]
               (style/create-link {:text (:name config-id)
                                   :href (nav/create-href (:nav-context props) config-id)}))}
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
     (let [nav-context (nav/parse-segment (:nav-context props))
           selected-method-config-id (common/get-id-from-nav-segment (:segment nav-context))]
       (if selected-method-config-id
         (nav/back nav-context)
         (react/call :reload (@refs "method-config-list")))))
   :render
   (fn [{:keys [props]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           selected-method-config-id (common/get-id-from-nav-segment (:segment nav-context))]
       [:div {:style {:padding "1rem 1.5rem"}}
        (if selected-method-config-id
          [MethodConfigEditor
           (merge (select-keys props [:workspace-id :bucket-access? :on-submission-success])
                  {:key selected-method-config-id
                   :config-id selected-method-config-id
                   :on-rename #(nav/navigate (:nav-context props) (str (:namespace selected-method-config-id) ":" %))
                   :after-delete #(nav/back nav-context)})]
          [MethodConfigurationsList
           (merge (select-keys props [:workspace-id :workspace :request-refresh])
                  {:ref "method-config-list"
                   :nav-context nav-context
                   :on-config-imported #(nav/navigate nav-context %)})])]))})
