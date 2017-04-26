(ns broadfcui.page.groups.manage-group
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.input :as input]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.common.table :as table]
    [broadfcui.common.table-utils :refer [add-right]]
    [broadfcui.common.table-style :as table-style]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.utils :as utils]
    ))

(react/defc AddUserDialog
  {:render
   (fn [{:keys [props state this refs]}]
     [comps/OKCancelForm
      {:header (str "Add user to " (:group-name props))
       :ok-button #(react/call :add-user this)
       :get-first-element-dom-node #(react/find-dom-node (@refs "email"))
       :content
       (react/create-element
        [:div {:style {:width 420}}
         (when (:adding? @state)
           [comps/Blocker {:banner "Adding user..."}])
         [:div {:style {:display "flex"}}
          [:div {:style {:flex "1 1 auto"}}
           (style/create-form-label "User email")
           [input/TextField {:ref "email" :style {:width "100%"}
                             :predicates [(input/valid-email "Email")]
                             :onKeyDown (common/create-key-handler [:enter] #(react/call :add-user this))}]]
          [:div {:style {:flex "0 0 10px"}}]
          [:div {:style {:flex "0 0 100px"}}
           (style/create-form-label "Role")
           (style/create-identity-select {:ref "role"} ["User" "Owner"])]]
         (style/create-validation-error-message (:fails @state))
         [comps/ErrorViewer {:error (:server-error @state)
                             :expect {404 "This is not a registered user"}}]])}])
   :add-user
   (fn [{:keys [props state refs]}]
     (let [[email & fails] (input/get-and-validate refs "email")]
       (swap! state assoc :fails fails :server-error nil)
       (when-not fails
         (let [role (common/get-text refs "role")]
           (swap! state assoc :adding? true)
           (endpoints/call-ajax-orch
            {:endpoint (endpoints/add-group-user {:group-name (:group-name props)
                                                  :role role
                                                  :email email})
             :on-done (fn [{:keys [success? get-parsed-response]}]
                        (swap! state dissoc :adding?)
                        (if success?
                          (do (modal/pop-modal)
                              ((:on-add props)))
                          (swap! state assoc :server-error (get-parsed-response))))})))))})


(defn- remove-user [state this data]
  (swap! state assoc :removing? true)
  (endpoints/call-ajax-orch
   {:endpoint (endpoints/delete-group-user data)
    :on-done (fn [{:keys [success? get-parsed-response]}]
               (swap! state dissoc :removing?)
               (if success?
                 (react/call :load this)
                 (swap! state assoc :remove-error (get-parsed-response))))}))


(react/defc GroupManagementPage
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [load-error group-info]} @state]
       (cond load-error [comps/ErrorViewer {:error load-error}]
             (not group-info) [comps/Spinner {:text "Loading group membership..."}]
             :else
             [:div {:style {:position "relative"}}
              (let [owners-group (:ownersGroup group-info)
                    users-group (:usersGroup group-info)]
                [:div {:style {:paddingBottom "0.5rem"}}
                 [:span {:style {:fontSize "110%"}} "Email the Group:"]
                 [:div {} "Owners: "
                  (style/create-link {:href (str "mailto:" (:groupEmail owners-group))
                                      :text (:groupName owners-group)})]
                 [:div {} "All Users: "
                  (style/create-link {:href (str "mailto:" (:groupEmail users-group))
                                      :text (:groupName users-group)})]])
              (when (:removing? @state)
                [comps/Blocker {:banner "Removing user..."}])
              [table/Table
               {:header-row-style table-style/header-row-style-light
                :header-style {:padding "0.5em 0 0.5em 14px"}
                :cell-content-style {:padding 0 :paddingRight 20 :marginRight -20}
                :row-style {:backgroundColor "white"}
                :reorderable-columns? false
                :resize-tab-color (:line-default style/colors)
                :toolbar (add-right
                          [comps/Button {:text "Add User..." :icon :add-new
                                         :onClick (fn [_]
                                                    (modal/push-modal
                                                     [AddUserDialog {:group-name (:group-name props)
                                                                     :on-add #(this :load)}]))}])
                :columns [{:header "Email" :starting-width 500
                           :content-renderer
                           (fn [email]
                             [:div {:style table-style/table-cell-plank-left}
                              email])}
                          {:header "Role" :starting-width 100 :resizable? false :sort-initial :asc
                           :content-renderer
                           (fn [role]
                             [:div {:style table-style/table-cell-plank-right}
                              role])}
                          {:starting-width :remaining
                           :filter-by :none :sort-by :none :resizable? false
                           :as-text
                           (fn [{:keys [email role]}]
                             (str "Remove " (clojure.string/lower-case role) " " email))
                           :content-renderer
                           (fn [{:keys [email role]}]
                             (style/create-link {:text "Remove"
                                                 :onClick #(remove-user state this {:group-name (:group-name props)
                                                                                    :role role
                                                                                    :email email})}))}]
                :data (concat (mapv #(identity {:email % :role "Owner"}) (:ownersEmails group-info))
                              (mapv #(identity {:email % :role "User"}) (:usersEmails group-info)))
                :->row (fn [{:keys [email role] :as row}]
                         [email
                          role
                          row])}]
              [comps/ErrorViewer {:error (:remove-error @state)}]])))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load this))
   :load
   (fn [{:keys [props state]}]
     (swap! state dissoc :group-info :load-error)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/list-group-members (:group-name props))
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (swap! state assoc (if success? :group-info :load-error) (get-parsed-response)))}))})
