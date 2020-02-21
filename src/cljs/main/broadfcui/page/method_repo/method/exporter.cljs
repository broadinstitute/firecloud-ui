(ns broadfcui.page.method-repo.method.exporter
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.input :as input]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.components.split-pane :refer [SplitPane]]
   [broadfcui.components.workspace-selector :refer [WorkspaceSelector]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.method-repo.method.common :as method-common]
   [broadfcui.page.method-repo.method.export-destination-form :refer [ExportDestinationForm]]
   [broadfcui.page.workspace.workspace-common :as ws-common]
   [broadfcui.net :as net]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   [broadfcui.utils.user :as user]
   ))


(react/defc- Preview
  {:render
   (fn [{:keys [state]}]
     (let [{:keys [config config-error]} @state]
       (cond config-error (style/create-server-error-message config-error)
             config [:div {:style {:padding "0.5rem 1rem" :background-color "white"}}
                     (method-common/render-config-details config)]
             :else (spinner "Loading Configuration Details..."))))
   :component-did-mount
   (fn [{:keys [props this]}]
     (this :-load (:preview-config props)))
   :component-will-receive-props
   (fn [{:keys [props next-props this]}]
     (when (not= (:preview-config props) (:preview-config next-props))
       (this :-load (:preview-config next-props))))
   :-load
   (fn [{:keys [state]} config]
     (swap! state dissoc :config :config-error)
     (let [{:keys [namespace name snapshotId]} config]
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/get-configuration namespace name snapshotId true)
         :on-done (net/handle-ajax-response
                   (fn [{:keys [success? parsed-response]}]
                     (if success?
                       (swap! state assoc :config parsed-response)
                       (swap! state assoc :config-error (:message parsed-response)))))})))})


(defn- config->id+snapshot [config]
  (assoc (select-keys config [:namespace :name]) :snapshotId (int (:snapshotId config))))


(react/defc MethodExporter
  {:get-initial-state
   (fn [{:keys [props]}]
     {:preview-config-id (config->id+snapshot (:initial-config-id props))})
   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [method-name dismiss workspace-id]} props
           {:keys [selected-config banner]} @state
           exporter [:div {}
                     (blocker banner)
                     (if selected-config
                       (this :-render-export-page)
                       (this :-render-config-selector))]]
       (if workspace-id
         [:div {} exporter (if selected-config
                             (this :-render-export-page-buttons)
                             (this :-render-config-selector-buttons))]
         [modals/OKCancelForm
          {:data-test-id "export-config-to-workspace-modal"
           :header (str "Export " method-name " to Workspace")
           :content (react/create-element exporter)
           :button-bar (if selected-config
                         (this :-render-export-page-buttons)
                         (this :-render-config-selector-buttons))
           :show-cancel? false
           :dismiss dismiss}])))
   :-render-config-selector
   (fn [{:keys [props state]}]
     (let [{:keys [preview-config]} @state]
       [:div {:style {:backgroundColor "white" :padding "1rem" :width "100vw" :maxWidth "calc(100% - 2rem)"}}
        [:div {:style {:fontSize "120%" :marginBottom "0.5rem"}}
         "Select Method Configuration"]
        [:div {:style {:maxHeight 580}}
         [SplitPane
          {:left [method-common/ConfigTable
                  {:method-id (:method-id props)
                   :snapshot-id (:selected-snapshot-id props)
                   :style {:body-row (fn [{:keys [row]}]
                                       {:borderTop style/standard-line :alignItems "baseline"
                                        :background-color (when (= (config->id+snapshot preview-config) (config->id+snapshot row))
                                                            (:tag-background style/colors))})}
                   :make-config-link-props
                   (fn [config]
                     {:onClick #(swap! state assoc :preview-config config)})
                   :on-load (fn [configs]
                              (swap! state assoc :preview-config
                                     (utils/first-matching #(= (:preview-config-id @state) (config->id+snapshot %)) configs)))}]
           :right (if preview-config
                    [Preview {:preview-config preview-config}]
                    [:div {:style {:position "relative" :backgroundColor "white" :height "100%"}}
                     (style/center {:style {:textAlign "center"}} "Select a Configuration to Preview")])
           :initial-slider-position 625
           :slider-padding "0.5rem"}]]]))
   :-render-config-selector-buttons
   (fn [{:keys [props state]}]
     (let [{:keys [preview-config]} @state]
       (flex/box {:style {:margin "-1rem" :padding "1rem" :paddingBottom (if (:workspace-id props) 0 "1rem")
                          :backgroundColor (:background-light style/colors)}}
         flex/spring
         [buttons/Button {:type :secondary :text "Use Blank Configuration"
                          :onClick #(swap! state assoc :selected-config :blank)}]
         (flex/strut "1rem")
         [buttons/Button {:text "Use Selected Configuration"
                          :disabled? (when-not preview-config "Select a configuration first")
                          :onClick #(swap! state assoc :selected-config preview-config)}])))
   :-render-export-page
   (fn [{:keys [props state locals]}]
     (let [{:keys [method-name workspace-id]} props
           {:keys [selected-config]} @state]
       (list
        [ExportDestinationForm {:ref #(swap! locals assoc :export-form %)
                                :initial-name (if (= selected-config :blank)
                                                method-name
                                                (:name selected-config))
                                :workspace-id workspace-id
                                :select-root-entity-type? (= selected-config :blank)}]
        [comps/ErrorViewer {:error (:server-error @state)}])))
   :-render-export-page-buttons
   (fn [{:keys [props state this]}]
     (flex/box {:style {:alignItems "center"}}
       (links/create-internal {:onClick #(swap! state dissoc :selected-config)}
         (flex/box {:style {:alignItems "center"}}
           (icons/render-icon {:style {:fontSize "150%" :margin "-3px 0.5rem 0 0"}} :angle-left)
           "Choose Another Configuration"))
       flex/spring
       [buttons/Button {:data-test-id "import-export-confirm-button"
                        :text (if (:workspace-id props) "Import Method" "Export to Workspace")
                        :onClick #(this :-export)}]))
   :-export
   (fn [{:keys [props state locals this]}]
     (let [{:keys [method-id]} props
           {:keys [selected-config]} @state
           {:keys [export-form]} @locals]
       (when (export-form :valid?)
         (let [form-fields (export-form :get-field-values)
               new-id (assoc (select-keys method-id [:namespace])
                        :name (:name form-fields))]
           (if (= selected-config :blank)
             (this :-create-template new-id form-fields)
             (this :-resolve-workspace (merge (:payloadObject selected-config) new-id) form-fields))))))
   :-create-template
   (fn [{:keys [props state this]} new-id form-fields]
     (swap! state assoc :banner "Creating template...")
     (let [{:keys [method-id selected-snapshot-id]} props]
       (endpoints/call-ajax-orch
        {:endpoint endpoints/create-template
         :payload {:methodNamespace (:namespace method-id)
                   :methodName (:name method-id)
                   :methodVersion (int selected-snapshot-id)}
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (if success?
                      (this :-resolve-workspace
                            (merge (get-parsed-response)
                                   new-id
                                   {:rootEntityType (:root-entity-type form-fields)})
                            form-fields)
                      (utils/multi-swap! state (assoc :server-error (get-parsed-response false))
                                               (dissoc :banner))))})))
   :-resolve-workspace
   (fn [{:keys [props state this]} config form-fields]
     (if-let [workspace-id (:workspace-id props)]
       (this :-export-loaded-config workspace-id config)
       (let [{:keys [existing-workspace new-workspace]} (:workspace form-fields)
             {:keys [project name description auth-domain]} new-workspace]
         (if existing-workspace
           (this :-export-loaded-config (ws-common/workspace->id existing-workspace) config)
           (do (swap! state assoc :banner "Creating workspace...")
               (endpoints/call-ajax-orch
                {:endpoint endpoints/create-workspace
                 :payload {:namespace project
                           :name name
                           :attributes (if (string/blank? description) {} {:description description})
                           :authorizationDomain auth-domain}
                 :headers ajax/content-type=json
                 :on-done (fn [{:keys [success? get-parsed-response]}]
                            (if success?
                              (this :-export-loaded-config {:namespace project :name name} config)
                              (utils/multi-swap! state (assoc :server-error (get-parsed-response false))
                                                       (dissoc :banner))))}))))))
   :-export-loaded-config
   (fn [{:keys [props state]} workspace-id config]
     (swap! state assoc :banner (if (:workspace-id props) "Importing..." "Exporting..."))
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/post-workspace-method-config workspace-id)
       :payload config
       :headers ajax/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    ((:on-export props) workspace-id (ws-common/config->id config))
                    (utils/multi-swap! state (assoc :server-error (get-parsed-response false))
                                             (dissoc :banner)))
                  (endpoints/send-metrics-event
                   "workflow:export:firecloud"
                   {:success success?
                    :config (not= (:selected-config @state) :blank)
                    :userId (:anonymousGroup @user/profile)}))}))}) ;; requiring user deeper causes circular dep
