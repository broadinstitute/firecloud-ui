(ns org.broadinstitute.firecloud-ui.page.billing.manage-project
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.input :as input]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.table-utils :refer [float-right]]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

(react/defc AddUserDialog
  {:render
   (fn [{:keys [props state this refs]}]
     [modal/OKCancelForm
      {:header (str "Add user to " (:project-name props))
       :ok-button #(react/call :add-user this)
       :get-first-element-dom-node #(react/find-dom-node (@refs "email"))
       :content
       (react/create-element
         [:div {:style {:width "420px"}}
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
                           (swap! state assoc :server-error (get-parsed-response))))})))))})


(defn- remove-user [state this data]
  (swap! state assoc :removing? true)
  (endpoints/call-ajax-orch
    {:endpoint (endpoints/delete-billing-project-user data)
     :on-done (fn [{:keys [success? get-parsed-response]}]
                (swap! state dissoc :removing?)
                (if success?
                  (react/call :load this)
                  (swap! state assoc :remove-error (get-parsed-response))))}))


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
               {:toolbar (float-right
                           [comps/Button {:text "Add User..." :style :add
                                          :onClick (fn [_]
                                                     (modal/push-modal
                                                       [AddUserDialog {:project-name (:project-name props)
                                                                       :on-add #(react/call :load this)}]))}])
                :columns [{:header "Email" :starting-width 300}
                          {:header "Role" :starting-width 100}
                          {:header "Actions" :starting-width 100
                           :filter-by :none :sort-by :none
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
                   (swap! state assoc (if success? :members :load-error) (get-parsed-response)))}))})
