(ns broadfcui.page.method-repo.method.configs
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.sticky :refer [Sticky]]
   [broadfcui.page.method-repo.redactor :refer [Redactor]]
   [broadfcui.net :as net]
   [broadfcui.common :as common]
   [broadfcui.utils :as utils]
   [broadfcui.page.method-repo.methods-configs-acl :as mca]
   [clojure.string :as string]
   [broadfcui.page.method-repo.synchronize :as mr-sync]
   [broadfcui.components.tab-bar :as tab-bar]))

(defn render-config-table [{:keys [make-config-link-props configs]}]
  [Table
   {:data configs
    :body {:empty-message "You don't have access to any published configurations for this method."
           :style table-style/table-light
           :behavior {:reorderable-columns? false}
           :columns [{:header "Configuration" :initial-width 400
                      :as-text (fn [{:keys [name namespace snapshotId]}]
                                 (str namespace "/" name " snapshot " snapshotId))
                      :sort-by :text
                      :render (fn [{:keys [name namespace snapshotId] :as config}]
                                (links/create-internal
                                 (make-config-link-props config)
                                 (style/render-name-id (str namespace "/" name) snapshotId)))}
                     {:header "Method Snapshot" :initial-width 150 :filterable? false
                      :column-data #(get-in % [:payload :methodRepoMethod :methodVersion]) :sort-by :text}
                     {:header "Synopsis" :initial-width :auto
                      :column-data :synopsis}]}}])

(react/defc ConfigViewer
  {:component-will-mount
   (fn [{:keys [props state locals]}]
     (swap! locals assoc :body-id (gensym "config"))
     (swap! state dissoc :configs :configs-error)
     (let [{:keys [config-id config-snapshot-id]} props]
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/get-configuration (:namespace config-id) (:name config-id) config-snapshot-id)
         :on-done (net/handle-ajax-response
                   (fn [{:keys [success? parsed-response]}]
                     (if success?
                       (swap! state assoc :config parsed-response)
                       (swap! state assoc :configs-error (:message parsed-response)))))})))
   :render
   (fn [{:keys [state this props]}]
     (let [{:keys [config config-error]} @state
           owner? (contains? (set (:managers config)) (utils/get-user-email))]
       [:div {:style {:margin "2.5rem 1.5rem"}}
        [:div {:style {:marginBottom "2rem"}}
         (let [{:keys [namespace name]} (:config-id props)]
           [:div {:style {:display "flex" :marginLeft (when owner? "300px")}}
            (tab-bar/render-title
             "CONFIG"
             (str namespace "/" name))
            [:div {:style {:paddingLeft "2rem"}}
             (tab-bar/render-title
              "SNAPSHOT"
              (:snapshot-id props))]])]
        (cond
          config-error
          [:div {:style {:textAlign "center" :color (:exception-state style/colors)}}
           "Error loading config: " config-error]
          (not config)
          [:div {:style {:textAlign "center" :padding "1rem"}}
           [comps/Spinner {:text "Loading config..."}]]
          :else
          [:div {:style {:display "flex"}}
           (when owner?
             [mr-sync/SyncContainer (merge {:ref "sync-container"} config)]
             (this :-render-sidebar))
           (this :-render-main)])]))
   :-render-sidebar
   (fn [{:keys [state locals refs]}]
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
            :entityType (:entityType config)
            :entityName (mca/get-ordered-name config)
            :title (str (:entityType config) " " (mca/get-ordered-name config))
            :on-users-added #((@refs "sync-container") :check-synchronization %)}]})
        [Sticky
         {:sticky-props {:data-check-every 1
                         :data-anchor body-id}
          :contents
          [:div {:style {:width 270}}
           [comps/SidebarButton
            {:style :light :color :button-primary
             :text "Permissions..." :icon :settings :margin :bottom
             :onClick #(swap! state assoc :sharing? true)}]
           [comps/SidebarButton
            {:style :light :color :exception-state
             :text "Redact" :icon :delete :margin :bottom
             :onClick #(swap! state assoc :deleting? true)}]]}]]))
   :-render-main
   (fn [{:keys [state locals]}]
     (let [{:keys [config]} @state
           {:keys [managers createDate]} config
           {:keys [body-id]} @locals
           make-block (fn [title body]
                        [:div {:style {:flexBasis "50%" :paddingRight "2rem" :marginBottom "2rem"}}
                         [:div {:style {:paddingBottom "0.5rem"}}
                          (style/create-subsection-header title)]
                         (style/create-subsection-contents body)])]
       [:div {:style {:flex "1 1 auto" :overflow "hidden"} :id body-id}
        [:div {:style {:display "flex"}}
         (make-block
          (str "Method Owner" (when (> (count managers) 1) "s"))
          (string/join ", " managers))

         (make-block
          "Created"
          (common/format-date createDate))]]))})

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
          (not configs) [:div {:style {:textAlign "center" :padding "1rem"}}
                         [comps/Spinner {:text "Loading configs..."}]]
          config-id [ConfigViewer (utils/restructure config-id config-snapshot-id)]
          :else
          (render-config-table (utils/restructure make-config-link-props configs)))]))
   :component-will-mount
   (fn [{:keys [props this]}]
     (when-not (:config-id props)
       (this :refresh)))
   :refresh
   (fn [{:keys [props state]}]
     (swap! state dissoc :configs :configs-error)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-agora-method-configs (:method-id props))
       :on-done (net/handle-ajax-response
                 (fn [{:keys [success? parsed-response]}]
                   (if success?
                     (let [configs (map #(assoc % :payload (utils/parse-json-string (:payload %) true)) parsed-response)]
                       (swap! state assoc :configs configs))
                     (swap! state assoc :configs-error (:message parsed-response)))))}))})
