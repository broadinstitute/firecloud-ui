(ns broadfcui.page.workspace.workspace-common
  (:require
    [broadfcui.common.table.style :as table-style]
    [broadfcui.common.table.table :refer [Table]]
    [broadfcui.common.table.utils :as table-utils]
    [broadfcui.common.style :as style]
    [broadfcui.utils :as utils]
    ))


(defn workspace-selector [{:keys [workspaces on-workspace-selected toolbar-items]}]
  (assert workspaces "No workspaces given")
  (assert on-workspace-selected "on-workspace-selected not provided")
  [Table
   {:data workspaces
    :body {:empty-message "There are no workspaces to display."
           :style table-style/table-heavy
           :behavior {:reorderable-columns? false}
           :columns
           [{:header "Billing Project" :initial-width 150
             :column-data (comp :namespace :workspace)}
            {:header "Name" :initial-width 150
             :as-text (comp :name :workspace) :sort-by :text
             :render (fn [ws]
                       (style/create-link {:text (get-in ws [:workspace :name])
                                           :onClick #(on-workspace-selected ws)}))}
            {:header "Created By" :initial-width 200
             :column-data (comp :createdBy :workspace)}
            (table-utils/date-column {:column-data (comp :createdDate :workspace)})
            {:header "Access Level" :initial-width 106
             :column-data :accessLevel}
            {:header "Authorization Domain" :starting-width 150
             :column-data (comp :membersGroupName :authorizationDomain :workspace)
             :render #(or % "None")}]}
    :toolbar {:items toolbar-items}}])
