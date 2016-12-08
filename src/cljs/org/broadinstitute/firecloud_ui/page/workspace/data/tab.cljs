(ns org.broadinstitute.firecloud-ui.page.workspace.data.tab
  (:require
    [dmohs.react :as react]
    [clojure.set :refer [union]]
    goog.net.cookies
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.entity-table :refer [EntityTable]]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.table-utils :as table-utils]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.config :as config]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.workspace.data.copy-data-workspaces :as copy-data-workspaces]
    [org.broadinstitute.firecloud-ui.page.workspace.data.import-data :as import-data]
    [org.broadinstitute.firecloud-ui.persistence :as persistence]
    [org.broadinstitute.firecloud-ui.utils :as u]
    ))


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
          [:div {:style {:fontWeight "bold" :fontSize "106%"}}
            [:img {:src "assets/Warning.svg" :style {:verticalAlign "middle" :marginRight ".32em"}}]
            (str "FireCloud is not intended to host personally identifiable information. Do not use any patient identifier, including name, social security number, or medical record number.")]
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


(react/defc WorkspaceData
  {:render
   (fn [{:keys [props state refs]}]
     (let [{:keys [workspace-id workspace workspace-error]} props]
       [:div {:style {:padding "1em"}}
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
                                 (u/parse-json-string (get-in workspace [:workspace :workspace-attributes :workspace-column-defaults]))
                                 (catch js/Object e
                                   (u/jslog e) nil))
              :toolbar (fn [built-in]
                         [:div {:style {:display "flex" :justifyContent "flex-start" :alignItems "baseline"}}
                          [:div {} built-in]
                          (when-let [selected-entity-type (some-> (:selected-entity-type @state) name)]
                            [:a {:style {:textDecoration "none" :margin "7px 0 0 1em"}
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
                             (str "Download '" selected-entity-type "' data")])
                          [:div {:style {:flexGrow 1}}]
                          [:div {:style {:paddingRight "2em"}}
                           [comps/Button {:text "Import Data..."
                                          :disabled? (when locked? "This workspace is locked.")
                                          :onClick #(modal/push-modal
                                                     [DataImporter {:workspace-id workspace-id
                                                                    :this-realm this-realm
                                                                    :import-type "data"
                                                                    :reload
                                                                    (fn [entity-type]
                                                                      ((:request-refresh props))
                                                                      (react/call :refresh (@refs "entity-table") entity-type))}])}]]])
              :on-filter-change #(swap! state assoc :selected-entity-type %)
              :attribute-renderer (table-utils/render-gcs-links (get-in workspace [:workspace :bucketName]))}])
          :else
          [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Checking workspace..."}]])]))
   :component-did-mount
   (fn [{:keys [props]}]
     ((:request-refresh props)))})
