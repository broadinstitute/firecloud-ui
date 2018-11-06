(ns broadfcui.page.workspace.workspace-common
  (:require
   [clojure.string :as string]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.common.table.utils :as table-utils]
   [broadfcui.common.icons :as icons]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   ))


(defn workspace->id [workspace]
  (select-keys (:workspace workspace) [:namespace :name]))


(defn workspace-selector [{:keys [workspaces on-workspace-selected toolbar-items]}]
  (assert workspaces "No workspaces given")
  (assert on-workspace-selected "on-workspace-selected not provided")
  [Table
   {:data-test-id "workspace-selector-table"
    :data workspaces
    :body {:empty-message "There are no workspaces to display."
           :style table-style/table-heavy
           :behavior {:reorderable-columns? false}
           :columns
           [{:header "Billing Project" :initial-width 150
             :column-data (comp :namespace :workspace)}
            {:header "Name" :initial-width 150
             :as-text (comp :name :workspace) :sort-by :text
             :render (fn [{:keys [workspace] :as ws}]
                       (links/create-internal {:data-test-id (str (:namespace workspace) "-" (:name workspace) "-link")
                                               :onClick #(on-workspace-selected ws)}
                         (:name workspace)))}
            {:header "Created By" :initial-width 200
             :column-data (comp :createdBy :workspace)}
            (table-utils/date-column {:column-data (comp :createdDate :workspace)})
            {:header "Access Level" :initial-width 106
             :column-data :accessLevel}
            {:header "Authorization Domain" :starting-width 150
             :column-data (comp :authorizationDomain :workspace)
             :as-text #(if (empty? %) "None" (string/join ", " (map :membersGroupName %)))
             :sort-by count}]}
    :toolbar {:get-items (constantly toolbar-items)}}])

(defn config->id [config]
  (select-keys config [:namespace :name]))

(defn- referenced-method-parts [config]
  (replace (:methodRepoMethod config) [:sourceRepo :methodNamespace :methodName :methodPath :methodVersion]))

(defn- method-as-text [config]
  (string/join "/" (referenced-method-parts config)))

(defn- method-as-maybe-link [config]
  (let [redacted? (:redacted? config)
        methodRepoMethod (:methodRepoMethod config)
        repo (:sourceRepo methodRepoMethod)
        coldata (referenced-method-parts config)]
    (if (or redacted? (not (= repo "agora")))
      (apply style/render-entity coldata)
      (let [method-id {:namespace (:methodNamespace methodRepoMethod)
                       :name (:methodName methodRepoMethod)
                       :snapshot-id (:methodVersion methodRepoMethod)}]
        (links/create-internal {:href (nav/get-link :method-loader method-id)} (apply style/render-entity coldata))))))

(defn method-config-selector [{:keys [data-test-id configs render-name toolbar-items]}]
  (assert configs "No configs given")
  (assert render-name "No name renderer given")
  [Table
   {:data-test-id (or data-test-id "method-configs-table")
    :data configs
    :body {:empty-message "There are no method configurations to display."
           :style table-style/table-heavy
           :columns [{:id "redacted" :hidden? true :resizable? false :sortable? false :filterable? false :initial-width 30
                      :as-text (fn [config]
                                 (when (:redacted? config)
                                   "The method snapshot referenced by this config has been redacted."))
                      :render (fn [config]
                                (when (:redacted? config)
                                  (icons/render-icon {:style {:alignSelf "center" :color (:state-warning style/colors)}} :warning)))}
                     {:header "Name" :initial-width 240
                      :as-text :name :sort-by :text
                      :sort-initial :asc
                      :render render-name}
                     {:header "Root Entity Type" :initial-width 140
                      :column-data :rootEntityType}
                     {:header "Method Source" :initial-width 130
                      :column-data #(let [repo (get-in % [:methodRepoMethod :sourceRepo])]
                                      (if (= repo "dockstore") "Dockstore" "FireCloud"))
                      :render (fn [repo] [:span {:style {:fontWeight 200}} repo])}
                     {:header "Method" :initial-width 800
                      :sort-by :text
                      :as-text method-as-text
                      :render method-as-maybe-link}]}
    :toolbar {:get-items (constantly toolbar-items)}}])


(def PHI-warning
  [:div {:style {:display "flex" :marginBottom ".5rem" :alignItems "center" :justifyContent "space-around"
                 :padding "1rem" :backgroundColor (:background-light style/colors)}}
   (icons/render-icon {:style {:fontSize 22 :color (:exception-state style/colors) :marginRight "1rem"}}
                      :alert)
   [:span {:style {:fontWeight 500}}
    "FireCloud is not intended to host personally identifiable information. Do not use any patient
     identifier, including name, social security number, or medical record number."]])
