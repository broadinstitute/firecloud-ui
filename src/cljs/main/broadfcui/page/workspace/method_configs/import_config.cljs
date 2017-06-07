(ns broadfcui.page.workspace.method-configs.import-config
  (:require
    [dmohs.react :as react]
    [broadfcui.common.components :as comps]
    [broadfcui.common.style :as style]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.nav :as nav]
    [broadfcui.page.method-repo.method-repo-table :refer [MethodRepoTable]]
    [broadfcui.page.workspace.workspace-common :as ws-common]
    [broadfcui.utils :as utils]
    [broadfcui.common.modal :as modal]))


(defn- white-wrap [component]
  [:div {:style {:backgroundColor "white" :padding "1rem"}}
   component])

(defn- id->str [id]
  (str (:namespace id) "/" (:name id)))


(react/defc ConfirmWorkspaceConfig
  {:render
   (fn [{:keys [state this]}]
     (let [{:keys [server-response import-error]} @state
           {:keys [config error-message]} server-response]
       (cond error-message (style/create-server-error-message error-message)
             config [:div {}
                     [:div {} "Confirm config"]
                     [comps/ErrorViewer {:error import-error}]
                     [:div {:style {:textAlign "center"}}
                      [comps/Button {:text "Import"
                                     :onClick #(this :-import)}]]]
             :else [:div {:style {:textAlign "center"}}
                    [comps/Spinner {:text "Loading configuration details..."}]])))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-workspace-method-config (:chosen-workspace-id props) (:config props))
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (swap! state assoc :server-response
                         (if success? {:config (get-parsed-response)}
                                      {:error-message status-text})))}))
   :-import
   (fn [{:keys [props state]}]
     (let [{:keys [workspace-id]} props
           {:keys [server-response]} @state
           {:keys [config]} server-response]
       (swap! state dissoc :import-error)
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/post-workspace-method-config workspace-id)
         :payload config
         :headers utils/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (if success?
                      (do (modal/pop-modal)
                          (nav/go-to-path :workspace-method-config workspace-id (ws-common/config->id config)))
                      (swap! state assoc :import-error (get-parsed-response false))))})))})


(react/defc WorkspaceConfigChooser
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [push-page]} props
           {:keys [server-response]} @state
           {:keys [configs error-message]} server-response]
       (white-wrap
        (cond error-message (style/create-server-error-message error-message)
              configs
              (ws-common/method-config-selector
               {:configs configs
                :render-name
                (fn [config]
                  (style/create-link
                   {:text (:name config)
                    :onClick #(push-page {:breadcrumb-text (id->str config)
                                          :component [ConfirmWorkspaceConfig
                                                      (assoc props :config config)]})}))})
              :else [:div {:style {:textAlign "center"}}
                     [comps/Spinner {:text "Loading configurations..."}]]))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/list-workspace-method-configs (:chosen-workspace-id props))
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (swap! state assoc :server-response
                         (if success? {:configs (vec (get-parsed-response))}
                                      {:error-message status-text})))}))})


(react/defc WorkspaceChooser
  {:render
   (fn [{:keys [props]}]
     (let [{:keys [get-workspaces push-page]} props
           {:keys [result removed-count error]} (get-workspaces)]
       (white-wrap
        (cond error (style/create-server-error-message error)
              (nil? result) [comps/Spinner {:text "Loading workspaces..."}]
              :else (ws-common/workspace-selector
                     {:workspaces result
                      :on-workspace-selected
                      (fn [ws]
                        (let [id (ws-common/workspace->id ws)]
                          (push-page {:breadcrumb-text (id->str id)
                                      :component [WorkspaceConfigChooser
                                                  (assoc props :chosen-workspace-id id)]})))
                      :toolbar-items
                      (when (pos? removed-count)
                        [(str removed-count
                              " workspace"
                              (when (> removed-count 1) "s")
                              " hidden due to permissions")])})))))
   :component-did-mount
   (fn [{:keys [props]}]
     ((:load-workspaces props)))})


(react/defc ConfirmEntity
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [loaded-entity error]} @state]
       (white-wrap
        (cond error (style/create-server-error-message error)
              (nil? loaded-entity) [comps/Spinner {:text "Loading..."}]
              :else [comps/EntityDetails {:entity loaded-entity}]))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (let [{:keys [namespace name snapshot-id]} (:id props)]
       (endpoints/call-ajax-orch
        {:endpoint ((case (:type props)
                      :method-config endpoints/get-configuration
                      :method endpoints/get-agora-method)
                    namespace name snapshot-id)
         :headers utils/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                    (if success?
                      (swap! state assoc :loaded-entity (get-parsed-response))
                      (swap! state assoc :error status-text)))})))})


(defn- method-chooser [{:keys [push-page] :as props}]
  [MethodRepoTable
   {:render-name
    (fn [{:keys [namespace name snapshotId entityType]}]
      (let [id {:namespace namespace
                :name name
                :snapshot-id snapshotId}
            type (if (= entityType "Configuration") :method-config :method)]
        (style/create-link
         {:text (style/render-name-id name snapshotId)
          :onClick #(push-page {:breadcrumb-text (style/render-entity namespace name snapshotId)
                                :component [ConfirmEntity (assoc props :type type :id id)]})})))}])


(defn- source-chooser [{:keys [push-page] :as props}]
  [:div {}
   [comps/Button {:text "Copy from another Workspace"
                  :onClick #(push-page {:breadcrumb-text "Choose Workspace"
                                        :component [WorkspaceChooser props]})
                  :style {:marginRight "1rem"}}]
   [comps/Button {:text "Import from Method Repository"
                  :onClick #(push-page {:breadcrumb-text "Method Repository"
                                        :component (white-wrap (method-chooser props))})}]])


(defn- filter-workspaces [workspaces]
  (remove (comp (partial = "NO ACCESS") :accessLevel) workspaces))

(react/defc ConfigImporter
  {:get-initial-state
   (fn [{:keys [props state this]}]
     (let [push-page #(swap! state update :pages conj %)
           load-workspaces #(this :-load-workspaces)
           get-workspaces #(this :-get-workspaces)]
       {:pages [{:breadcrumb-text "Choose Source"
                 :component (source-chooser
                             (merge props (utils/restructure push-page load-workspaces get-workspaces)))}]}))
   :render
   (fn [{:keys [state this]}]
     [comps/OKCancelForm
      {:header "Import Method Configuration"
       :show-cancel? false
       :content
       [:div {}
        [:div {:style {:marginBottom "1rem"}}
         (this :-build-breadcrumbs)]
        (-> (:pages @state) last :component)]}])
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
