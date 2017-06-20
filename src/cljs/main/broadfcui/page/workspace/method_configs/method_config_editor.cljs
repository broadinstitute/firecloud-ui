(ns broadfcui.page.workspace.method-configs.method-config-editor
  (:require
    [dmohs.react :as react]
    [clojure.string :refer [trim blank?]]
    [broadfcui.common :refer [clear-both get-text root-entity-types access-greater-than?]]
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
        method (:methodRepoMethod config)
        [name rootEntityType] (get-text refs "confname" "rootentitytype")
        inputs (->> (:inputs (:inputs-outputs @state))
                    (map :name)
                    (map (juxt identity #(get-text refs (str "in_" %))))
                    (remove (comp empty? val))
                    (into {}))
        outputs (->> (:outputs (:inputs-outputs @state))
                     (map :name)
                     (map (juxt identity #(get-text refs (str "out_" %))))
                     (remove (comp empty? val))
                     (into {}))
        method-ref (merge method ((@refs "methodDetailsViewer") :get-fields))
        new-conf (assoc config
                   :name name
                   :rootEntityType rootEntityType
                   :inputs inputs
                   :outputs outputs
                   :methodRepoMethod method-ref
                   :workspaceName workspace-id)]
    (swap! state assoc :blocker "Updating...")
    (endpoints/call-ajax-orch
      {:endpoint (endpoints/update-workspace-method-config workspace-id (ws-common/config->id config))
       :payload new-conf
       :headers utils/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (swap! state dissoc :blocker)
                  (if success?
                    (do ((:on-rename props) name)
                        (swap! state assoc :loaded-config (get-parsed-response) :blocker nil))
                    (comps/push-error-response (get-parsed-response false))))})))

(react/defc MethodDetailsViewer
  {:get-fields
   (fn [{:keys [refs]}]
     ((@refs "methodDetails") :get-fields))
   :render
   (fn [{:keys [props state]}]
     (cond
       (:loaded-method @state)
       [comps/EntityDetails {:entity (:loaded-method @state)
                             :editing? (:editing? props)
                             :wdl-parse-error (:wdl-parse-error props)
                             :ref "methodDetails"
                             :onSnapshotIdChange (:onSnapshotIdChange props)
                             :snapshots (get (:methods props) (replace (:loaded-method @state) [:namespace :name]))}]
       (:error @state) (style/create-server-error-message (:error @state))
       :else [comps/Spinner {:text "Loading details..."}]))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :load-agora-method))
   :load-agora-method
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-agora-method
                    (:namespace props)
                    (:name props)
                    (:snapshotId props))
        :headers utils/content-type=json
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :loaded-method (get-parsed-response))
                     (swap! state assoc :error status-text)))}))})


(defn- render-side-bar [{:keys [state refs config editing? props restore-on-cancel]}]
  [:div {:style {:width 290 :float "left"}}
   [:div {:ref "sidebar"}]
   (style/create-unselectable :div {:style {:position (when-not (:sidebar-visible? @state) "fixed")
                                            :top (when-not (:sidebar-visible? @state) 4)
                                            :width 290}}
     (let [locked? (:locked? @state)
           can-edit? (access-greater-than? (:access-level props) "READER")
           snapshot-id (get-in config [:methodRepoMethod :methodVersion])
           config-id (ws-common/config->id config)]
       [:div {}
        (when (and can-edit? (not editing?))
          (list
           [comps/SidebarButton {:style :light :color :button-primary
                                 :text "Edit Configuration" :icon :edit
                                 :disabled? (when locked? "The workspace is locked")
                                 :onClick #(swap! state assoc :editing? true :prev-snapshot-id snapshot-id)}]
           [comps/SidebarButton {:style :light :color :exception-state :margin :top
                                 :text "Delete" :icon :delete
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
          [comps/SidebarButton {:color :success-state
                                :text "Save" :icon :done
                                :onClick (fn []
                                           (commit state refs config props)
                                           (stop-editing state))}])
        (when editing?
          [comps/SidebarButton {:color :exception-state :margin :top
                                :text "Cancel Editing" :icon :cancel
                                :onClick (fn []
                                           (restore-on-cancel (:prev-snapshot-id @state))
                                           (stop-editing state))}])]))])


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

(defn- render-main-display [{:keys [on-snapshot-id-change wrapped-config editing?
                                    wdl-parse-error inputs-outputs methods]}]
  (let [config (:methodConfiguration wrapped-config)
        {:keys [methodRepoMethod]} config]
    [:div {:style {:marginLeft 330}}
     (create-section-header "Method Configuration Name")
     (create-section
       (if editing?
         (style/create-text-field {:ref "confname" :style {:width 500}
                                   :defaultValue (:name config)})
         [:div {:style {:padding "0.5em 0 1em 0"}} (:name config)]))
     (create-section-header "Referenced Method")
     (create-section [MethodDetailsViewer
                      {:ref "methodDetailsViewer"
                       :name (:methodName methodRepoMethod)
                       :namespace (:methodNamespace methodRepoMethod)
                       :snapshotId (:methodVersion methodRepoMethod)
                       :config config
                       :methods methods
                       :editing? editing?
                       :wdl-parse-error wdl-parse-error
                       :onSnapshotIdChange on-snapshot-id-change}])
     (create-section-header "Root Entity Type")
     (create-section
       (if editing?
         (style/create-identity-select {:ref "rootentitytype"
                                        :defaultValue (:rootEntityType config)
                                        :style {:width 500}}
                                       root-entity-types)
         [:div {:style {:padding "0.5em 0 1em 0"}} (:rootEntityType config)]))
     [:datalist {:id "inputs-datalist"}
      [:option {:value "this."}]
      [:option {:value "workspace."}]]
     (create-section-header "Inputs")
     (input-output-list {:values (:inputs config)
                         :all-values (:inputs inputs-outputs)
                         :ref-prefix "in"
                         :invalid-values (:invalidInputs wrapped-config)
                         :editing? editing?})
     (create-section-header "Outputs")
     (input-output-list {:values (:outputs config)
                         :all-values (:outputs inputs-outputs)
                         :ref-prefix "out"
                         :invalid-values (:invalidOutputs wrapped-config)
                         :editing? editing?})]))

(defn- render-display [{:keys [this state refs props]}]
  (let [wrapped-config (:loaded-config @state)
        config (:methodConfiguration wrapped-config)
        editing? (:editing? @state)
        restore-on-cancel #(this :load-new-method-template %)]
    [:div {}
     [comps/Blocker {:banner (:blocker @state)}]
     [:div {:style {:padding "1em 2em"}}
      (render-side-bar (utils/restructure state refs config editing? props restore-on-cancel))
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
      (render-main-display (merge {:on-snapshot-id-change #(this :load-new-method-template %)}
                                  (utils/restructure wrapped-config editing?)
                                  (select-keys @state [:wdl-parse-error :inputs-outputs :methods])))
      (clear-both)]]))

(react/defc MethodConfigEditor
  {:get-initial-state
   (fn []
     {:editing? false
      :sidebar-visible? true})
   :render
   (fn [{:keys [state] :as react-data}]
     (cond (and (every? @state [:loaded-config :methods])
                (contains? @state :locked?))
           (render-display react-data)

           (:error @state) (style/create-server-error-message (:error @state))
           :else [:div {:style {:textAlign "center"}}
                  [comps/Spinner {:text "Loading Method Configuration..."}]]))
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
