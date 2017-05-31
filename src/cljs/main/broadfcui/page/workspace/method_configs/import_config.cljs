(ns broadfcui.page.workspace.method-configs.import-config
  (:require
    [dmohs.react :as react]
    [broadfcui.common.components :as comps]
    [broadfcui.common.style :as style]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.page.method-repo.method-config-importer :refer [MethodConfigImporter]]
    [broadfcui.page.workspace.workspace-common :as ws-common]
    [broadfcui.utils :as utils]
    ))


(defn- white-wrap [component]
  [:div {:style {:backgroundColor "white" :padding "1rem"}}
   component])


(react/defc ConfigChooser
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [push-page workspace-id]} props
           server-response (:server-response @state)
           {:keys [configs error-message]} server-response]
       (white-wrap
        (cond
          error-message (style/create-server-error-message error-message)
          configs
          [:div {}
           [:div {:style {:fontSize "125%" :marginBottom "0.5rem"}}
            (str "Method Configurations in " (:namespace workspace-id) "/" (:name workspace-id) ":")]
           (ws-common/method-config-selector
            {:configs configs
             :render-name (fn [config-id]
                            (style/create-link
                             {:text (:name config-id)
                              :onClick #(push-page {:breadcrumb-text "Confirm"
                                                    :component [:div {} "Confirm stuff"]})}))})]
          :else [:div {:style {:textAlign "center"}}
                 [comps/Spinner {:text "Loading configurations..."}]]))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/list-workspace-method-configs (:workspace-id props))
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
                        (push-page {:breadcrumb-text "Choose Method Configuration"
                                    :component [ConfigChooser (assoc props :workspace-id (ws-common/workspace->id ws))]}))
                      :toolbar-items
                      (when (pos? removed-count)
                        [(str removed-count
                              " workspace"
                              (when (> removed-count 1) "s")
                              " hidden due to permissions")])})))))
   :component-did-mount
   (fn [{:keys [props]}]
     ((:load-workspaces props)))})


(defn- source-chooser [{:keys [push-page] :as props}]
  [:div {}
   [comps/Button {:text "Copy from another Workspace"
                  :onClick #(push-page {:breadcrumb-text "Choose Workspace"
                                        :component [WorkspaceChooser props]})
                  :style {:marginRight "1rem"}}]
   [comps/Button {:text "Import from Method Repository"
                  :onClick #(push-page {:breadcrumb-text "Choose Method"
                                        :component (white-wrap [MethodConfigImporter props])})}]])


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
