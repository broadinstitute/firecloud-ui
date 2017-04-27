(ns broadfcui.page.groups.groups-management
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.flex-utils :as flex]
    [broadfcui.common.management-utils :refer [MembershipManagementPage]]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.common.table.table :refer [Table]]
    [broadfcui.common.table.style :as table-style]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.nav :as nav]
    [broadfcui.page.groups.create-group :refer [CreateGroupDialog]]
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
                  :as-text :group-name
                  :render
                  (fn [{:keys [group-name access-levels]}]
                    (if
                     (contains? (set access-levels) "Owner")
                      (style/create-link {:text group-name
                                          :href (nav/get-link :group group-name)})
                      group-name))}
                 {:header "Access Levels" :initial-width :auto
                  :column-data #(clojure.string/join ", " (:access-levels %))}]}
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
          (let [groups (->>
                        (map (fn [group]
                               {:group-name (get-in group [:managedGroupRef :usersGroupName])
                                :access-level (:accessLevel group)})
                             groups)
                        (group-by :group-name)
                        (map (fn [[k v]]
                               {:group-name k :access-levels (sort (map :access-level v))})))]
            (swap! state assoc :groups groups))))))
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
       [:div {:style {:padding "1rem 1rem 0"}}
        [:div {:style {:marginBottom "1rem" :fontSize "1.1rem"}}
         [:div {:style {:fontSize "1.2em"}} (when group-name "Group: ")
          [:span {:style {:fontWeight 500}} (if group-name group-name "Group Management")]]]
        (if group-name
          [MembershipManagementPage
           {:group-name group-name
            :add-endpoint #(endpoints/add-group-user {:group-name %1
                                                      :role %2
                                                      :email %3})
            :delete-endpoint #(endpoints/delete-group-user {:group-name %1
                                                            :role %2
                                                            :email %3})
            :header (fn [data]
                      (let [owners-group (:ownersGroup data)
                            users-group (:usersGroup data)]
                        [:div {:style {:paddingBottom "0.5rem"}}
                         [:span {:style {:fontSize "110%"}} "Email the Group:"]
                         [:div {} "Owners: "
                          (style/create-link {:href (str "mailto:" (:groupEmail owners-group))
                                              :text (:groupName owners-group)})]
                         [:div {} "All Users: "
                          (style/create-link {:href (str "mailto:" (:groupEmail users-group))
                                              :text (:groupName users-group)})]]))
            :table-data (fn [data]
                          (concat (mapv (fn [email] {:email email :role "Owner"}) (:ownersEmails data))
                                  (mapv (fn [email] {:email email :role "User"}) (:usersEmails data))))
            :list-endpoint endpoints/list-group-members}]
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
