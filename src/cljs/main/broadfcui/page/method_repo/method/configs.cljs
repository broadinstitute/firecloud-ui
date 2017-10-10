(ns broadfcui.page.method-repo.method.configs
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.sticky :refer [Sticky]]
   [broadfcui.components.tab-bar :as tab-bar]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.net :as net]
   [broadfcui.page.method-repo.method.common :as method-common]
   [broadfcui.page.method-repo.methods-configs-acl :as mca]
   [broadfcui.page.method-repo.redactor :refer [Redactor]]
   [broadfcui.page.method-repo.synchronize :as mr-sync]
   [broadfcui.utils :as utils]
   ))


(react/defc ConfigViewer
  {:component-will-mount
   (fn [{:keys [locals this]}]
     (swap! locals assoc :body-id (gensym "config"))
     (this :-refresh))
   :render
   (fn [{:keys [state this props]}]
     (let [{:keys [config config-error]} @state
           owner? (contains? (set (:managers config)) (utils/get-user-email))]
       [:div {:style {:margin "2.5rem 1.5rem"}}
        [:div {:style {:marginBottom "2rem"}}
         (let [{:keys [namespace name]} (:config-id props)]
           [:div {:style {:display "flex" :marginLeft (when owner? "300px")}}
            (tab-bar/render-title
             "CONFIGURATION"
             (str namespace "/" name))
            [:div {:style {:paddingLeft "2rem"}}
             (tab-bar/render-title
              "SNAPSHOT"
              (:config-snapshot-id props))]])]
        (cond
          config-error
          [:div {:style {:textAlign "center" :color (:exception-state style/colors)}}
           "Error loading config: " config-error]
          (not config)
          [:div {:style {:textAlign "center" :padding "1rem"}}
           [comps/Spinner {:text "Loading config..."}]]
          :else
          [:div {:style {:display "flex"}}
           [mr-sync/SyncContainer {:ref "sync-container" :config config}]
           (when owner?
             (this :-render-sidebar))
           (this :-render-main)])]))
   :-render-sidebar
   (fn [{:keys [state locals refs this]}]
     (let [{:keys [config]} @state
           {:keys [body-id]} @locals]
       [:div {:style {:flex "0 0 270px" :paddingRight 30}}
        (modals/show-modals
         state
         {:deleting?
          [Redactor {:entity config :config? true :on-delete #(nav/go-to-path :method-repo)}]
          :sharing?
          [mca/AgoraPermsEditor
           {:save-endpoint (endpoints/persist-agora-entity-acl true config)
            :load-endpoint (endpoints/get-agora-entity-acl true config)
            :on-commit #(this :-refresh)
            :entityType (:entityType config)
            :entityName (mca/get-ordered-name config)
            :title (str (:entityType config) " " (mca/get-ordered-name config))
            :on-users-added #((@refs "sync-container") :check-synchronization %)}]})
        [Sticky
         {:sticky-props {:data-check-every 1
                         :data-anchor body-id}
          :contents
          [:div {:style {:width 270}}
           [buttons/SidebarButton
            {:style :light :color :button-primary
             :text "Permissions..." :icon :settings :margin :bottom
             :onClick #(swap! state assoc :sharing? true)}]
           [buttons/SidebarButton
            {:style :light :color :exception-state
             :text "Redact" :icon :delete :margin :bottom
             :onClick #(swap! state assoc :deleting? true)}]]}]]))
   :-render-main
   (fn [{:keys [state locals]}]
     (let [{:keys [config]} @state
           {:keys [body-id]} @locals]
       [:div {:style {:flex "1 1 auto" :overflow "hidden"} :id body-id}
        (method-common/render-config-details config)]))
   :-refresh
   (fn [{:keys [props state]}]
     (swap! state dissoc :config :config-error)
     (let [{:keys [config-id config-snapshot-id]} props]
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/get-configuration (:namespace config-id) (:name config-id) config-snapshot-id true)
         :on-done (net/handle-ajax-response
                   (fn [{:keys [success? parsed-response]}]
                     (if success?
                       (swap! state assoc :config parsed-response)
                       (swap! state assoc :config-error (:message parsed-response)))))})))})

(react/defc Configs
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [method-id snapshot-id config-id config-snapshot-id]} props
           {:keys [configs configs-error]} @state
           make-config-link-props (fn [{:keys [namespace name snapshotId]}]
                                    {:href (nav/get-link :method-config-viewer
                                                         method-id snapshot-id
                                                         {:config-ns namespace :config-name name} snapshotId)})]
       [:div {:style {:margin "2.5rem 1.5rem"}}
        (cond
          configs-error [:div {:style {:textAlign "center" :color (:exception-state style/colors)}}
                         "Error loading configs: " configs-error]
          config-id [ConfigViewer (utils/restructure config-id config-snapshot-id)]
          (not configs) [:div {:style {:textAlign "center" :padding "1rem"}}
                         [comps/Spinner {:text "Loading configs..."}]]
          :else (method-common/render-config-table (utils/restructure make-config-link-props configs)))]))
   :component-will-mount
   (fn [{:keys [props this]}]
     (when-not (:config-id props)
       (this :refresh)))
   :component-did-update
   (fn [{:keys [props prev-props this]}]
     (when (not= (:snapshot-id props) (:snapshot-id prev-props))
       (this :refresh)))
   :refresh
   (fn [{:keys [props state]}]
     (swap! state dissoc :configs :configs-error)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-agora-compatible-configs (conj (:method-id props) (select-keys props [:snapshot-id])))
       :on-done (net/handle-ajax-response
                 (fn [{:keys [success? parsed-response]}]
                   (if success?
                     (swap! state assoc :configs parsed-response)
                     (swap! state assoc :configs-error (:message parsed-response)))))}))})
