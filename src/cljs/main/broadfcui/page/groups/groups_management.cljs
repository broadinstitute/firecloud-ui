(ns broadfcui.page.groups.groups-management
  (:require
    [dmohs.react :as react]
    [broadfcui.common.components :as comps]
    [broadfcui.common.flex-utils :as flex]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.management-utils :refer [MembershipManagementPage]]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.common.table.table :refer [Table]]
    [broadfcui.common.table.style :as table-style]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.nav :as nav]
    [broadfcui.page.groups.create-group :refer [CreateGroupDialog]]
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
                  (fn [{:keys [groupName accessLevels]}]
                    (if
                     (contains? (set accessLevels) "Owner")
                      (style/create-link {:text groupName
                                          :href (nav/get-link :group groupName)})
                      groupName))}
                 {:header "Role" :initial-width 100 :resizable? false
                  :column-data #(clojure.string/join ", " (:accessLevels %))}
                 {:id "delete group" :initial-width :auto
                  :filterable? false :sortable? false
                  :as-text
                  (fn [{:keys [groupName accessLevels]}]
                    (when (contains? (set accessLevels) "Owner")
                      (str "Delete group " groupName)))
                  :render
                  (fn [{:keys [groupName accessLevels]}]
                    (when (contains? (set accessLevels) "Owner")
                      (style/create-link
                       {:text (icons/icon {} :delete)
                        :style {:float "right"}
                        :onClick (fn []
                                   (swap! state assoc :deleting? true)
                                   (endpoints/call-ajax-orch
                                    {:endpoint (endpoints/delete-group groupName)
                                     :on-done (fn [{:keys [success? get-parsed-response]}]
                                                (swap! state dissoc :deleting?)
                                                (if success?
                                                  (this :load-data)
                                                  (swap! state assoc :error-mesage (get-parsed-response false))))}))})))}]}
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
          (swap! state assoc :groups groups)))))})


(react/defc Page
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [group-name]} props]
       [:div {:style style/thin-page-style}
        [:div {:style {:marginBottom "1rem" :fontSize "1.1rem"}}
         [:div {:style {:fontSize "1.2em"}} (when group-name "Group: ")
          [:span {:style {:fontWeight 500}} (if group-name group-name "Group Management")]]]
        (when (:deleting? @state)
          [comps/Blocker {:banner "Deleting group..."}])
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
                      [:small {:style {:display "block" :margin "-0.75rem 0 1rem"}}
                       "Use when sharing workspaces: " (get-in data [:usersGroup :groupEmail])])
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
