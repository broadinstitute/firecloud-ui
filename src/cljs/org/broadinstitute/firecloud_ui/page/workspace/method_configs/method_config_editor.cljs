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
                 (filter (fn [[k v]] (not (empty? v))))
                 (into {}))
        outputs (->> ((:inputs-outputs @state) "outputs")
                  (map #(% "name"))
                  (map (juxt identity #(get-text refs (str "out_" %))))
                  (filter (fn [[k v]] (not (empty? v))))
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
       :headers {"Content-Type" "application/json"} ;; TODO - make endpoint take text/plain
       :on-done (fn [{:keys [success? get-parsed-response xhr]}]
                  (if-not success?
                    (do (js/alert (str "Exception:\n" (.-statusText xhr)))
                        (swap! state dissoc :blocker))
                    (if (= name (config "name"))
                      (swap! state assoc :loaded-config (get-parsed-response) :blocker nil)
                      (endpoints/call-ajax-orch ;; TODO - make unified call in orchestration
                        {:endpoint (endpoints/rename-workspace-method-config workspace-id config)
                         :payload (select-keys new-conf ["name" "namespace" "workspaceName"])
                         :headers {"Content-Type" "application/json"}
                         :on-done (fn [{:keys [success? xhr]}]
                                    (swap! state dissoc :blocker)
                                    (if success?
                                      ((:on-rename props) name)
                                      (js/alert (str "Exception:\n" (.-statusText xhr)))))}))))})))

(react/defc MethodDetailsViewer
  {:get-fields
   (fn [{:keys [refs]}]
     (react/call :get-fields (@refs "methodDetails")))
   :render
   (fn [{:keys [props refs state]}]
     (cond
       (:loaded-method @state)
       [comps/EntityDetails {:entity (:loaded-method @state) :editing? (:editing? props)
                             :ref "methodDetails"
                             :onSnapshotIdChange (:onSnapshotIdChange props)
                                                     :snapshots (get-in (:methods props)
                                                                        [[(get-in (:loaded-method @state) ["namespace"])
                                                                          (get-in (:loaded-method @state) ["name"])]])}]
       (:error @state) (style/create-server-error-message (:error @state))
        :else [comps/Spinner {:text "Loading details..."}]))
   :component-did-mount
   (fn [{:keys [this props state]}]
     (react/call :load-agora-method this props state))
   :load-agora-method
   (fn [{:keys [props state]} method-ref]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-agora-method
                    (:namespace method-ref)
                    (:name method-ref)
                    (:snapshotId method-ref))
        :headers {"Content-Type" "application/json"}
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :loaded-method (get-parsed-response))
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
          [comps/SidebarButton {:style :light :color :button-blue
                                :text "Edit this page" :icon :pencil
                                :disabled? (when locked? "The workspace is locked")
                                :onClick #(swap! state assoc :editing? true)}])
        (when-not editing?
          [comps/SidebarButton {:style :light :color :exception-red :margin :top
                                :text "Delete" :icon :trash-can
                                :disabled? (when locked? "The workspace is locked")
                                :onClick #(modal/push-modal
                                           [delete/DeleteDialog {:config config
                                                                 :workspace-id (:workspace-id props)
                                                                 :after-delete (:after-delete props)}])}])

        (when-not editing?
          [comps/SidebarButton {:style :light :color :button-blue :margin :top
                                :text "Publish" :icon :share
                                :onClick #(modal/push-modal
                                           [publish/PublishDialog {:config config
                                                                   :workspace-id (:workspace-id props)}])}])

        (when editing?
          [comps/SidebarButton {:color :success-green
                                :text "Save" :icon :status-done
                                :onClick #(do (commit state refs config props)
                                              (stop-editing state))}])
        (when editing?
          [comps/SidebarButton {:color :exception-red :margin :top
                                :text "Cancel Editing" :icon :x
                                :onClick #(stop-editing state)}])]))])


(defn- input-output-list [config value-type invalid-values editing? all-values]
  (create-section
    [:div {}
     (map
       (fn [m]
         (let [[field-name inputType outputType optional?] (map m ["name" "inputType" "outputType" "optional"])
               type (or inputType outputType)
               field-value (get (config value-type) field-name)
               error (invalid-values field-name)]
           [:div {}
            [:div {:style {:float "left" :margin "0 1em 0.5em 0" :padding "0.5em" :verticalAlign "middle"
                           :backgroundColor (:background-gray style/colors)
                           :border style/standard-line :borderRadius 2}}
             (str field-name ": (" (when optional? "optional ") type ")")]
            (if editing?
              [:div {:style {:float "left"}}
               (style/create-text-field {:ref (str (if (= value-type "inputs") "in" "out") "_" field-name)
                                         :defaultValue field-value})]
              [:div {:style {:float "left" :marginTop "0.5em"}}
               (or field-value [:span {:style {:fontStyle "italic"}} "No value entered"])
               (when error
                 (icons/font-icon {:style {:margin "0.5em 0 0 0.7em" :verticalAlign "middle"
                                           :color (:exception-red style/colors)}}
                   :x))])
            (clear-both)
            (when error
              [:div {:style {:padding "0.5em" :marginBottom "0.5em"
                             :backgroundColor (:exception-red style/colors)
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
     (create-section-header "Root Entity Type")
     (create-section
       (if editing?
         (style/create-identity-select {:ref "rootentitytype"
                                        :defaultValue (config "rootEntityType")
                                        :style {:width "500px"}}
                                       root-entity-types)
         [:div {:style {:padding "0.5em 0 1em 0"}} (config "rootEntityType")]))
     (create-section-header "Inputs")
     (input-output-list config "inputs" invalid-inputs editing? (inputs-outputs "inputs"))
     (create-section-header "Outputs")
     (input-output-list config "outputs" invalid-outputs editing? (inputs-outputs "outputs"))
     (create-section-header "Referenced Method")
     (create-section [MethodDetailsViewer
                      {:ref "methodDetailsViewer"
                       :name (get-in config ["methodRepoMethod" "methodName"])
                       :namespace (get-in config ["methodRepoMethod" "methodNamespace"])
                       :snapshotId (get-in config ["methodRepoMethod" "methodVersion"])
                       :config config
                       :methods methods
                       :editing? editing?
                       :onSnapshotIdChange #(react/call :load-new-method-template this %)}])]))

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
                                :root-entity-type (config "rootEntityType")
                                :disabled? (cond (:locked? @state) "This workspace is locked"
                                                 (not (:bucket-access? props))
                                                 (str "You do not currently have access"
                                                      " to the Google Bucket associated with this workspace"))
                                :force-login? (not (:has-refresh-token? @state))
                                :after-login #(swap! state assoc :has-refresh-token? true)
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
     (cond (and (:loaded-config @state) (contains? @state :locked?) (contains? @state :has-refresh-token?))
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
                     (swap! state assoc :locked? (get-in (get-parsed-response) ["workspace" "isLocked"]))
                     (swap! state assoc :error status-text)))})
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-refresh-token-date)
        :on-done (fn [{:keys [success?]}]
                   ;; login checks validity of the token, so just check for existence
                   (swap! state assoc :has-refresh-token? success?))})
     (endpoints/call-ajax-orch
       {:endpoint endpoints/list-methods
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :methods (->> (get-parsed-response)
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
   :load-validated-method-config
   (fn [{:keys [state props refs this]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-validated-workspace-method-config (:workspace-id props) (:config-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                     (if success?
                       (let [response (get-parsed-response)]
                            (endpoints/call-ajax-orch
                              {:endpoint endpoints/get-inputs-outputs
                               :payload (get-in response ["methodConfiguration" "methodRepoMethod"])
                               :headers {"Content-Type" "application/json"}
                               :on-done (fn [{:keys [success? get-parsed-response]}]
                                            (if success?
                                              (swap! state assoc :loaded-config response :inputs-outputs (get-parsed-response))
                                              (swap! state assoc :error ((get-parsed-response) "message"))))}))
                       (swap! state assoc :error status-text)))}))
   :load-new-method-template
   (fn [{:keys [state props refs this]} new-snapshot-id]
     (let [namespace (get-in (:loaded-config @state) ["methodConfiguration" "methodRepoMethod" "methodNamespace"])
           name (get-in (:loaded-config @state) ["methodConfiguration" "methodRepoMethod" "methodName"])
           method-ref {"methodNamespace" namespace
                       "methodName" name
                       "methodVersion" new-snapshot-id}]
       (swap! state assoc :blocker "Updating...")
       (react/call :load-agora-method (@refs "methodDetailsViewer") {:namespace namespace
                                                                     :name name
                                                                     :snapshotId new-snapshot-id} state)
       (endpoints/call-ajax-orch
         {:endpoint (endpoints/create-template method-ref)
          :payload method-ref
          :headers {"Content-Type" "application/json"}
          :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                       (if success?
                         (let [response (get-parsed-response)]
                              (endpoints/call-ajax-orch
                                {:endpoint endpoints/get-inputs-outputs
                                 :payload (get-in response ["methodRepoMethod"])
                                 :headers {"Content-Type" "application/json"}
                                 :on-done (fn [{:keys [success? get-parsed-response]}]
                                            (swap! state dissoc :blocker)
                                            (let [template {"methodConfiguration" (assoc response "namespace" namespace
                                                                                         "name" name)}]
                                              (if success?
                                                (swap! state assoc :loaded-config (assoc template
                                                                                         "invalidInputs" {}
                                                                                         "validInputs" {}
                                                                                         "invalidOutputs" {}
                                                                                         "validOutputs" {})
                                                       :inputs-outputs (get-parsed-response))
                                                (swap! state assoc :error ((get-parsed-response) "message")))))}))
                         (swap! state assoc :error status-text)))})))
   :component-will-unmount
   (fn [{:keys [this]}]
     (.removeEventListener js/window "scroll" (.-onScrollHandler this)))})
