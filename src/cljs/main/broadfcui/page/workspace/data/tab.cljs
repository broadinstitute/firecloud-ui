(ns broadfcui.page.workspace.data.tab
  (:require
    [dmohs.react :as react]
    [clojure.set :refer [union]]
    clojure.string
    goog.net.cookies
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.entity-table :as entity-table :refer [EntityTable]]
    [broadfcui.common.gcs-file-preview :refer [GCSFilePreviewLink]]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.table :as table]
    [broadfcui.common.table-utils :as table-utils]
    [broadfcui.common.style :as style]
    [broadfcui.config :as config]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.page.workspace.data.copy-data-workspaces :as copy-data-workspaces]
    [broadfcui.page.workspace.data.import-data :as import-data]
    [broadfcui.persistence :as persistence]
    [broadfcui.utils :as utils]
    ))

(defn- render-list-item [item]
  (if (entity-table/is-single-ref? item)
    (:entityName item)
    item))

(defn- get-column-name [entity-type]
  (case (str entity-type)
    "sample_set" "Sample"
    "participant_set" "Participant"
    "pair_set" "Pair"
    "Entity"))

(defn- is-entity-set? [entity-type]
  (or (= entity-type "sample_set") (= entity-type "pair_set") (= entity-type "participant_set") false))

(defn- get-entity-attrs [entity-name entity-type workspace-id update-main]
  (when (and (some? entity-name) (some? entity-type))
    (endpoints/call-ajax-orch
     {:endpoint (endpoints/get-entity workspace-id entity-type entity-name)
      :on-done (fn [{:keys [success? get-parsed-response]}]
                 (if success?
                   (if (is-entity-set? entity-type)
                     (let [attrs (:attributes (get-parsed-response true))
                           items (case entity-type
                                   "sample_set" (:items (:samples attrs))
                                   "pair_set" (:items (:pairs attrs))
                                   "participant_set" (:items (:participants attrs)))]
                       (update-main :attr-list items :loading-attributes false))
                     (update-main :attr-list (:attributes (get-parsed-response true)) :loading-attributes false))
                   (update-main :server-error (get-parsed-response false) :loading-attributes false)))})))

(react/defc DataImporter
  {:get-initial-state
   (fn [{:keys [state]}]
     {:crumbs [{:text "Choose Source"
                :onClick #(swap! state update :crumbs (comp vec (partial take 1)))}]})
   :render
   (fn [{:keys [state props]}]
     [comps/OKCancelForm
      {:header "Import Data" :show-cancel? false
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
                      [:a {:href (config/user-guide-url) :target "_blank"} "user guide"] "."]}))]
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

(react/defc EntityAttributes
  {:render
   (fn [{:keys [props]}]
     (let [update-main (:update-main props)
           entity-type (:entity-type props)
           entity-name (:entity-name props)
           attributes (:attr-list props)
           item-column-name (get-column-name entity-type)
           setColumns [{:header item-column-name :starting-width 320 :sort-initial :asc :sort-by :text
                        :as-text (fn [x] (:entityName x)) :content-renderer (fn [x] x)}]
           singleColumns [{:header "Attribute" :starting-width 120 :sort-initial :asc}
                          {:header "Value" :starting-width :remaining
                           :as-text :name :sort-by :text :resizable? false
                           :content-renderer (fn [attr-value]
                                               (if-let [parsed (common/parse-gcs-uri attr-value)]
                                                 [GCSFilePreviewLink (assoc parsed
                                                                       :attributes {:style {:display "inline"}}
                                                                       :link-label attr-value)]
                                                 attr-value))}]
           item-link (fn [item-type item-name]
                       (style/create-link
                        {:text item-name
                         :onClick
                         #(do (update-main :current-entity-type item-type :attr-list nil
                                           :loading-attributes true :selected-entity item-name)
                              (get-entity-attrs item-name item-type (:workspace-id props) update-main))}))]
       [:div {:style {:width (if attributes "30%" 0) :ref "entity-attributes-list"}}
        [:div {:style {:marginLeft ".38em"}}
         (when (some? attributes)
           [:div {:style {:fontWeight "bold" :padding "0.7em 0" :marginBottom "1em"}}
            (if (= item-column-name "Entity") (str entity-name "  Attributes:")
                                              (str entity-name "  " item-column-name "s:"))])
         (when (some? attributes)
           [:div {}
            [table/Table {:reorderable-columns? false
                          :width :narrow
                          :pagination :none
                          :filterable? false
                          :initial-rows-per-page 500
                          :always-sort? true
                          :header-row-style {:borderBottom (str "2px solid " (:line-default style/colors))
                                             :backgroundColor "white" :color "black" :fontWeight "bold"}
                          :empty-message (if (= item-column-name "Entity") (str "No Entity Attributes defined")
                                                                           (str "No " item-column-name "s defined"))
                          :columns (if (is-entity-set? entity-type) setColumns singleColumns)
                          :data (seq attributes)
                          :->row
                          (fn [x]
                            (cond
                              (map? x)                      ; x is a member of a set
                              [(item-link (:entityType x) (:entityName x))]
                              (map? (last x))               ; x is a set
                              (let [item-type (:entityType (last x))]
                                [item-type
                                 (item-link item-type (:entityName (last x)))])
                              :else
                              (let [name (name (first x))
                                    item (last x)]
                                [name
                                 (if (common/attribute-list? item)
                                   (let [items (map render-list-item (common/attribute-values item))]
                                     (if (empty? items)
                                       "0 items"
                                       (str (count items) " items: " (clojure.string/join ", " items))))
                                   item)])))}]])]]))})


(react/defc WorkspaceData
  {:get-initial-state
   (fn [_] {:selected-entity-type nil :attr-list nil :current-entity-type nil
            :loading-attributes false})
   :render
   (fn [{:keys [props state refs this]}]
     (let [{:keys [workspace-id workspace workspace-error]} props]
       [:div {:style {:padding "1rem 1.5rem" :display "flex"}}
        (when (:loading-attributes @state)
          [comps/Blocker {:banner "Loading..."}])
        (cond
          workspace-error
          (style/create-server-error-message workspace-error)
          workspace
          (let [locked? (get-in workspace [:workspace :isLocked])
                this-auth-domain (get-in workspace [:workspace :authorizationDomain :membersGroupName])]
            [:div {:style {:flex "1" :width 0}}
             [EntityTable
              {:ref "entity-table"
               :workspace-id workspace-id
               :column-defaults (try
                                  (utils/parse-json-string (get-in workspace [:workspace :workspace-attributes
                                                                              :workspace-column-defaults]))
                                  (catch js/Object e
                                    (utils/jslog e) nil))
               :toolbar (fn [built-in]
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
                                         :value (str "Download '" selected-entity-type "' data")}]])
                             [:div {:style {:flexGrow 1}}]
                             [comps/Button {:text "Import Data..."
                                            :disabled? (when locked? "This workspace is locked.")
                                            :onClick #(this :-handle-import-data-click)}]]))
               :on-filter-change #(swap! state assoc :selected-entity-type % :selected-entity nil :attr-list nil)
               :attribute-renderer (table-utils/render-gcs-links (get-in workspace [:workspace :bucketName]))
               :linked-entity-renderer (fn [e]
                                         (let [entity-name (str (:entityName e))
                                               entity-type (str (:entityType e))
                                               last-entity (str (:selected-entity @state))
                                               last-entity-type (:current-entity-type @state)]
                                           (if (map? e)
                                             (style/create-link
                                              {:text entity-name
                                               :onClick #(if (and (= entity-name last-entity) (= entity-type last-entity-type))
                                                           (swap! state assoc :current-entity-type nil :attr-list nil
                                                                  :selected-entity nil :loading-attributes false)
                                                           (do (swap! state assoc :current-entity-type entity-type :attr-list nil
                                                                      :loading-attributes true :selected-entity entity-name)
                                                               (get-entity-attrs entity-name entity-type (:workspace-id props) (partial react/call :update-state this))))})
                                             entity-name)))
               :entity-name-renderer (fn [e]
                                       (let [entity-name (str (:name e))
                                             entity-type (str (:entityType e))
                                             last-entity (str (:selected-entity @state))
                                             last-entity-type (:current-entity-type @state)]
                                         (style/create-link
                                          {:text entity-name
                                           :onClick #(if (and (= entity-name last-entity) (= entity-type last-entity-type))
                                                       (swap! state assoc :current-entity-type nil :attr-list nil
                                                              :selected-entity nil :loading-attributes false)
                                                       (do (swap! state assoc :current-entity-type entity-type :attr-list nil
                                                                  :loading-attributes true :selected-entity entity-name)
                                                           (get-entity-attrs entity-name entity-type (:workspace-id props) (partial react/call :update-state this))))})))}]])
          :else
          [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Checking workspace..."}]])
        (let [workspaceId (:workspace-id props)
              entityType (:current-entity-type @state)
              entityName (:selected-entity @state)
              attributes (:attr-list @state)]
          [EntityAttributes {:workspace-id workspaceId :entity-type entityType :entity-name entityName
                             :attr-list attributes :update-main (partial react/call :update-state this)}])]))
   :component-did-mount
   (fn [{:keys [props]}]
     ((:request-refresh props)))
   :-handle-import-data-click
   (fn [{:keys [props state refs]}]
     (modal/push-modal
      [DataImporter
       (merge
        (select-keys props [:workspace-id])
        {:this-auth-domain (get-in props [:workspace :workspace :authorizationDomain :membersGroupName])
         :import-type "data"
         :on-data-imported #(react/call :refresh (@refs "entity-table")
                                        (or % (:selected-entity-type @state)) true)})]))
   :update-state
   (fn [{:keys [state]} & args]
     (apply swap! state assoc args))})
