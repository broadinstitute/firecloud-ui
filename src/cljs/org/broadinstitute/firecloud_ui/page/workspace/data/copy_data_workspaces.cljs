(ns org.broadinstitute.firecloud-ui.page.workspace.data.copy-data-workspaces
  (:require
    [clojure.string]
    [dmohs.react :as react]
    [clojure.set :refer [union]]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.page.workspace.data.copy-data-entities :as copy-data-entities]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

(react/defc WorkspaceList
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:margin "1em"}}
      [table/Table
       {:empty-message "There are no workspaces to display."
        :columns [{:header "Billing Project" :starting-width 150}
                  {:header "Name" :starting-width 150
                   :as-text #(get-in % ["workspace" "name"]) :sort-by :text
                   :content-renderer
                   (fn [ws]
                     (style/create-link {:text (get-in ws ["workspace" "name"])
                                         :onClick #((:onWorkspaceSelected props) ws)}))}
                  {:header "Created By" :starting-width 200}
                  (table/date-column {})
                  {:header "Access Level" :starting-width 106}
                  {:header "Protected" :starting-width 86
                   :content-renderer #(if % "Yes" "No")}]
        :toolbar (fn [built-in]
                   [:div {}
                    [:div {:style {:float "left"}} built-in]
                    (let [num (:num-filtered props)]
                      (when (pos? num)
                        [:div {:style {:float "left" :margin "7px 0 0 1em"}}
                         (str
                           (:num-filtered props)
                           " workspace(s) unavailable because they contain protected data.")]))
                    (common/clear-both)])
        :data (:workspaces props)
        :->row (fn [ws]
                 [(get-in ws ["workspace" "namespace"])
                  ws
                  (get-in ws ["workspace" "createdBy"])
                  (get-in ws ["workspace" "createdDate"])
                  (ws "accessLevel")
                  (get-in ws ["workspace" "isProtected"])])}]])})

(defn- remove-self [workspace-id workspace-list]
  (filter #(not= workspace-id {:namespace (get-in % ["workspace" "namespace"])
                               :name (get-in % ["workspace" "name"])}) workspace-list))

(defn- filter-workspaces [this-realm workspace-list]
  (filter #(let [src-realm (get-in % ["workspace" "realm" "groupName"])]
             (or (nil? src-realm) (= src-realm this-realm)))
    workspace-list))

(defn- workspace->id [workspace]
  {:namespace (get-in workspace ["workspace" "namespace"])
   :name (get-in workspace ["workspace" "name"])})

(react/defc Page
  {:render
   (fn [{:keys [state props]}]
     (let [selected-workspace (:selected-workspace (first (:crumbs props)))]
       (cond
         selected-workspace
         [copy-data-entities/SelectType {:workspace-id (:workspace-id props)
                                         :selected-workspace-id (workspace->id selected-workspace)
                                         :crumbs (rest (:crumbs props))
                                         :add-crumb (:add-crumb props)
                                         :reload-data-tab (:reload-data-tab props)}]
         (:workspaces @state)
         [WorkspaceList {:workspaces (:workspaces @state)
                         :num-filtered (:num-filtered @state)
                         :onWorkspaceSelected
                         (fn [ws]
                           ((:add-crumb props)
                            {:text (str (get-in ws ["workspace" "namespace"]) "/"
                                        (get-in ws ["workspace" "name"]))
                             :onClick #((:pop-to-depth props) 3)
                             :selected-workspace ws}))}]
         (:error-message @state) (style/create-server-error-message (:error-message @state))
         :else [:div {:style {:textAlign "center"}}
                [comps/Spinner {:text "Loading workspaces..."}]])))
   :component-did-mount
   (fn [{:keys [state props]}]
     (endpoints/call-ajax-orch
       {:endpoint endpoints/list-workspaces
        :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                   (if success?
                     (let [all-workspaces (remove-self (:workspace-id props) (get-parsed-response false))
                           filtered-workspaces (filter-workspaces (:this-realm props) all-workspaces)]
                       (swap! state assoc :workspaces filtered-workspaces
                         :num-filtered (- (count all-workspaces) (count filtered-workspaces))))
                     (swap! state assoc :error-message status-text)))}))})
