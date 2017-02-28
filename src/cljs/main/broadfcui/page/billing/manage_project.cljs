(ns broadfcui.page.billing.manage-project
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.input :as input]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.common.table :as table]
    [broadfcui.common.table-utils :refer [add-right]]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.utils :as utils]
    ))

(react/defc AddUserDialog
  {:render
   (fn [{:keys [props state this refs]}]
     [comps/OKCancelForm
      {:header (str "Add user to " (:project-name props))
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
          [:div {:style {:marginBottom "1em"}}
           "Warning: Adding any user to this project will mean they can incur costs to the billing associated with this project."]
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
             {:endpoint (endpoints/add-billing-project-user {:project-id (:project-name props)
                                                             :role role
                                                             :user-email email})
              :on-done (fn [{:keys [success? get-parsed-response]}]
                         (swap! state dissoc :adding?)
                         (if success?
                           (do (modal/pop-modal)
                               ((:on-add props)))
                           (swap! state assoc :server-error (get-parsed-response false))))})))))})


(defn- remove-user [state this data]
  (swap! state assoc :removing? true)
  (endpoints/call-ajax-orch
    {:endpoint (endpoints/delete-billing-project-user data)
     :on-done (fn [{:keys [success? get-parsed-response]}]
                (swap! state dissoc :removing?)
                (if success?
                  (react/call :load this)
                  (swap! state assoc :remove-error (get-parsed-response false))))}))


(react/defc BillingProjectManagementPage
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [load-error members]} @state]
       (cond load-error [comps/ErrorViewer {:error load-error}]
             (not members) [comps/Spinner {:text "Loading project membership..."}]
             :else
             [:div {:style {:position "relative"}}
              (when (:removing? @state)
                [comps/Blocker {:banner "Removing user..."}])
              [table/Table
               {:header-row-style style/header-row-style-light
                :header-style {:padding "0.5em 0 0.5em 14px"}
                :row-style {:backgroundColor (:background-light style/colors)
                            :borderRadius 8 :margin "4px 0"}
                :reorderable-columns? false
                :resize-tab-color (:line-default style/colors)
                :toolbar (add-right
                          [comps/Button {:text "Add User..." :icon :add
                                         :onClick (fn [_]
                                                    (modal/push-modal
                                                     [AddUserDialog {:project-name (:project-name props)
                                                                     :on-add #(react/call :load this)}]))}])
                :columns [{:header "Email" :starting-width 500}
                          {:header "Role" :starting-width 100 :resizable? false :sort-initial :asc}
                          {:starting-width :remaining
                           :filter-by :none :sort-by :none :resizable? false
                           :as-text
                           (fn [{:strs [email role]}]
                             (str "Remove " (clojure.string/lower-case role) " " email))
                           :content-renderer
                           (fn [{:strs [email role]}]
                             (style/create-link {:text "Remove"
                                                 :onClick #(remove-user state this {:project-id (:project-name props)
                                                                                    :role role
                                                                                    :user-email email})}))}]
                :data members
                :->row (fn [{:strs [email role] :as row}]
                         [email
                          role
                          row])}]
              [comps/ErrorViewer {:error (:remove-error @state)}]])))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load this))
   :load
   (fn [{:keys [props state]}]
     (swap! state dissoc :members :load-error)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/list-billing-project-members (:project-name props))
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (swap! state assoc (if success? :members :load-error) (get-parsed-response false)))}))})
