(ns broadfcui.page.groups.groups-management
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.flex-utils :as flex]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.common.table.table :refer [Table]]
    [broadfcui.common.table.style :as table-style]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.nav :as nav]
    [broadfcui.page.groups.create-group :refer [CreateGroupDialog]]
    [broadfcui.page.groups.manage-group :refer [GroupManagementPage]]
    [broadfcui.page.workspace.monitor.common :as moncommon]
    [broadfcui.utils :as utils]
    ))


(react/defc GroupTable
  {:reload
   (fn [{:keys [this]}]
     (react/call :load-data this))
   :render
   (fn [{:keys [state this]}]
     (cond
       (:error-message @state) (style/create-server-error-message (:error-message @state))
       (nil? (:groups @state)) [comps/Spinner {:text "Loading groups..."}]
       :else
       [Table
        {:data (:groups @state)
         :body {:behavior {:reorderable-columns? false}
                :style table-style/table-light
                :columns
                [{:header "Group Name" :initial-width 500 :sort-initial :asc
                  :sort-by :text
                  :as-text
                  (fn [{:keys [managedGroupRef]}]
                    (:usersGroupName managedGroupRef))
                  :render
                  (fn [{:keys [managedGroupRef accessLevel]}]
                    (let [group-name (:usersGroupName managedGroupRef)]
                      (if
                       (= accessLevel "Owner")
                        (style/create-link {:text group-name
                                            :href (nav/get-link :group group-name)})
                        group-name)))}
                 {:header "Access Level" :initial-width :auto :column-data :accessLevel}]}
         :toolbar
         {:items
          [flex/spring
           [comps/Button
            {:text "Create New Group"
             :onClick
             (fn []
               (modal/push-modal
                [CreateGroupDialog
                 {:on-success #(react/call :reload this)}]))}]]}}]))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-data this))
   :load-data
   (fn [{:keys [state]}]
     (endpoints/get-groups
      (fn [err-text groups]
        (if err-text
          (swap! state assoc :error-message err-text)
          (swap! state assoc :groups groups)))))
   :-handle-status-change
   (fn [{:keys [state]} group-name new-status message]
     (let [group-index (utils/first-matching-index
                        #(= (:groupName %) group-name)
                        (:groups @state))
           group (get-in @state [:groups group-index])
           updated-group (assoc group :creationStatus new-status :message message)]
       (swap! state assoc-in [:groups group-index] updated-group)))})


(react/defc Page
  {:render
   (fn [{:keys [props]}]
     (let [{:keys [group-name]} props]
       [:div {:style {:padding "1em"}}
        [:div {:style {:marginBottom "1rem" :fontSize "1.1rem"}}
         [comps/Breadcrumbs {:crumbs [{:text "Group Management" :href (nav/get-link :groups)}
                                      (when group-name {:text group-name})]}]]
        (if group-name
          [GroupManagementPage (utils/restructure group-name)]
          [GroupTable])]))})

(defn add-nav-paths []
  (nav/defpath
    :groups
    {:component Page
     :regex #"groups"
     :make-props (fn [_] {})
     :make-path (fn [] "groups")})
  (nav/defpath
    :group
    {:component Page
     :regex #"groups/([^/]+)"
     :make-props (fn [group-name] (utils/restructure group-name))
     :make-path (fn [group-name] (str "groups/" group-name))}))
