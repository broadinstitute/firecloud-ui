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
    [org.broadinstitute.firecloud-ui.utils :as utils :refer [access-token]]))



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
          [:div {:style {:backgroundColor "white" :padding "1em"}}
           (case last-crumb-id
             :file-import
             [import-data/Page (select-keys props [:workspace-id :reload-data-tab])]
             :workspace-import
             [copy-data-workspaces/Page
              (assoc (select-keys props [:workspace-id :this-realm :reload-data-tab])
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
              :column-defaults (utils/parse-json-string (get-in workspace [:workspace :workspace-attributes :workspace-column-defaults]))
              :toolbar (fn [built-in]
                         [:div {}
                          [:div {:style {:float "left"}} built-in]
                          (when-let [selected-entity-type (:selected-entity-type @state)]
                            [:a {:style {:textDecoration "none" :float "left" :margin "7px 0 0 1em"}
                                 :href (str (config/api-url-root) "/cookie-authed/workspaces/"
                                            (:namespace workspace-id) "/"
                                            (:name workspace-id) "/entities/" selected-entity-type "/tsv")
                                 :onClick #(utils/set-access-token-cookie @access-token)
                                 :target "_blank"}
                             (str "Download '" selected-entity-type "' data")])
                          [:div {:style {:float "right" :paddingRight "2em"}}
                           [comps/Button {:text "Import Data..."
                                          :disabled? (when locked? "This workspace is locked.")
                                          :onClick #(modal/push-modal
                                                     [DataImporter {:workspace-id workspace-id
                                                                    :this-realm this-realm
                                                                    :reload-data-tab
                                                                    (fn [entity-type]
                                                                      ((:request-refresh props))
                                                                      (react/call :refresh (@refs "entity-table") entity-type))}])}]]
                          (common/clear-both)])
              :on-filter-change #(swap! state assoc :selected-entity-type %)
              :attribute-renderer (table-utils/render-gcs-links (get-in workspace [:workspace :bucketName]))}])
          :else
          [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Checking workspace..."}]])]))
   :component-did-mount
   (fn [{:keys [props]}]
     ((:request-refresh props)))})
