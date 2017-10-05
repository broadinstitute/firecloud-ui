(ns broadfcui.page.method-repo.method.exporter
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.input :as input]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.split-pane :refer [SplitPane]]
   [broadfcui.components.workspace-selector :refer [WorkspaceSelector]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.method-repo.method.common :as method-common]
   [broadfcui.page.workspace.workspace-common :as ws-common]
   [broadfcui.net :as net]
   [broadfcui.utils :as utils]
   ))


(react/defc- Preview
  {:render
   (fn [{:keys [state]}]
     (let [{:keys [config config-error]} @state]
       (cond config-error (style/create-server-error-message config-error)
             config [:div {:style {:padding "0.5rem 1rem" :background-color "white"}}
                     (method-common/render-config-details config)]
             :else [comps/Spinner {:text "Loading Configuration Details..."}])))
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
           {:keys [configs configs-error selected-config banner]} @state
           exporter [:div {}
                     (when banner
                       [comps/Blocker {:banner banner}])
                     (cond configs-error (style/create-server-error-message configs-error)
                           selected-config (this :-render-export-page)
                           configs (this :-render-config-selector)
                           :else [comps/Spinner {:text "Loading Method Configurations..."}])]]
       (if workspace-id
         [:div {} exporter (cond selected-config (this :-render-export-page-buttons)
                                 configs (this :-render-config-selector-buttons))]
         [modals/OKCancelForm
          {:header (str "Export " method-name " to Workspace")
           :content (react/create-element exporter)
           :button-bar (cond selected-config (this :-render-export-page-buttons)
                             configs (this :-render-config-selector-buttons))
           :show-cancel? false
           :dismiss dismiss}])))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-agora-compatible-configs (assoc (:method-id props) :snapshot-id (:selected-snapshot-id props)))
       :on-done (net/handle-ajax-response
                 (fn [{:keys [success? parsed-response]}]
                   (if success?
                     (let [configs (map #(assoc % :payload (utils/parse-json-string (:payload %) true)) parsed-response)]
                       (swap! state assoc :configs configs :preview-config (utils/first-matching #(= (:preview-config-id @state) (config->id+snapshot %)) configs)))
                     (swap! state assoc :configs-error (:message parsed-response)))))}))
   :-render-config-selector
   (fn [{:keys [state]}]
     (let [{:keys [configs preview-config]} @state]
       [:div {:style {:backgroundColor "white" :padding "1rem" :width "100vw" :maxWidth "calc(100% - 2rem)"}}
        [:div {:style {:fontSize "120%" :marginBottom "0.5rem"}}
         "Select Method Configuration"]
        [:div {:style {:maxHeight 580}}
         [SplitPane
          {:left (method-common/render-config-table
                  {:configs configs
                   :style {:body-row (fn [{:keys [row]}]
                                       {:borderTop style/standard-line :alignItems "baseline"
                                        :background-color (when (= (config->id+snapshot preview-config) (config->id+snapshot row))
                                                            (:tag-background style/colors))})}
                   :make-config-link-props
                   (fn [config]
                     {:onClick #(swap! state assoc :preview-config config)})})
           :right (if preview-config
                    [Preview {:preview-config preview-config}]
                    [:div {:style {:position "relative" :backgroundColor "white" :height "100%"}}
                     (style/center {:style {:textAlign "center"}} "Select a Configuration to Preview")])
           :initial-slider-position 625
           :slider-padding "0.5rem"}]]]))
   :-render-config-selector-buttons
   (fn [{:keys [props state]}]
     (let [{:keys [preview-config]} @state]
       (flex/box
        {:style {:margin "-1rem" :padding "1rem" :paddingBottom (if (:workspace-id props) 0 "1rem")
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
       [:div {:style {:width 550}}
        (style/create-form-label "Name")
        [input/TextField {:ref "name-field"
                          :style {:width "100%"}
                          :defaultValue (if (= selected-config :blank)
                                          method-name
                                          (:name selected-config))
                          :predicates [(input/nonempty "Name")]}]
        (when (= selected-config :blank)
          (list
           (style/create-form-label "Root Entity Type")
           (style/create-identity-select {:ref "root-entity-type"}
                                         common/root-entity-types)))
        (when-not workspace-id
          (list
           (style/create-form-label "Destination Workspace")
           [WorkspaceSelector {:style {:width "100%"}
                               :filter #(common/access-greater-than-equal-to? (:accessLevel %) "WRITER")
                               :on-select #(swap! locals assoc :selected-workspace-id (ws-common/workspace->id %))}]))
        [:div {:style {:padding "0.5rem"}}] ;; select2 is eating any padding/margin I give to WorkspaceSelector
        (style/create-validation-error-message (:validation-errors @state))
        [comps/ErrorViewer {:error (:server-error @state)}]]))
   :-render-export-page-buttons
   (fn [{:keys [props state this]}]
     (flex/box
      {:style {:alignItems "center"}}
      (links/create-internal
       {:onClick #(swap! state dissoc :selected-config)}
       (flex/box
        {:style {:alignItems "center"}}
        (icons/icon {:style {:fontSize "150%" :marginRight "0.5rem"}} :angle-left)
        "Choose Another Configuration"))
      flex/spring
      [buttons/Button {:text (if (:workspace-id props) "Import Method" "Export to Workspace")
                       :onClick #(this :-export)}]))
   :-export
   (fn [{:keys [props state refs this]}]
     (swap! state assoc :validation-errors nil :banner "Resolving...")
     (let [[name & errors] (input/get-and-validate refs "name-field")
           new-id (assoc (select-keys (:method-id props) [:namespace])
                    :name name)
           {:keys [selected-config]} @state]
       (cond errors (swap! state assoc :validation-errors errors)
             (= :blank selected-config) (this :-create-template new-id)
             :else (this :-export-loaded-config (merge (:payloadObject selected-config) new-id)))))
   :-create-template
   (fn [{:keys [props state refs this]} new-id]
     (swap! state assoc :banner "Creating template...")
     (let [{:keys [method-id selected-snapshot-id]} props
           dest-ret (.-value (@refs "root-entity-type"))]
       (endpoints/call-ajax-orch
        {:endpoint endpoints/create-template
         :payload {:methodNamespace (:namespace method-id)
                   :methodName (:name method-id)
                   :methodVersion (int selected-snapshot-id)}
         :headers utils/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (if success?
                      (this :-export-loaded-config
                            (merge (get-parsed-response) new-id {:rootEntityType dest-ret}))
                      (swap! state assoc :banner nil :server-error (get-parsed-response false))))})))
   :-export-loaded-config
   (fn [{:keys [props state locals]} config]
     (assert (some? (:rootEntityType config)) "Trying to send a config ID where a config is required")
     (swap! state assoc :banner (if (:workspace-id props) "Importing..." "Exporting..."))
     (let [workspace-id (or (:workspace-id props) (:selected-workspace-id @locals))]
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/post-workspace-method-config workspace-id)
         :payload config
         :headers utils/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (if success?
                      ((:on-export props) workspace-id (ws-common/config->id config))
                      (swap! state assoc :banner nil :server-error (get-parsed-response false))))})))})
