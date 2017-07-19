(ns broadfcui.page.workspace.method-configs.method-config-editor
  (:require
    [dmohs.react :as react]
    [clojure.string :as string]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.page.workspace.method-configs.delete-config :as delete]
    [broadfcui.page.workspace.method-configs.launch-analysis :as launch]
    [broadfcui.page.workspace.method-configs.publish :as publish]
    [broadfcui.page.workspace.workspace-common :as ws-common]
    [broadfcui.utils :as utils]
    ))

(defn- filter-empty [coll]
  (->> coll (map string/trim) (remove string/blank?) vec))

(defn- create-section-header [text]
  [:div {:style {:fontSize "125%" :fontWeight 500}} text])

(defn- create-section [& children]
  [:div {:style {:padding "1em 0 2em 0"}} children])


(react/defc MethodDetailsViewer
  {:get-fields
   (fn [{:keys [refs]}]
     ((@refs "methodDetails") :get-fields))
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [loaded-method error]} @state]
       (cond loaded-method [comps/EntityDetails
                            (merge {:ref "methodDetails"
                                    :snapshots (get (:methods props)
                                                    (replace loaded-method [:namespace :name]))
                                    :entity loaded-method}
                                   (select-keys props [:editing? :wdl-parse-error :onSnapshotIdChange]))]
             error (style/create-server-error-message error)
             :else [comps/Spinner {:text "Loading details..."}])))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :load-agora-method))
   :load-agora-method
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-agora-method (:namespace props) (:name props) (:snapshotId props))
       :headers utils/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (if success?
                    (swap! state assoc :loaded-method (get-parsed-response))
                    (swap! state assoc :error status-text)))}))})


(defn- input-output-list [{:keys [values ref-prefix invalid-values editing? all-values]}]
  (create-section
   [:div {}
    (map
     (fn [{:keys [name inputType outputType optional]}]
       (let [type (or inputType outputType)
             name-kwd (keyword name)
             field-value (get values name-kwd "")
             error (get invalid-values name-kwd)]
         [:div {:key name :style {:marginBottom "1rem"}}
          [:div {}
           [:div {:style {:margin "0 0.5rem 0.5rem 0" :padding "0.5rem" :display "inline-block"
                          :backgroundColor (:background-light style/colors)
                          :border style/standard-line :borderRadius 2}}
            (str name ": (" (when optional "optional ") type ")")]
           (when (and error (not editing?))
             (icons/icon {:style {:marginLeft "0.5rem" :alignSelf "center"
                                  :color (:exception-state style/colors)}}
                         :error))
           (when editing?
             (style/create-text-field {:ref (str ref-prefix "_" name)
                                       :list "inputs-datalist"
                                       :data-test-id (str name "-text-input")
                                       :defaultValue field-value
                                       :style {:width 500}}))
           (when-not editing?
             (or field-value [:span {:style {:fontStyle "italic"}} "No value entered"]))]
          (when error
            [:div {:style {:padding "0.5em" :marginBottom "0.5rem"
                           :backgroundColor (:exception-state style/colors)
                           :display "inline-block"
                           :border style/standard-line :borderRadius 2}}
             error])]))
     all-values)]))


(react/defc MethodConfigEditor
  {:get-initial-state
   (fn []
     {:editing? false
      :sidebar-visible? true})
   :render
   (fn [{:keys [state this]}]
     (cond (and (every? @state [:loaded-config :methods])
                (contains? @state :locked?))
           (this :-render-display)

           (:error @state) (style/create-server-error-message (:error @state))
           :else [:div {:style {:textAlign "center"}}
                  [comps/Spinner {:text "Loading Method Configuration..."}]]))
   :-render-display
   (fn [{:keys [props state this]}]
     (let [{:keys [loaded-config editing?]} @state
           config (:methodConfiguration loaded-config)]
       [:div {}
        [comps/Blocker {:banner (:blocker @state)}]
        [:div {:style {:padding "1em 2em"}}
         (this :-render-sidebar)
         (when-not editing?
           [:div {:style {:float "right"}}
            (launch/render-button {:workspace-id (:workspace-id props)
                                   :config-id (ws-common/config->id config)
                                   :root-entity-type (:rootEntityType config)
                                   :disabled? (cond (:locked? @state) "This workspace is locked."
                                                    (not (:bucket-access? props))
                                                    (str "You do not currently have access"
                                                         " to the Google Bucket associated with this workspace."))
                                   :on-success (:on-submission-success props)})])
         (this :-render-main)
         (common/clear-both)]]))
   :-render-sidebar
   (fn [{:keys [props state this]}]
     (let [{:keys [editing? loaded-config]} @state
           config (:methodConfiguration loaded-config)]
       [:div {:style {:width 290 :float "left"}}
        [:div {:ref "sidebar"}]
        (style/create-unselectable
         :div
         {:style {:position (when-not (:sidebar-visible? @state) "fixed")
                  :top (when-not (:sidebar-visible? @state) 4)
                  :width 290}}
         (let [{:keys [locked?]} @state
               can-edit? (common/access-greater-than? (:access-level props) "READER")
               snapshot-id (get-in config [:methodRepoMethod :methodVersion])
               config-id (ws-common/config->id config)]
           [:div {}
            (when (and can-edit? (not editing?))
              (list
               [comps/SidebarButton {:style :light :color :button-primary
                                     :text "Edit Configuration" :icon :edit
                                     :data-test-id "edit-method-config-button"
                                     :disabled? (when locked? "The workspace is locked")
                                     :onClick #(swap! state assoc :editing? true :prev-snapshot-id snapshot-id)}]
               [comps/SidebarButton {:style :light :color :exception-state :margin :top
                                     :text "Delete" :icon :delete
                                     :data-test-id "delete-method-config-button"
                                     :disabled? (when locked? "The workspace is locked")
                                     :onClick #(modal/push-modal
                                                [delete/DeleteDialog {:config-id config-id
                                                                      :workspace-id (:workspace-id props)
                                                                      :after-delete (:after-delete props)}])}]))

            (when-not editing?
              [comps/SidebarButton {:style :light :color :button-primary :margin (when can-edit? :top)
                                    :text "Publish..." :icon :share
                                    :onClick #(modal/push-modal
                                               [publish/PublishDialog {:config-id config-id
                                                                       :workspace-id (:workspace-id props)}])}])

            (when editing?
              (list
               [comps/SidebarButton {:color :success-state
                                     :text "Save" :icon :done
                                     :data-test-id "save-editted-method-config-button"
                                     :onClick #(this :-commit)}]
               [comps/SidebarButton {:color :exception-state :margin :top
                                     :text "Cancel Editing" :icon :cancel
                                     :data-test-id "cancel-edit-method-config-button"
                                     :onClick (fn []
                                                (this :load-new-method-template (:prev-snapshot-id @state))
                                                (swap! state assoc :editing? false))}]))]))]))
   :-render-main
   (fn [{:keys [state this]}]
     (let [{:keys [editing? loaded-config wdl-parse-error inputs-outputs methods]} @state
           config (:methodConfiguration loaded-config)
           {:keys [methodRepoMethod]} config]
       [:div {:style {:marginLeft 330}}
        (create-section-header "Method Configuration Name")
        (create-section
         (if editing?
           (style/create-text-field {:ref "confname" :style {:width 500}
                                     :data-test-id "edit-method-config-name-input"
                                     :defaultValue (:name config)})
           [:div {:style {:padding "0.5em 0 1em 0"}
                  :data-test-id "method-config-name"} (:name config)]))
        (create-section-header "Referenced Method")
        (create-section [MethodDetailsViewer
                         (merge {:ref "methodDetailsViewer"
                                 :name (:methodName methodRepoMethod)
                                 :namespace (:methodNamespace methodRepoMethod)
                                 :snapshotId (:methodVersion methodRepoMethod)
                                 :onSnapshotIdChange #(this :load-new-method-template %)}
                                (utils/restructure config methods editing? wdl-parse-error))])
        (create-section-header "Root Entity Type")
        (create-section
         (if editing?
           (style/create-identity-select {:ref "rootentitytype"
                                          :data-test-id "edit-method-config-root-entity-type-select"
                                          :defaultValue (:rootEntityType config)
                                          :style {:width 500}}
                                         common/root-entity-types)
           [:div {:style {:padding "0.5em 0 1em 0"}} (:rootEntityType config)]))
        [:datalist {:id "inputs-datalist"}
         [:option {:value "this."}]
         [:option {:value "workspace."}]]
        (create-section-header "Inputs")
        (input-output-list {:values (:inputs config)
                            :all-values (:inputs inputs-outputs)
                            :ref-prefix "in"
                            :invalid-values (:invalidInputs loaded-config)
                            :editing? editing?})
        (create-section-header "Outputs")
        (input-output-list {:values (:outputs config)
                            :all-values (:outputs inputs-outputs)
                            :ref-prefix "out"
                            :invalid-values (:invalidOutputs loaded-config)
                            :editing? editing?})]))
   :component-did-mount
   (fn [{:keys [state props refs this]}]
     (this :load-validated-method-config)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-workspace (:workspace-id props))
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (if success?
                    (swap! state assoc :locked? (get-in (get-parsed-response) [:workspace :isLocked]))
                    (swap! state assoc :error status-text)))})
     (endpoints/call-ajax-orch
      {:endpoint endpoints/list-methods
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (if success?
                    (swap! state assoc :methods (->> (get-parsed-response)
                                                     (map #(select-keys % [:namespace :name :snapshotId]))
                                                     (group-by (juxt :namespace :name))
                                                     (utils/map-values (partial map :snapshotId))))
                    (swap! state assoc :error-message status-text)))})
     (set! (.-onScrollHandler this)
           (fn []
             (when-let [sidebar (@refs "sidebar")]
               (let [visible (< (.-scrollY js/window) (.-offsetTop sidebar))]
                 (when-not (= visible (:sidebar-visible? @state))
                   (swap! state assoc :sidebar-visible? visible))))))
     (.addEventListener js/window "scroll" (.-onScrollHandler this)))
   :component-will-unmount
   (fn [{:keys [this]}]
     (.removeEventListener js/window "scroll" (.-onScrollHandler this)))
   :-commit
   (fn [{:keys [props state refs]}]
     (let [{:keys [workspace-id]} props
           config (-> @state :loaded-config :methodConfiguration)
           [name root-entity-type] (common/get-text refs "confname" "rootentitytype")
           deref-vals (fn [io-key ref-prefix]
                        (->> (io-key (:inputs-outputs @state))
                             (map :name)
                             (map (juxt identity #(common/get-text refs (str ref-prefix "_" %))))
                             (remove (comp empty? val))
                             (into {})))]
       (swap! state assoc :blocker "Updating...")
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/update-workspace-method-config workspace-id (ws-common/config->id config))
         :payload (assoc config
                    :name name
                    :rootEntityType root-entity-type
                    :inputs (deref-vals :inputs "in")
                    :outputs (deref-vals :outputs "out")
                    :methodRepoMethod (merge (:methodRepoMethod config)
                                             ((@refs "methodDetailsViewer") :get-fields))
                    :workspaceName workspace-id)
         :headers utils/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :blocker :editing?)
                    (if success?
                      (do ((:on-rename props) name)
                          (swap! state assoc :loaded-config (get-parsed-response) :blocker nil))
                      (comps/push-error-response (get-parsed-response false))))})))
   :load-validated-method-config
   (fn [{:keys [state props]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-validated-workspace-method-config (:workspace-id props) (:config-id props))
       :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                  (if success?
                    (let [response (get-parsed-response)]
                      (endpoints/call-ajax-orch
                       {:endpoint endpoints/get-inputs-outputs
                        :payload (get-in response [:methodConfiguration :methodRepoMethod])
                        :headers utils/content-type=json
                        :on-done (fn [{:keys [success? get-parsed-response]}]
                                   (if success?
                                     (swap! state assoc :loaded-config response :inputs-outputs (get-parsed-response))
                                     (swap! state assoc :error (:message (get-parsed-response)))))}))
                    (swap! state assoc :error status-text)))}))
   :load-new-method-template
   (fn [{:keys [state refs]} new-snapshot-id]
     (let [[method-namespace method-name] (map (fn [key]
                                                 (get-in (:loaded-config @state)
                                                         [:methodConfiguration :methodRepoMethod key]))
                                               [:methodNamespace :methodName])
           config-namespace+name (select-keys (get-in @state [:loaded-config :methodConfiguration])
                                              [:namespace :name])
           method-ref {:methodNamespace method-namespace
                       :methodName method-name
                       :methodVersion new-snapshot-id}]
       (swap! state assoc :blocker "Updating...")
       ((@refs "methodDetailsViewer") :load-agora-method {:namespace method-namespace
                                                          :name method-name
                                                          :snapshotId new-snapshot-id})
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/create-template method-ref)
         :payload method-ref
         :headers utils/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (let [response (get-parsed-response)]
                      (if success?
                        (endpoints/call-ajax-orch
                         {:endpoint endpoints/get-inputs-outputs
                          :payload (:methodRepoMethod response)
                          :headers utils/content-type=json
                          :on-done (fn [{:keys [success? get-parsed-response]}]
                                     (swap! state dissoc :blocker :wdl-parse-error)
                                     (let [template {:methodConfiguration (merge response config-namespace+name)}]
                                       (if success?
                                         (swap! state assoc
                                                :loaded-config (assoc template
                                                                 :invalidInputs {}
                                                                 :validInputs {}
                                                                 :invalidOutputs {}
                                                                 :validOutputs {})
                                                :inputs-outputs (get-parsed-response))
                                         (swap! state assoc :error (:message (get-parsed-response))))))})
                        (do
                          (swap! state assoc :blocker nil :wdl-parse-error (:message response))
                          (comps/push-error (style/create-server-error-message (:message response)))))))})))})
