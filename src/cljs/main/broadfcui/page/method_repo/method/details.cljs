(ns broadfcui.page.method-repo.method.details
  (:require
   [dmohs.react :as react]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.foundation-dropdown :as dropdown]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.components.tab-bar :as tab-bar]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.net :as net]
   [broadfcui.page.method-repo.method.configs :as configs]
   [broadfcui.page.method-repo.method.exporter :refer [MethodExporter]]
   [broadfcui.page.method-repo.method.summary :refer [Summary]]
   [broadfcui.page.method-repo.method.wdl :refer [WDLViewer]]
   [broadfcui.page.workspace.method-configs.synchronize :as mc-sync]
   [broadfcui.page.workspace.workspace-common :as ws-common]
   [broadfcui.utils :as utils]
   ))


(def ^:private SUMMARY "Summary")
(def ^:private WDL "WDL")
(def ^:private CONFIGS "Configurations")

(def ^:private tab-nav-map
  {nil :method-summary
   WDL :method-wdl
   CONFIGS :method-configs})

(declare MethodDetails)

(react/defc MethodDetails
  {:render
   (fn [{:keys [props state refs this]}]
     (let [{:keys [method-id snapshot-id config-id config-snapshot-id workspace-id nav-method]} props
           {:keys [method method-error selected-snapshot loading-snapshot? exporting? post-export?]} @state
           selected-snapshot-id (or snapshot-id (:snapshotId (last method)))
           active-tab (:tab-name props)
           request-refresh #(this :-refresh-method)
           refresh-snapshot #(this :-refresh-snapshot selected-snapshot-id)
           refresh-tab #((@refs %) :refresh)]
       [:div {:style {:position "relative"}}
        (when loading-snapshot?
          (blocker "Loading..."))
        (when (and method exporting?)
          [MethodExporter {:dismiss #(swap! state dissoc :exporting?)
                           :method-name (:name (last method))
                           :method-id method-id
                           :selected-snapshot-id selected-snapshot-id
                           :initial-config-id (some-> config-id (assoc :snapshotId config-snapshot-id))
                           :on-export
                           (fn [workspace-id config-id]
                             (swap! state assoc
                                    :exporting? false
                                    :dest-workspace-id workspace-id
                                    :dest-config-id config-id
                                    :post-export? true))}])
        (when post-export?
          [modals/OKCancelForm
           {:header "Export successful"
            :content "Would you like to go to the edit page now?"
            :cancel-text "No, stay here"
            :dismiss #(swap! state dissoc :post-export? :dest-workspace-id :dest-config-id)
            :ok-button
            {:text "Yes"
             :onClick #(mc-sync/flag-synchronization)
             :href (nav/get-link :workspace-method-config (:dest-workspace-id @state) (:dest-config-id @state))}}])
        [:div {:style {:display "flex" :marginTop "1.5rem" :padding "0 1.5rem" :alignItems "flex-end"}}
         (tab-bar/render-title
          "METHOD"
          [:span {}
           [:span {:data-test-id "header-namespace"} (:namespace method-id)]
           "/"
           [:span {:data-test-id "header-name"} (:name method-id)]])
         [:div {:style {:marginLeft "2rem" :marginTop -3}}
          (this :-render-snapshot-selector)]
         (when (:public (:selected-snapshot @state))
           [:span {:style {:alignSelf "center" :marginLeft "2.5rem"}}
            (icons/render-icon {:style {:marginRight "0.5rem"}} :public)
            "Publicly Readable"]) ; wording matches the permissions modal
         (when-not workspace-id
           [buttons/Button {:style {:marginLeft "auto"}
                            :text "Export to Workspace..."
                            :onClick #(swap! state assoc :exporting? true)}])]
        (tab-bar/create-bar (merge {:tabs [[SUMMARY :method-summary]
                                           [WDL :method-wdl]
                                           (when-not workspace-id [CONFIGS :method-configs])]
                                    :on-click (when workspace-id
                                                #(nav-method
                                                  {:replace? true
                                                   :label (str (:namespace method-id) "/" (:name method-id))
                                                   :component MethodDetails
                                                   :props (merge (utils/restructure method-id nav-method workspace-id)
                                                                 {:snapshot-id selected-snapshot-id
                                                                  :tab-name %})}))
                                    :context-id (assoc method-id :snapshot-id selected-snapshot-id)
                                    :active-tab (or active-tab SUMMARY)}
                                   (utils/restructure request-refresh refresh-tab)))
        [:div {:style {:marginTop "2rem"}}
         (if method-error
           [:div {:style {:textAlign "center" :color (:state-exception style/colors)}
                  :data-test-id "method-details-error"}
            "Error loading method: " method-error]
           (if-not selected-snapshot
             [:div {:style {:textAlign "center" :padding "1rem"}}
              (spinner "Loading method...")]
             (condp = active-tab
               WDL (react/create-element
                    [:div {}
                     [WDLViewer
                      {:ref WDL :wdl (:payload selected-snapshot)}]
                     (when (:public selected-snapshot)
                       [:div {:style {:marginLeft "1.5rem" :marginBottom "0.5rem"}}
                        [:span {:style {:fontWeight 500}}"Import URL for this WDL"]
                        (let [{:keys [namespace name snapshotId]} selected-snapshot
                              link (str (config/api-url-root)
                                        "/ga4gh/v1/tools/"
                                        namespace
                                        ":"
                                        name
                                        "/versions/"
                                        snapshotId
                                        "/plain-WDL/descriptor")]
                          [:input {:type "text" :readOnly true :value link
                                   :style {:cursor "unset" :fontSize "1rem" :width 300
                                           :display "block" :marginTop "0.25rem"}
                                   :onClick #(.. % -target select)}])])])
               CONFIGS (react/create-element
                        [configs/Configs
                         (merge {:ref CONFIGS}
                                (utils/restructure method-id snapshot-id config-id config-snapshot-id))])
               (react/create-element
                [Summary
                 (merge {:ref SUMMARY}
                        (utils/restructure selected-snapshot workspace-id refresh-snapshot))]))))]
        (when workspace-id
          (flex/box
           {:style {:marginTop "-1.5rem"}}
           flex/spring
           [buttons/Button {:style {:marginLeft "auto"}
                            :text "Select Configuration"
                            :onClick #(nav-method {:label "Select Configuration"
                                                   :component MethodExporter
                                                   :props {:workspace-id workspace-id
                                                           :method-name (:name (last method))
                                                           :method-id method-id
                                                           :selected-snapshot-id selected-snapshot-id
                                                           :initial-config (some-> config-id (assoc :snapshotId config-snapshot-id))
                                                           :on-export
                                                           (fn [workspace-id config-id]
                                                             (mc-sync/flag-synchronization)
                                                             (nav/go-to-path :workspace-method-config
                                                                             workspace-id
                                                                             (ws-common/config->id config-id)))}})}]))]))
   :component-will-mount
   (fn [{:keys [this]}]
     (this :-refresh-method))
   :component-did-update
   (fn [{:keys [props prev-props this]}]
     (when (not= (:snapshot-id props) (:snapshot-id prev-props))
       (this :-refresh-method true)))
   :-render-snapshot-selector
   (fn [{:keys [state this props]}]
     (let [{:keys [method]} @state
           snapshot-id (some :snapshot-id [props @state])
           selected-snapshot-id (or snapshot-id (:snapshotId (last method)))]
       (dropdown/render-dropdown-menu
        {:label (tab-bar/render-title
                 "SNAPSHOT"
                 [:div {:style {:display "flex" :alignItems "center"}
                        :data-test-id "snapshot-dropdown"}
                  [:span {} (if method selected-snapshot-id "Loading...")]
                  [:span {:style {:marginLeft "0.25rem" :fontSize 8 :lineHeight "inherit"}} "▼"]])
         :width :auto
         :items (vec (map
                      (fn [{:keys [snapshotId]}]
                        {:text snapshotId
                         :dismiss #(this :-refresh-snapshot snapshotId)})
                      method))})))
   :-refresh-method
   (fn [{:keys [props state this]} & [force-update?]]
     (let [{:keys [snapshot-id method-id]} props]
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/list-method-snapshots (:namespace method-id) (:name method-id))
         :on-done (net/handle-ajax-response
                   (fn [{:keys [success? parsed-response]}]
                     (if success?
                       (do (swap! state assoc :method parsed-response)
                           (when (or force-update? (not (:selected-snapshot @state)))
                             (this :-refresh-snapshot (or snapshot-id (:snapshotId (last parsed-response))))))
                       (swap! state assoc :method-error (:message parsed-response)))))})))
   :-refresh-snapshot
   (fn [{:keys [state props]} snapshot-id]
     (swap! state assoc :loading-snapshot? true)
     (let [{:keys [method-id tab-name workspace-id nav-method]} props
           {:keys [namespace name]} method-id
           old-snapshot-id (:snapshot-id props)
           tab-key (tab-nav-map tab-name)
           context-id (utils/restructure namespace name snapshot-id)]
       (when (not= old-snapshot-id snapshot-id)
         (if workspace-id
           (nav-method {:replace? true
                        :label (str namespace "/" name)
                        :component MethodDetails
                        :props (utils/restructure method-id snapshot-id workspace-id nav-method tab-name)})
           (if old-snapshot-id
             (nav/go-to-path tab-key context-id)
             ;; also stick new snapshot into state--otherwise page doesn't rerender
             (do (swap! state assoc :snapshot-id snapshot-id)
                 (nav/replace-history-state tab-key context-id)))))
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/get-agora-method namespace name snapshot-id)
         :on-done (net/handle-ajax-response
                   (fn [{:keys [success? parsed-response]}]
                     (if success?
                       (swap! state assoc :selected-snapshot parsed-response :loading-snapshot? false)
                       (swap! state assoc :method-error (:message parsed-response)))))})))})

(defn- method-path [{:keys [namespace name snapshot-id]}]
  (str "methods/" namespace "/" name "/" snapshot-id))

(defn add-nav-paths []
  (nav/defpath
   :method-loader
   {:component MethodDetails
    :regex #"methods/([^/]+)/([^/]+)/$"
    :make-props (fn [namespace name]
                  {:method-id (utils/restructure namespace name)})
    :make-path method-path})
  (nav/defpath
   :method-summary
   {:component MethodDetails
    :regex #"methods/([^/]+)/([^/]+)/(\d+)"
    :make-props (fn [namespace name snapshot-id]
                  {:method-id (utils/restructure namespace name)
                   :snapshot-id snapshot-id})
    :make-path method-path})
  (nav/defpath
   :method-wdl
   {:component MethodDetails
    :regex #"methods/([^/]+)/([^/]+)/(\d+)/wdl"
    :make-props (fn [namespace name snapshot-id]
                  {:method-id (utils/restructure namespace name)
                   :snapshot-id snapshot-id
                   :tab-name "WDL"})
    :make-path (fn [method-id]
                 (str (method-path method-id) "/wdl"))})
  (nav/defpath
   :method-configs
   {:component MethodDetails
    :regex #"methods/([^/]+)/([^/]+)/(\d+)/configs"
    :make-props (fn [namespace name snapshot-id]
                  {:method-id (utils/restructure namespace name)
                   :snapshot-id snapshot-id
                   :tab-name "Configurations"})
    :make-path (fn [method-id]
                 (str (method-path method-id) "/configs"))})
  (nav/defpath
   :method-config-viewer
   {:component MethodDetails
    :regex #"methods/([^/]+)/([^/]+)/(\d+)/configs/([^/]+)/([^/]+)/(\d+)"
    :make-props (fn [namespace name snapshot-id config-ns config-name config-snapshot-id]
                  {:method-id (utils/restructure namespace name)
                   :snapshot-id snapshot-id
                   :tab-name "Configurations"
                   :config-id {:namespace config-ns :name config-name}
                   :config-snapshot-id config-snapshot-id})
    :make-path (fn [method-id snapshot-id {:keys [config-ns config-name]} config-snapshot-id]
                 (str (method-path (assoc method-id :snapshot-id snapshot-id)) "/configs/"
                      config-ns "/" config-name "/" config-snapshot-id))}))
