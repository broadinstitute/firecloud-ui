(ns org.broadinstitute.firecloud-ui.page.workspace.data.tab
  (:require
    [dmohs.react :as react]
    [clojure.set :refer [union]]
    goog.net.cookies
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
    [org.broadinstitute.firecloud-ui.common.entity-table :refer [EntityTable]]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.table-utils :as table-utils]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.config :as config]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.workspace.data.copy-data-workspaces :as copy-data-workspaces]
    [org.broadinstitute.firecloud-ui.page.workspace.data.import-data :as import-data]
    [org.broadinstitute.firecloud-ui.utils :as utils :refer [access-token]]
    ))


(react/defc DataImporter
  {:get-initial-state
   (fn [{:keys [state]}]
     {:crumbs [{:text "Choose Source"
                :onClick #(swap! state update-in [:crumbs] (comp vec (partial take 1)))}]})
   :render
   (fn [{:keys [state props]}]
     [dialog/OKCancelForm
      {:header "Import Data" :dismiss-self modal/pop-modal
       :show-cancel? false :ok-button [comps/Button {:text "Done" :onClick modal/pop-modal}]
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
                         :backgroundColor (:button-blue style/colors)
                         :color "#fff" :padding "1em" :borderRadius 8}]
              [:div {}
               [:div {:style style :onClick #(add-crumb :file-import "File")}
                "Import from file"]
               [:div {:style {:height "1em"}}]
               [:div {:style style :onClick #(add-crumb :workspace-import "Choose Workspace")}
                "Copy from another workspace"]]))]])}])})


(react/defc WorkspaceData
  {:refresh
   (fn [{:keys [state this]} & [entity-type]]
     (swap! state dissoc :server-response)
     (react/call :load this entity-type))
   :render
   (fn [{:keys [props state this]}]
     (let [workspace-id (:workspace-id props)
           server-response (:server-response @state)
           {:keys [server-error locked? this-realm]} server-response]
       [:div {:style {:padding "1em"}}
        (cond
          server-error
          (style/create-server-error-message server-error)
          (nil? locked?)
          [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Checking workspace..."}]]
          :else
          [EntityTable
           {:ref "entity-table"
            :workspace-id workspace-id
            :toolbar (fn [built-in]
                       [:div {}
                        [:div {:style {:float "left"}} built-in]
                        (when-let [selected-entity-type (:selected-entity-type @state)]
                          (utils/log selected-entity-type)
                          [:a {:style {:textDecoration "none" :float "left" :margin "7px 0 0 1em"}
                               :href (str (config/api-url-root) "/cookie-authed/workspaces/"
                                          (:namespace workspace-id) "/"
                                          (:name workspace-id) "/entities/" selected-entity-type "/tsv")
                               :onClick #(utils/set-access-token-cookie @access-token)
                               :target "_blank"}
                           (str "Download '" selected-entity-type "' data")])
                        [:div {:style {:float "right" :paddingRight "2em"}}
                         [comps/Button {:text "Import Data..."
                                        :disabled? (when locked? "This workspace is locked")
                                        :onClick #(modal/push-modal
                                                   [DataImporter {:workspace-id workspace-id
                                                                  :this-realm this-realm
                                                                  :reload-data-tab (partial react/call :refresh this)}])}]]
                        (common/clear-both)])
            :initial-entity-type (get-in @state [:server-response :initial-entity-type])
            :on-filter-change #(swap! state assoc :selected-entity-type %)
            :attribute-renderer (fn [maybe-uri]
                                  (if (string? maybe-uri)
                                    (if-let [parsed (common/parse-gcs-uri maybe-uri)]
                                      [dialog/GCSFilePreviewLink parsed]
                                      maybe-uri)
                                    (table-utils/default-render maybe-uri)))}])]))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load this))
   :load
   (fn [{:keys [state props]} & [entity-type]]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (let [workspace ((get-parsed-response) "workspace")]
                       (swap! state update-in [:server-response] assoc
                              :locked? (workspace "isLocked")
                              :this-realm (get-in workspace ["realm" "groupName"])
                              :initial-entity-type entity-type))
                     (swap! state update-in [:server-response]
                       assoc :server-error status-text)))}))})
