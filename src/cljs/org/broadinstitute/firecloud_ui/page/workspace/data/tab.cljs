(ns org.broadinstitute.firecloud-ui.page.workspace.data.tab
  (:require
    [dmohs.react :as react]
    [clojure.set :refer [union]]
    clojure.string
    goog.net.cookies
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.entity-table :as entity-table :refer [EntityTable]]
    [org.broadinstitute.firecloud-ui.common.gcs-file-preview :refer [GCSFilePreviewLink]]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.table-utils :as table-utils]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.config :as config]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.workspace.data.copy-data-workspaces :as copy-data-workspaces]
    [org.broadinstitute.firecloud-ui.page.workspace.data.import-data :as import-data]
    [org.broadinstitute.firecloud-ui.persistence :as persistence]
    [org.broadinstitute.firecloud-ui.utils :as u]
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

(defn- get-entity-attrs [entity-name entity-type workspace-id state]
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
                       (swap! state assoc :attr-list items :loading-attributes false))
                     (swap! state assoc :attr-list (:attributes (get-parsed-response true)) :loading-attributes false))
                   (swap! state assoc :server-error (get-parsed-response false) :loading-attributes false)))})))

(react/defc DataImporter
  {:get-initial-state
   (fn [{:keys [state]}]
     {:crumbs [{:text "Choose Source"
                :onClick #(swap! state update-in [:crumbs] (comp vec (partial take 1)))}]})
   :render
   (fn [{:keys [state props]}]
     [modal/OKCancelForm
      {:header "Import Data"
       :show-cancel? false :ok-button {:text "Done" :onClick modal/pop-modal}
       :content
       (let [last-crumb-id (:id (second (:crumbs @state)))
             add-crumb (fn [id text]
                         (swap! state update-in [:crumbs] conj
                                {:id id :text text
                                 :onClick #(swap! state update-in [:crumbs] (comp vec (partial take 2)))}))]
         [:div {:style {:position "relative" :minWidth 500}}
          [:div {:style {:fontSize "150%" :marginBottom "1ex"}}
           [comps/Breadcrumbs {:crumbs (:crumbs @state)}]]
          common/PHI-warning
          [:div {:style {:backgroundColor "white" :padding "1em"}}
           (case last-crumb-id
             :file-import
             [import-data/Page (select-keys props [:workspace-id :reload :import-type])]
             :workspace-import
             [copy-data-workspaces/Page
              (assoc (select-keys props [:workspace-id :this-realm :reload])
                :crumbs (drop 2 (:crumbs @state))
                :add-crumb #(swap! state update-in [:crumbs] conj %)
                :pop-to-depth #(swap! state update-in [:crumbs] subvec 0 %))]
             (let [style {:width 240 :margin "auto" :textAlign "center" :cursor "pointer"
                          :backgroundColor (:button-primary style/colors)
                          :color "#fff" :padding "1em" :borderRadius 8}]
               [:div {}
                [:div {:style style :onClick #(add-crumb :file-import "File")}
                 "Import from file"]
                [:div {:style {:height "1em"}}]
                [:div {:style style :onClick #(add-crumb :workspace-import "Choose Workspace")}
                 "Copy from another workspace"]]))]])}])})

(react/defc EntityAttributes
  {:render
   (fn [{:keys [state props]}]
     (let [entity-type (:entity-type props)
           entity-name (:entity-name props)
           attributes (:attr-list props)
           item-column-name (get-column-name entity-type)
           setColumns [{:header item-column-name :starting-width 320 :sort-initial :asc :sort-by :text
                        :as-text (fn [x] (:entityName x)) :content-renderer (fn [x] x)}]
           singleColumns [{:header "Attribute" :starting-width 120 :sort-initial :asc}
                          {:header "Value" :starting-width :remaining
                           :content-renderer (fn [attr-value]
                                               (if-let [parsed (common/parse-gcs-uri attr-value)]
                                                 [GCSFilePreviewLink (assoc parsed
                                                                       :attributes {:style {:display "inline"}}
                                                                       :link-label attr-value)]
                                                 attr-value))}]]
       [:div {:style {:flexBasis (if attributes "20%" 0) :ref "entity-attributes-list"}}
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
                            (if (map? x) ;map is a sample_set or pair_set or participant_set
                              (let [item (str (:entityName x))
                                    last-entity (str (:selected-entity @state))
                                    item-type (clojure.string/lower-case (get-column-name entity-type))]
                                [(style/create-link
                                  {:text item
                                   :onClick
                                   #(if (= item last-entity)
                                      (swap! (:mainState props) assoc :current-entity-type nil :attr-list nil
                                             :selected-entity nil :loading-attributes false)
                                      (do (swap! (:mainState props) assoc :current-entity-type item-type :attr-list nil
                                                 :loading-attributes true :selected-entity item)
                                          (get-entity-attrs item item-type (:workspace-id props) (:mainState props))))})])
                              (let [nkey (str (first x))
                                    last-entity (str (:selected-entity @state))
                                    last-entity-type (str :current-entity-type @state)
                                    name (clojure.string/replace nkey #":" "")
                                    item (val x)
                                    entity-type (str (:entityType item))]
                                [[name] (cond
                                          (entity-table/is-single-ref? item)
                                          (do
                                            (if (map? item)
                                              (style/create-link
                                               {:text entity-name :title entity-name
                                                :onClick
                                                #(if (and (= entity-name last-entity) (= entity-type last-entity-type))
                                                   (swap! (:mainState props) assoc :current-entity-type nil :attr-list nil
                                                          :selected-entity nil :loading-attributes false)
                                                   (do (swap! (:mainState props) assoc :current-entity-type entity-type
                                                              :attr-list nil :loading-attributes true
                                                              :selected-entity entity-name)
                                                       (get-entity-attrs entity-name entity-type
                                                                         (:workspace-id props) (:mainState props))))})
                                              (:entityName item)))
                                          (common/attribute-list? item)
                                          (let [items (map render-list-item (common/attribute-values item))]
                                            (if (empty? items)
                                              "0 items"
                                              (str (count items) " items: " (clojure.string/join ", " items))))
                                          :else item)])))}]])]]))})


(react/defc WorkspaceData
  {:get-initial-state
   (fn [_] {:selected-entity-type nil :attr-list nil :current-entity-type nil
            :loading-attributes false})
   :render
   (fn [{:keys [props state refs]}]
     (let [{:keys [workspace-id workspace workspace-error]} props]
       [:div {:style {:padding "1em" :display "flex"}}
        (when (:loading-attributes @state)
          [comps/Blocker {:banner "Loading..."}])
        (cond
          workspace-error
          (style/create-server-error-message workspace-error)
          workspace
          (let [locked? (get-in workspace [:workspace :isLocked])
                this-realm (get-in workspace [:workspace :realm :groupName])]
            [EntityTable
             {:ref "entity-table"
              :workspace-id workspace-id
              :column-defaults (try
                                 (u/parse-json-string (get-in workspace [:workspace :workspace-attributes
                                                                         :workspace-column-defaults]))
                                 (catch js/Object e
                                   (u/jslog e) nil))
              :toolbar (fn [built-in]
                         (let [layout (fn [item] [:div {:style {:marginRight "1em"}}] item)]
                           [:div {:style {:display "flex" :alignItems "center" :marginBottom "1em"}}
                            (map layout (vals built-in))
                            (when-let [selected-entity-type (some-> (:selected-entity-type @state) name)]
                              [:a {:style {:textDecoration "none" :margin "7px .3em 0 0"}
                                   :href (str (config/api-url-root) "/cookie-authed/workspaces/"
                                              (:namespace workspace-id) "/"
                                              (:name workspace-id) "/entities/" selected-entity-type "/tsv"
                                              "?attributeNames="
                                              (->> (persistence/try-restore
                                                    {:key (str (common/workspace-id->string workspace-id) ":data:" selected-entity-type)
                                                     :initial (constantly {})})
                                                   :column-meta
                                                   (filter :visible?)
                                                   (map :header)
                                                   (clojure.string/join ",")))
                                   :onClick #(u/set-access-token-cookie (u/get-access-token))
                                   :target "_blank"}
                               (str "Download '" (clojure.string/replace selected-entity-type ":" "") "' data")])
                            [:div {:style {:flexGrow 1}}]
                            [:div {:style {:paddingRight ".5em"}}
                             [comps/Button {:text "Import Data..."
                                            :disabled? (when locked? "This workspace is locked.")
                                            :onClick #(modal/push-modal
                                                       [DataImporter {:workspace-id workspace-id
                                                                      :this-realm this-realm
                                                                      :import-type "data"
                                                                      :reload
                                                                      (fn [entity-type]
                                                                        ((:request-refresh props))
                                                                        (react/call :refresh (@refs "entity-table") entity-type))}])}]]]))
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
                                                              (get-entity-attrs entity-name entity-type (:workspace-id props) state)))})
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
                                                          (get-entity-attrs entity-name entity-type (:workspace-id props) state)))
                                          })))}])
          :else
          [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Checking workspace..."}]])
        (let [workspaceId (:workspace-id props)
              entityType (:current-entity-type @state)
              entityName (:selected-entity @state)
              attributes (:attr-list @state)]
          [EntityAttributes {:workspace-id workspaceId :entity-type entityType :entity-name entityName
                             :attr-list attributes :mainState state}])]
       ))
   :component-did-mount
   (fn [{:keys [props]}]
     ((:request-refresh props)))})
