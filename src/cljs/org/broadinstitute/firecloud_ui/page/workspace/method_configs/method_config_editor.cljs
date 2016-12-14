(ns org.broadinstitute.firecloud-ui.page.workspace.method-configs.method-config-editor
  (:require
    [dmohs.react :as react]
    [clojure.string :refer [trim blank?]]
    [org.broadinstitute.firecloud-ui.common :refer [clear-both get-text root-entity-types]]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.overlay :as dialog]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.workspace.method-configs.delete-config :as delete]
    [org.broadinstitute.firecloud-ui.page.workspace.method-configs.launch-analysis :as launch]
    [org.broadinstitute.firecloud-ui.page.workspace.method-configs.publish :as publish]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    [org.broadinstitute.firecloud-ui.config :as gconfig]
    ))

(defn- filter-empty [list]
  (vec (remove blank? (map trim list))))

(defn- create-section-header [text]
  [:div {:style {:fontSize "125%" :fontWeight 500}} text])

(defn- create-section [& children]
  [:div {:style {:padding "1em 0 2em 0"}} children])

(defn- stop-editing [state]
  (swap! state assoc :editing? false))

(defn- commit [state refs config props]
  (let [workspace-id (:workspace-id props)
        method (get-in config ["methodRepoMethod"])
        [name rootEntityType] (get-text refs "confname" "rootentitytype")
        inputs (->> ((:inputs-outputs @state) "inputs")
                    (map #(% "name"))
                    (map (juxt identity #(get-text refs (str "in_" %))))
                    (filter (comp not empty? val))
                    (into {}))
        outputs (->> ((:inputs-outputs @state) "outputs")
                     (map #(% "name"))
                     (map (juxt identity #(get-text refs (str "out_" %))))
                     (filter (comp not empty? val))
                     (into {}))
        method-ref (merge method (react/call :get-fields (@refs "methodDetailsViewer")))
        new-conf (assoc config
                   "name" name
                   "rootEntityType" rootEntityType
                   "inputs" inputs
                   "outputs" outputs
                   "methodRepoMethod" method-ref
                   "workspaceName" workspace-id)]
    (swap! state assoc :blocker "Updating...")
    (endpoints/call-ajax-orch
      {:endpoint (endpoints/update-workspace-method-config workspace-id config)
       :payload new-conf
       :headers utils/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response xhr]}]
                  (if-not success?
                    (do (comps/push-error-text (str "Exception:\n" (.-statusText xhr)))
                        (swap! state dissoc :blocker))
                    (if (= name (config "name"))
                      (swap! state assoc :loaded-config (get-parsed-response false) :blocker nil)
                      (endpoints/call-ajax-orch ;; TODO - make unified call in orchestration
                        {:endpoint (endpoints/rename-workspace-method-config workspace-id config)
                         :payload (select-keys new-conf ["name" "namespace" "workspaceName"])
                         :headers utils/content-type=json
                         :on-done (fn [{:keys [success? xhr]}]
                                    (swap! state dissoc :blocker)
                                    (if success?
                                      ((:on-rename props) name)
                                      (comps/push-error-text
                                       (str "Exception:\n" (.-statusText xhr)))))}))))})))

(react/defc MethodDetailsViewer
  {:get-fields
   (fn [{:keys [refs]}]
     (react/call :get-fields (@refs "methodDetails")))
   :render
   (fn [{:keys [props state]}]
     (cond
       (:loaded-method @state)
       [comps/EntityDetails {:entity (:loaded-method @state)
                             :editing? (:editing? props)
                             :ref "methodDetails"
                             :onSnapshotIdChange (:onSnapshotIdChange props)
                             :snapshots ((:methods props) (map (:loaded-method @state) ["namespace" "name"]))}]
       (:error @state) (style/create-server-error-message (:error @state))
        :else [comps/Spinner {:text "Loading details..."}]))
   :component-did-mount
   (fn [{:keys [this props state]}]
     (react/call :load-agora-method this props state))
   :load-agora-method
   (fn [{:keys [state]} method-ref]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-agora-method
                    (:namespace method-ref)
                    (:name method-ref)
                    (:snapshotId method-ref))
        :headers utils/content-type=json
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :loaded-method (get-parsed-response false))
                     (swap! state assoc :error status-text)))}))})


(defn- render-side-bar [state refs config editing? props]
  [:div {:style {:width 290 :float "left"}}
   [:div {:ref "sidebar"}]
   (style/create-unselectable :div {:style {:position (when-not (:sidebar-visible? @state) "fixed")
                                            :top (when-not (:sidebar-visible? @state) 4)
                                            :width 290}}
     (let [locked? (:locked? @state)]
       [:div {:style {:lineHeight 1}}
        (when-not editing?
          [comps/SidebarButton {:style :light :color :button-primary
                                :text "Edit this page" :icon :edit
                                :disabled? (when locked? "The workspace is locked")
                                :onClick #(swap! state assoc :editing? true)}])
        (when-not editing?
          [comps/SidebarButton {:style :light :color :exception-state :margin :top
                                :text "Delete" :icon :delete
                                :disabled? (when locked? "The workspace is locked")
                                :onClick #(modal/push-modal
                                           [delete/DeleteDialog {:config config
                                                                 :workspace-id (:workspace-id props)
                                                                 :after-delete (:after-delete props)}])}])

        (when-not editing?
          [comps/SidebarButton {:style :light :color :button-primary :margin :top
                                :text "Publish" :icon :share
                                :onClick #(modal/push-modal
                                           [publish/PublishDialog {:config config
                                                                   :workspace-id (:workspace-id props)}])}])

        (when editing?
          [comps/SidebarButton {:color :success-state
                                :text "Save" :icon :done
                                :onClick #(do (commit state refs config props)
                                              (stop-editing state))}])
        (when editing?
          [comps/SidebarButton {:color :exception-state :margin :top
                                :text "Cancel Editing" :icon :cancel
                                :onClick #(stop-editing state)}])]))])


(defn- input-output-list [values ref-prefix invalid-values editing? all-values]
  (create-section
   [:div {}
    (map
     (fn [m]
       (let [[field-name inputType outputType optional?] (map m ["name" "inputType" "outputType" "optional"])
             type (or inputType outputType)
             field-value (get values field-name "")
             error (invalid-values field-name)]
         [:div {:key field-name}
          [:div {:style {:display "flex" :alignItems "baseline" :marginBottom "0.5em"}}
           [:div {:style {:marginRight "1em" :padding "0.5em"
                          :backgroundColor (:background-light style/colors)
                          :border style/standard-line :borderRadius 2}}
            (str field-name ": (" (when optional? "optional ") type ")")]
           (when editing?
             (style/create-text-field {:ref (str ref-prefix "_" field-name)
                                       :defaultValue field-value}))
           (when-not editing?
             (or field-value [:span {:style {:fontStyle "italic"}} "No value entered"]))
           (when (and error (not editing?))
             (icons/icon {:style {:margin "0 0 0 0.7em" :alignSelf "center"
                                  :color (:exception-state style/colors)}}
                         :error))]
          (when error
            [:div {:style {:padding "0.5em" :marginBottom "0.5em"
                           :backgroundColor (:exception-state style/colors)
                           :display "inline-block"
                           :border style/standard-line :borderRadius 2}}
             error])]))
     all-values)]))

(defn- render-main-display [this wrapped-config editing? inputs-outputs methods]
  (let [config (get-in wrapped-config ["methodConfiguration"])
        invalid-inputs (get-in wrapped-config ["invalidInputs"])
        invalid-outputs (get-in wrapped-config ["invalidOutputs"])]
    [:div {:style {:marginLeft 330}}
     (create-section-header "Method Configuration Name")
     (create-section
       (if editing?
         (style/create-text-field {:ref "confname" :style {:width 500}
                                   :defaultValue (config "name")})
         [:div {:style {:padding "0.5em 0 1em 0"}} (config "name")]))
     (create-section-header "Referenced Method")
     (create-section [MethodDetailsViewer
                      {:ref "methodDetailsViewer"
                       :name (get-in config ["methodRepoMethod" "methodName"])
                       :namespace (get-in config ["methodRepoMethod" "methodNamespace"])
                       :snapshotId (get-in config ["methodRepoMethod" "methodVersion"])
                       :config config
                       :methods methods
                       :editing? editing?
                       :onSnapshotIdChange #(react/call :load-new-method-template this %)}])
     (create-section-header "Root Entity Type")
     (create-section
       (if editing?
         (style/create-identity-select {:ref "rootentitytype"
                                        :defaultValue (config "rootEntityType")
                                        :style {:width 500}}
                                       root-entity-types)
         [:div {:style {:padding "0.5em 0 1em 0"}} (config "rootEntityType")]))
     (create-section-header "Inputs")
     (input-output-list (config "inputs") "in" invalid-inputs editing? (inputs-outputs "inputs"))
     (create-section-header "Outputs")
     (input-output-list
      (config "outputs") "out" invalid-outputs editing? (inputs-outputs "outputs"))]))

(defn- render-display [this state refs props]
  (let [wrapped-config (:loaded-config @state)
        config (wrapped-config "methodConfiguration")
        editing? (:editing? @state)]
    [:div {}
     [comps/Blocker {:banner (:blocker @state)}]
     [:div {:style {:padding "1em 2em"}}
      (render-side-bar state refs config editing? props)
      (when-not editing?
        [:div {:style {:float "right"}}
         (launch/render-button {:workspace-id (:workspace-id props)
                                :config-id {:namespace (config "namespace") :name (config "name")}
                                :cromwell-version (gconfig/cromwell-version)
                                :root-entity-type (config "rootEntityType")
                                :disabled? (cond (:locked? @state) "This workspace is locked."
                                                 (not (:bucket-access? props))
                                                 (str "You do not currently have access"
                                                      " to the Google Bucket associated with this workspace."))
                                :on-success (:on-submission-success props)})])
      (render-main-display this wrapped-config editing? (:inputs-outputs @state) (:methods @state))
      (clear-both)]]))

(react/defc MethodConfigEditor
  {:get-initial-state
   (fn []
     {:editing? false
      :sidebar-visible? true})
   :render
   (fn [{:keys [this state refs props]}]
     (cond (and (:loaded-config @state) (contains? @state :locked?))
           (render-display this state refs props)
           (:error @state) (style/create-server-error-message (:error @state))
           :else [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading Method Configuration..."}]]))
   :component-did-mount
   (fn [{:keys [state props refs this]}]
     (react/call :load-validated-method-config this)
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :locked? (get-in (get-parsed-response false) ["workspace" "isLocked"]))
                     (swap! state assoc :error status-text)))})
     (endpoints/call-ajax-orch
       {:endpoint endpoints/list-methods
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :methods (->> (get-parsed-response false)
                                                      (map utils/keywordize-keys)
                                                      (map #(select-keys % [:namespace :name :snapshotId]))
                                                      (group-by (juxt :namespace :name))
                                                      (map (fn [[k v]] [k (map :snapshotId v)]))
                                                      (into {})))
                     (swap! state assoc :error-message status-text)))})
     (set! (.-onScrollHandler this)
           (fn []
             (when-let [sidebar (@refs "sidebar")]
               (let [visible (< (.-scrollY js/window) (.-offsetTop sidebar)) ]
                 (when-not (= visible (:sidebar-visible? @state))
                   (swap! state assoc :sidebar-visible? visible))))))
     (.addEventListener js/window "scroll" (.-onScrollHandler this)))
   :component-will-unmount
   (fn [{:keys [this]}]
     (.removeEventListener js/window "scroll" (.-onScrollHandler this)))
   :load-validated-method-config
   (fn [{:keys [state props]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-validated-workspace-method-config (:workspace-id props) (:config-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                     (if success?
                       (let [response (get-parsed-response false)]
                            (endpoints/call-ajax-orch
                              {:endpoint endpoints/get-inputs-outputs
                               :payload (get-in response ["methodConfiguration" "methodRepoMethod"])
                               :headers utils/content-type=json
                               :on-done (fn [{:keys [success? get-parsed-response]}]
                                            (if success?
                                              (swap! state assoc :loaded-config response :inputs-outputs (get-parsed-response false))
                                              (swap! state assoc :error ((get-parsed-response false) "message"))))}))
                       (swap! state assoc :error status-text)))}))
   :load-new-method-template
   (fn [{:keys [state refs]} new-snapshot-id]
     (let [[method-namespace method-name] (map (fn [key]
                                                 (get-in (:loaded-config @state)
                                                         ["methodConfiguration" "methodRepoMethod" key]))
                                               ["methodNamespace" "methodName"])
           config-namespace+name (select-keys (get-in @state [:loaded-config "methodConfiguration"])
                                              ["namespace" "name"])
           method-ref {"methodNamespace" method-namespace
                       "methodName" method-name
                       "methodVersion" new-snapshot-id}]
       (swap! state assoc :blocker "Updating...")
       (react/call :load-agora-method (@refs "methodDetailsViewer") {:namespace method-namespace
                                                                     :name method-name
                                                                     :snapshotId new-snapshot-id})
       (endpoints/call-ajax-orch
         {:endpoint (endpoints/create-template method-ref)
          :payload method-ref
          :headers utils/content-type=json
          :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                     (if success?
                       (let [response (get-parsed-response false)]
                         (endpoints/call-ajax-orch
                           {:endpoint endpoints/get-inputs-outputs
                            :payload (response "methodRepoMethod")
                            :headers utils/content-type=json
                            :on-done (fn [{:keys [success? get-parsed-response]}]
                                       (swap! state dissoc :blocker)
                                       (let [template {"methodConfiguration" (merge response config-namespace+name)}]
                                         (if success?
                                           (swap! state assoc
                                                  :loaded-config (assoc template
                                                                   "invalidInputs" {}
                                                                   "validInputs" {}
                                                                   "invalidOutputs" {}
                                                                   "validOutputs" {})
                                                  :inputs-outputs (get-parsed-response false))
                                           (swap! state assoc :error ((get-parsed-response false) "message")))))}))
                       (swap! state assoc :error status-text)))})))})
