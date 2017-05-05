(ns broadfcui.page.workspace.data.tab
  (:require
    [dmohs.react :as react]
    [clojure.set :refer [union]]
    clojure.string
    goog.net.cookies
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.entity-table :refer [EntityTable]]
    [broadfcui.common.gcs-file-preview :refer [GCSFilePreviewLink]]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.table-utils :as table-utils]
    [broadfcui.common.style :as style]
    [broadfcui.config :as config]
    [broadfcui.page.workspace.data.copy-data-workspaces :as copy-data-workspaces]
    [broadfcui.page.workspace.data.entity-viewer :refer [EntityViewer]]
    [broadfcui.page.workspace.data.import-data :as import-data]
    [broadfcui.page.workspace.data.utils :as data-utils]
    [broadfcui.persistence :as persistence]
    [broadfcui.utils :as utils]
    ))

(react/defc MetadataImporter
  {:get-initial-state
   (fn [{:keys [state]}]
     {:crumbs [{:text "Choose Source"
                :onClick #(swap! state update :crumbs (comp vec (partial take 1)))}]})
   :render
   (fn [{:keys [state props]}]
     [comps/OKCancelForm
      {:header "Import Metadata" :show-cancel? false
       :content
       (let [last-crumb-id (:id (second (:crumbs @state)))
             add-crumb (fn [id text]
                         (swap! state update :crumbs conj
                                {:id id :text text
                                 :onClick #(swap! state update :crumbs (comp vec (partial take 2)))}))]
         [:div {:style {:position "relative"}}
          [:div {:style {:fontSize "1.1rem" :marginBottom "1rem"}}
           [:span {:style {:display "inline-block"}} [comps/Breadcrumbs {:crumbs (:crumbs @state)}]]
           (when-not last-crumb-id
             (common/render-info-box
              {:text [:div {} "For more information about importing files, see our "
                      [:a {:href (config/user-guide-url) :target "_blank"} "user guide." icons/external-link-icon]]}))]
          [:div {:style {:backgroundColor "white" :padding "1em"}}
           (case last-crumb-id
             :file-import
             [import-data/Page
              (select-keys props [:workspace-id :import-type :on-data-imported])]
             :workspace-import
             [copy-data-workspaces/Page
              (assoc (select-keys props [:workspace-id :this-auth-domain :on-data-imported])
                :crumbs (drop 2 (:crumbs @state))
                :add-crumb #(swap! state update :crumbs conj %)
                :pop-to-depth #(swap! state update :crumbs subvec 0 %))]
             (let [style {:width 240 :margin "0 1rem" :textAlign "center" :cursor "pointer"
                          :backgroundColor (:button-primary style/colors)
                          :color "#fff" :padding "1rem" :borderRadius 8}]
               [:div {:style {:display "flex" :justifyContent "center"}}
                [:div {:style style :onClick #(add-crumb :file-import "File")}
                 "Import from file"]
                [:div {:style style :onClick #(add-crumb :workspace-import "Choose Workspace")}
                 "Copy from another workspace"]]))]])}])})


(react/defc WorkspaceData
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [workspace-id workspace workspace-error]} props
           update-parent-state (partial this :update-state)]
       [:div {:style {:padding "1rem 1.5rem" :display "flex"}}
        (when (:loading-attributes @state)
          [comps/Blocker {:banner "Loading..."}])
        (cond
          workspace-error
          (style/create-server-error-message workspace-error)
          workspace
          (let [locked? (get-in workspace [:workspace :isLocked])
                this-auth-domain (get-in workspace [:workspace :authorizationDomain :membersGroupName])
                entity-renderer (fn [e]
                                  (let [entity-name (str (:name e))
                                        entity-type (str (:entityType e))
                                        last-entity (str (:selected-entity @state))
                                        last-entity-type (:selected-entity-type @state)]
                                    (style/create-link
                                     {:text entity-name
                                      :onClick #(if (and (= entity-name last-entity) (= entity-type last-entity-type))
                                                  (swap! state dissoc :selected-entity-type :selected-attr-list
                                                         :selected-entity :loading-attributes)
                                                  (do (swap! state assoc :selected-entity-type entity-type :selected-attr-list nil
                                                             :loading-attributes true :selected-entity entity-name)
                                                      (data-utils/get-entity-attrs (utils/restructure entity-name entity-type workspace-id update-parent-state))))})))]
            [:div {:style {:flex "1" :width 0}}
             [EntityTable
              {:ref "entity-table"
               :workspace-id workspace-id
               :column-defaults
               (try
                 (utils/parse-json-string (get-in workspace [:workspace :workspace-attributes
                                                             :workspace-column-defaults]))
                 (catch js/Object e
                   (utils/jslog e) nil))
               :toolbar
               (fn [built-in]
                 (let [layout (fn [item] [:div {:style {:marginRight "1em"}}] item)]
                   [:div {:style {:display "flex" :alignItems "center" :marginBottom "1em"}}
                    (map layout (vals built-in))
                    (when-let [selected-entity-type (some-> (:selected-entity-type @state) name)]
                      [:form {:target "_blank"
                              :method "POST"
                              :action (str (config/api-url-root) "/cookie-authed/workspaces/"
                                           (:namespace workspace-id) "/"
                                           (:name workspace-id) "/entities/" selected-entity-type "/tsv")}
                       [:input {:type "hidden"
                                :name "FCtoken"
                                :value (utils/get-access-token)}]
                       [:input {:type "hidden"
                                :name "attributeNames"
                                :value (->> (persistence/try-restore
                                             {:key (str (common/workspace-id->string
                                                         workspace-id) ":data:" selected-entity-type)
                                              :initial (constantly {})})
                                            :column-meta
                                            (filter :visible?)
                                            (map :header)
                                            (clojure.string/join ","))}]
                       [:input {:style {:border "none" :backgroundColor "transparent" :cursor "pointer"
                                        :color (:button-primary style/colors) :fontSize "inherit" :fontFamily "inherit"
                                        :padding 0 :marginLeft "1em"}
                                :type "submit"
                                :value (str "Download '" selected-entity-type "' metadata")}]])
                    [:div {:style {:flexGrow 1}}]
                    [:div {:style {:paddingRight ".5em"}}
                     [comps/Button {:text "Import Metadata..."
                                    :disabled? (when locked? "This workspace is locked.")
                                    :onClick #(this :-handle-import-data-click)}]]]))
               :on-filter-change #(swap! state assoc :selected-entity-type % :selected-entity nil :selected-attr-list nil)
               :attribute-renderer (table-utils/render-gcs-links (get-in workspace [:workspace :bucketName]))
               :linked-entity-renderer
               (fn [entity]
                 (if (map? entity)
                   (entity-renderer entity)
                   (:entity-Name entity)))
               :entity-name-renderer entity-renderer}]])
          :else
          [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Checking workspace..."}]])
        (when (:selected-entity @state)
          (let [{:keys [selected-entity-type selected-entity selected-attr-list]} @state]
            [EntityViewer {:workspace-id workspace-id :entity-type selected-entity-type :entity-name selected-entity
                           :attr-list selected-attr-list :update-parent-state (partial this :update-state)}]))]))
   :component-did-mount
   (fn [{:keys [props]}]
     ((:request-refresh props)))
   :-handle-import-data-click
   (fn [{:keys [props state refs]}]
     (modal/push-modal
      [MetadataImporter
       (merge
        (select-keys props [:workspace-id])
        {:this-auth-domain (get-in props [:workspace :workspace :authorizationDomain :membersGroupName])
         :import-type "data"
         :on-data-imported #(react/call :refresh (@refs "entity-table")
                                        (or % (:selected-entity-type @state)) true)})]))
   :update-state
   (fn [{:keys [state]} & args]
     (apply swap! state assoc args))})
