(ns broadfcui.common.management-utils
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.flex-utils :as flex]
    [broadfcui.common.input :as input]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.common.table.table :as table]
    [broadfcui.common.table.style :as table-style]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.utils :as utils]
    ))

(react/defc AddUserDialog
  {:render
   (fn [{:keys [props state this refs]}]
     [comps/OKCancelForm
      {:header (str "Add user to " (:group-name props))
       :ok-button #(this :-add-user)
       :get-first-element-dom-node #(react/find-dom-node (@refs "email"))
       :content
       (react/create-element
        [:div {:style {:width 420}}
         (when (:adding? @state)
           [comps/Blocker {:banner "Adding user..."}])
         [:div {:style {:display "flex"}}
          [:div {:style {:flex "1 1 auto"}}
           (style/create-form-label "User email")
           [input/TextField {:ref "email" :autoFocus true
                             :style {:width "100%"}
                             :predicates [(input/valid-email "Email")]
                             :onKeyDown (common/create-key-handler [:enter] #(this :-add-user))}]]
          [:div {:style {:flex "0 0 10px"}}]
          [:div {:style {:flex "0 0 100px"}}
           (style/create-form-label "Role")
           (style/create-identity-select {:ref "role"} ["User" "Owner"])]]
         (:footer props)
         (style/create-validation-error-message (:fails @state))
         [comps/ErrorViewer {:error (:server-error @state)
                             :expect {404 "This is not a registered user"}}]])}])
   :-add-user
   (fn [{:keys [props state refs]}]
     (let [[email & fails] (input/get-and-validate refs "email")]
       (swap! state assoc :fails fails :server-error nil)
       (when-not fails
         (let [role (common/get-text refs "role")
               {:keys [endpoint on-add]} props]
           (swap! state assoc :adding? true)
           (endpoints/call-ajax-orch
            {:endpoint (endpoint (:group-name props) role email)
             :on-done (fn [{:keys [success? get-parsed-response]}]
                        (swap! state dissoc :adding?)
                        (if success?
                          (do (modal/pop-modal)
                              (on-add))
                          (swap! state assoc :server-error (get-parsed-response false))))})))))})


(react/defc MembershipManagementPage
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [load-error data]} @state
           {:keys [header delete-endpoint table-data]} props]
       (cond load-error [comps/ErrorViewer {:error load-error}]
             (not data) [comps/Spinner {:text "Loading..."}]
             :else
             [:div {:style {:position "relative"}}
              (when header
                (header (:data @state)))
              (when (:removing? @state)
                [comps/Blocker {:banner "Removing user..."}])
              [table/Table
               {:data (table-data data)
                :body {:behavior {:reorderable-columns? false}
                       :style (merge
                               table-style/table-light
                               {:body-cell {:padding 0}
                                :body-row (constantly {:margin "4px 0"})})
                       :columns
                       [{:header "Email" :initial-width 500 :column-data :email
                         :render
                         (fn [email]
                           [:div {:style table-style/table-cell-plank-left} email])}
                        {:header "Role" :initial-width 100 :resizable? false
                         :column-data :role :sort-initial :asc
                         :render
                         (fn [role]
                           [:div {:style table-style/table-cell-plank-right} role])}
                        {:id "remove user" :initial-width :remaining
                         :filterable? false :sortable? false :resizable? false
                         :as-text
                         (fn [{:keys [email role]}]
                           (str "Remove " (clojure.string/lower-case role) " " email))
                         :render
                         (fn [{:keys [email role]}]
                           [:div {:style {:padding "0.6rem 0 0.6rem 32px"}}
                            (style/create-link {:text "Remove"
                                                :onClick #(:-remove-user role email)})])}]}
                :toolbar {:items [flex/spring
                                  [comps/Button
                                   {:text "Add User..." :icon :add-new
                                    :onClick (fn [_]
                                               (modal/push-modal
                                                [AddUserDialog {:endpoint (:add-endpoint props)
                                                                :group-name (:group-name props)
                                                                :on-add #(this :-load-data)
                                                                :footer (:add-member-footer props)}]))}]]}}]
              [comps/ErrorViewer {:error (:remove-error @state)}]])))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :-load-data))
   :-load-data
   (fn [{:keys [props state]}]
     (let [endpoint (:list-endpoint props)]
       (swap! state dissoc :data :load-error)
       (endpoints/call-ajax-orch
        {:endpoint (endpoint (:group-name props))
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state assoc (if success? :data :load-error) (get-parsed-response)))})))
   :-remove-user
   (fn [{:keys [props state this]} role email]
     (let [{:keys [delete-endpoint group-name]} props]
       (swap! state assoc :removing? true)
       (endpoints/call-ajax-orch
        {:endpoint (delete-endpoint group-name role email)
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :removing?)
                    (if success?
                      (this :-load-data)
                      (swap! state assoc :remove-error (get-parsed-response false))))})))})
