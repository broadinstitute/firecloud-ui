(ns broadfcui.page.workspace.method-configs.import-config
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.input :as input]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.entity-details :refer [EntityDetails]]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.method-repo.method-repo-table :refer [MethodRepoTable]]
   [broadfcui.page.workspace.workspace-common :as ws-common]
   [broadfcui.utils :as utils]
   ))


(defn- wrap [& components]
  [:div {:style {:backgroundColor "white" :padding "1rem" :width "100vw" :maxWidth "calc(100% - 2rem)"}}
   components])

(defn- id->str [id]
  (str (:namespace id) "/" (:name id)))


(react/defc- ConfirmWorkspaceConfig
  {:render
   (fn [{:keys [state this]}]
     (let [{:keys [loaded-config config-load-error loaded-method method-load-error import-error]} @state]
       (wrap
        (cond config-load-error (style/create-server-error-message config-load-error)
              (not loaded-config) (spinner "Loading configuration details...")
              :else
              [:div {}
               [:div {:style {:paddingBottom "0.5rem"}}
                "Root Entity Type: "
                [:strong {} (:rootEntityType loaded-config)]]
               [:div {:style {:fontSize "120%" :marginBottom "0.5rem"}}
                "Referenced Method:"]
               (cond loaded-method [EntityDetails {:entity loaded-method}]
                     method-load-error (style/create-server-error-message
                                        (if (= method-load-error "Not Found")
                                          "The referenced method snapshot could not be found.
                                          It may have been redacted, or you may not have access to it."
                                          method-load-error))
                     :else (spinner "Loading method details..."))
               [:div {:style {:fontSize "120%" :margin "1rem 0 0.5rem"}}
                "Import as:"]
               [:div {:style {:float "left" :marginRight "0.5rem"}}
                (style/create-form-label "Namespace")
                [input/TextField {:ref "namespace"
                                  :defaultValue (:namespace loaded-config)
                                  :placeholder "Required"
                                  :predicates [(input/nonempty "Namespace")]}]]
               [:div {:style {:float "left"}}
                (style/create-form-label "Name")
                [input/TextField {:ref "name"
                                  :defaultValue (:name loaded-config)
                                  :placeholder "Required"
                                  :predicates [(input/nonempty "Name")]}]]
               (common/clear-both)
               (style/create-validation-error-message (:validation-error @state))
               [comps/ErrorViewer {:error import-error}]
               (when (and loaded-config loaded-method)
                 [buttons/Button {:text "Import"
                                  :onClick #(this :-import)}])]))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-workspace-method-config (:chosen-workspace-id props) (:config props))
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (if success?
                    (swap! state assoc :loaded-config (get-parsed-response))
                    (swap! state assoc :config-load-error status-text)))})
     (let [{:keys [methodNamespace methodName methodVersion]} (:methodRepoMethod (:config props))]
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/get-agora-method methodNamespace methodName methodVersion)
         :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                    (if success?
                      (swap! state assoc :loaded-method (get-parsed-response))
                      (swap! state assoc :method-load-error status-text)))})))
   :-import
   (fn [{:keys [props state refs]}]
     (let [[namespace name & fails] (input/get-and-validate refs "namespace" "name")
           {:keys [workspace-id]} props
           {:keys [loaded-config]} @state
           updated-config (assoc loaded-config :namespace namespace :name name)]
       (if fails
         (swap! state assoc :validation-error fails)
         (do
           (swap! state dissoc :import-error)
           (endpoints/call-ajax-orch
            {:endpoint (endpoints/post-workspace-method-config workspace-id)
             :payload updated-config
             :headers utils/content-type=json
             :on-done (fn [{:keys [success? get-parsed-response]}]
                        (if success?
                          (do
                            ((:dismiss props))
                            ((:after-import props) {:config-id (ws-common/config->id updated-config)}))
                          (swap! state assoc :import-error (get-parsed-response false))))})))))})


(react/defc- WorkspaceConfigChooser
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [push-page]} props
           {:keys [server-response]} @state
           {:keys [configs error-message]} server-response]
       (wrap
        (cond error-message (style/create-server-error-message error-message)
              configs
              (ws-common/method-config-selector
               {:configs configs
                :render-name
                (fn [config]
                  (links/create-internal
                   {:onClick #(push-page {:breadcrumb-text (id->str config)
                                          :component [ConfirmWorkspaceConfig
                                                      (assoc props :config config)]})}
                   (:name config)))})
              :else (spinner "Loading configurations...")))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/list-workspace-method-configs (:chosen-workspace-id props))
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (swap! state assoc :server-response
                         (if success? {:configs (vec (get-parsed-response))}
                                      {:error-message status-text})))}))})


(react/defc- WorkspaceChooser
  {:render
   (fn [{:keys [props]}]
     (let [{:keys [get-workspaces push-page]} props
           {:keys [result removed-count error]} (get-workspaces)]
       (wrap
        (cond error (style/create-server-error-message error)
              (nil? result) (spinner "Loading workspaces...")
              :else (ws-common/workspace-selector
                     {:workspaces result
                      :on-workspace-selected
                      (fn [ws]
                        (let [id (ws-common/workspace->id ws)]
                          (push-page {:breadcrumb-text (id->str id)
                                      :component [WorkspaceConfigChooser
                                                  (assoc props :chosen-workspace-id id)]})))
                      :toolbar-items (when (pos? removed-count)
                                       [(str removed-count
                                             " workspace"
                                             (when (> removed-count 1) "s")
                                             " hidden due to permissions")])})))))
   :component-did-mount
   (fn [{:keys [props]}]
     ((:load-workspaces props)))})


(defn- method-chooser [{:keys [push-page replace-page workspace-id]}]
  (let [nav-method (fn [{:keys [replace? label component props]}]
                     ((if replace? replace-page push-page)
                      {:breadcrumb-text label
                       :component [component props]}))]
    [MethodRepoTable
     {:nav-method nav-method
      :workspace-id workspace-id}]))


(defn- source-chooser [{:keys [push-page] :as props}]
  (flex/box
   {:style {:justifyContent "center"}}
   [buttons/Button {:data-test-id "import-from-repo-button"
                    :text "Import from Method Repository"
                    :onClick #(push-page {:breadcrumb-text "Method Repository"
                                          :component (wrap (method-chooser props))})
                    :style {:marginRight "1rem"}}]
   [buttons/Button {:data-test-id "copy-from-workspace-button"
                    :text "Copy from another Workspace"
                    :onClick #(push-page {:breadcrumb-text "Choose Workspace"
                                          :component [WorkspaceChooser props]})}]))


(defn- filter-workspaces [workspaces]
  (remove (comp (partial = "NO ACCESS") :accessLevel) workspaces))

(react/defc ConfigImporter
  {:get-initial-state
   (fn [{:keys [props state this]}]
     (let [push-page #(swap! state update :pages conj %)
           replace-page #(swap! state update :pages utils/replace-top %)
           load-workspaces #(this :-load-workspaces)
           get-workspaces #(this :-get-workspaces)]
       {:pages [{:breadcrumb-text "Choose Source"
                 :component (source-chooser
                             (merge props (utils/restructure push-page replace-page load-workspaces get-workspaces)))}]}))
   :render
   (fn [{:keys [props state this]}]
     [modals/OKCancelForm
      {:header "Import Method Configuration"
       :show-cancel? false
       :dismiss (:dismiss props)
       :content
       [:div {}
        [:div {:style {:marginBottom "1rem"}}
         (this :-build-breadcrumbs)]
        (wrap
         (-> (:pages @state) last :component))]}])
   :component-did-mount
   (fn [{:keys [locals]}]
     (swap! locals assoc :workspaces-status :not-requested))
   :-build-breadcrumbs
   (fn [{:keys [state]}]
     [comps/Breadcrumbs
      {:crumbs (map-indexed (fn [index {:keys [breadcrumb-text]}]
                              {:text breadcrumb-text
                               :onClick #(swap! state update :pages subvec 0 (inc index))})
                            (:pages @state))}])
   :-load-workspaces
   (fn [{:keys [state locals]}]
     (when (= (:workspaces-status @locals) :not-requested)
       (swap! locals assoc :workspaces-status :requested)
       (endpoints/call-ajax-orch
        {:endpoint endpoints/list-workspaces
         :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                    (swap! locals assoc :workspaces-status :loaded)
                    (if success?
                      (let [workspaces (get-parsed-response)
                            filtered (filter-workspaces workspaces)
                            removed-count (- (count workspaces) (count filtered))]
                        (swap! state update :workspaces assoc
                               :result filtered
                               :removed-count removed-count))
                      (swap! state update :workspaces assoc :error status-text)))})))
   :-get-workspaces
   (fn [{:keys [state]}]
     (:workspaces @state))})
