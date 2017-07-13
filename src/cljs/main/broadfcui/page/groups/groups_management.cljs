(ns broadfcui.page.groups.groups-management
  (:require
    [dmohs.react :as react]
    [broadfcui.common.components :as comps]
    [broadfcui.common.flex-utils :as flex]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.management-utils :as management-utils]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.common.table.style :as table-style]
    [broadfcui.common.table.table :refer [Table]]
    [broadfcui.components.modals :as modals]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.nav :as nav]
    [broadfcui.net :as net]
    [broadfcui.page.groups.create-group :refer [CreateGroupDialog]]
    [broadfcui.utils :as utils]
    ))

(react/defc GroupTable
  {:render
   (fn [{:keys [this state]}]
     [:div {}
      (cond
        (:delete-modal? @state)
        (if (:deleting? @state)
          [comps/Blocker {:banner "Deleting Group"}]
          (modals/render-message
           {:text "Are you sure you want to delete this group?"
            :on-confirm (fn []
                          (swap! state assoc :deleting? true)
                          (net/render-with-ajax
                           (:delete-response @state)
                           (this :-delete-group)
                           {:rephrase-error
                            #(if (= 409 (:status-code %))
                               "Sorry you are unable to delete this group because it is in use"
                               (get-in % [:parsed-response :message]))}))}))
        (:error? @state)
        (modals/render-error
         {:text (:error-message @state)
          :on-dismiss #(swap! state dissoc :error? :delete-modal?)}))
      (net/render-with-ajax
       (:groups-response @state)
       #(this :-render-groups-table)
       {:loading-text "Loading Groups..."})])
   :component-did-mount
   (fn [{:keys [this]}]
     (this :-load-data))
   :-load-data
   (fn [{:keys [state]}]
     (utils/ajax-orch
      "/groups"
      {:on-done (net/handle-ajax-response #(swap! state assoc :groups-response %))}))
   :-render-groups-table
   (fn [{:keys [this state]}]
     [Table
      {:data (get-in @state [:groups-response :parsed-response])
       :body {:behavior {:reorderable-columns? false}
              :style table-style/table-light
              :columns
              [{:header "Group Name" :initial-width 300 :sort-initial :asc
                :sort-by :text
                :as-text :groupName
                :render
                (fn [{:keys [groupName role]}]
                  (if
                   (= role "Admin")
                    (style/create-link {:text groupName
                                        :href (nav/get-link :group groupName)})
                    groupName))}
               {:header "Role" :initial-width 100
                :as-text :role}
               {:header "Email for Sharing Workspaces" :initial-width :auto
                :resizable? false
                :as-text :groupEmail
                :render
                (fn [{:keys [groupEmail]}]
                  [:span {:style {:fontWeight "normal"}} groupEmail])}
               {:id "delete group" :initial-width 30
                :filterable? false :sortable? false :resizable? false
                :as-text
                (fn [{:keys [groupName role]}]
                  (when (= role "Admin")
                    (str "Delete group " groupName)))
                :render
                (fn [{:keys [groupName role]}]
                  (when (= role "Admin")
                    (style/create-link
                     {:text (icons/icon {} :delete)
                      :style {:float "right" :color (:exception-state style/colors)}
                      :onClick #(do
                                  (swap! state assoc :group-name groupName)
                                  (swap! state assoc :delete-modal? true))})))}]}
       :toolbar
       {:items
        [flex/spring
         [comps/Button
          {:text "Create New Group..."
           :onClick
           (fn []
             (modal/push-modal
              [CreateGroupDialog
               {:on-success #(this :-load-data)}]))}]]}}])
   :-delete-group
   (fn [{:keys [state this]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/delete-group (:group-name @state))
       :on-done (net/handle-ajax-response
                 (fn [{:keys [success? status-code parsed-response]}]
                   (swap! state dissoc :deleting? :delete-modal?)
                   (if success?
                     (this :-load-data)
                     (do
                       (swap! state assoc :error-message (:message parsed-response))
                       (swap! state assoc :error? true)))))}))})

(react/defc Page
  {:render
   (fn [{:keys [props]}]
     (let [{:keys [group-name]} props]
       [:div {:style style/thin-page-style}
        [:div {:style {:marginBottom "1rem" :fontSize "1.1rem"}}
         [:div {:style {:fontSize "1.2em"}} (when group-name "Group: ")
          [:span {:style {:fontWeight 500}} (if group-name group-name "Group Management")]]]
        (if group-name
          [management-utils/MembershipManagementPage
           {:group-name group-name
            :admin-term "Admin"
            :user-term "Member"
            :add-endpoint #(endpoints/add-group-user {:group-name group-name
                                                      :role %1
                                                      :email %2})
            :delete-endpoint #(endpoints/delete-group-user {:group-name group-name
                                                            :role %1
                                                            :email %2})
            :header (fn [data]
                      [:small {:style {:display "block" :margin "-0.75rem 0 1rem"}}
                       "Use when sharing workspaces: " (get-in data [:membersGroup :groupEmail])])
            :table-data (fn [data]
                          (concat (mapv (fn [email] {:email email :role "Admin"}) (:adminsEmails data))
                                  (mapv (fn [email] {:email email :role "Member"}) (:membersEmails data))))
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
