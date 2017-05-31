(ns broadfcui.page.workspace.workspace-common
  (:require
    [broadfcui.common.table :as table]
    [broadfcui.common.table-utils :refer [default-toolbar-layout]]
    [broadfcui.common.style :as style]
    [broadfcui.utils :as utils]
    ))


(defn workspace-selector [{:keys [workspaces on-workspace-selected toolbar-items]}]
  (assert workspaces "No workspaces given")
  (assert on-workspace-selected "on-workspace-selected not provided")
  [table/Table
   {:empty-message "There are no workspaces to display."
    :reorderable-columns? false
    :columns [{:header "Billing Project" :starting-width 150}
              {:header "Name" :starting-width 150
               :as-text #(get-in % [:workspace :name]) :sort-by :text
               :content-renderer
               (fn [ws]
                 (style/create-link {:text (get-in ws [:workspace :name])
                                     :onClick #(on-workspace-selected ws)}))}
              {:header "Created By" :starting-width 200}
              (table/date-column {})
              {:header "Access Level" :starting-width 106}
              {:header "Authorization Domain" :starting-width 150
               :content-renderer #(or % "None")}]
    :toolbar (apply default-toolbar-layout toolbar-items)
    :data workspaces
    :->row (fn [ws]
             (let [workspace (:workspace ws)]
               [(:namespace workspace)
                ws
                (:createdBy workspace)
                (:createdDate workspace)
                (:accessLevel ws)
                (get-in workspace [:authorizationDomain :membersGroupName])]))}])
