(ns org.broadinstitute.firecloud-ui.page.billing-management
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.input :as input]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.sign-in :as sign-in]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.table-utils :refer [float-right]]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

(react/defc CreateBillingProjectDialog
  {:render
   (fn [{:keys [state this]}]
     [modal/OKCancelForm
      {:header "Create Billing Project"
       :content
       (react/create-element
         (let [{:keys [billing-accounts billing-acct-error]} @state]
           (cond billing-acct-error
                 ; if the user has not enabled the correct scopes to list billing accounts, the call will return an error
                 ; with a redirect URL in string-encoded JSON format.  Rather than redirecting the browser directly
                 ; (not permitted in AJAX) we pop up a separate window with a callback to this component's :get-billing-accounts
                 (try
                   (if-let [redirect-url ((utils/parse-json-string (billing-acct-error "message")) "redirect")]
                     [comps/Button {:text "Click Here To Enable Billing Permissions"
                                    :onClick #(.. js/window
                                                  (open redirect-url
                                                        "Authentication"
                                                        "menubar=no,toolbar=no,width=500,height=500"))}]
                     [comps/ErrorViewer {:error billing-acct-error}])
                   (catch js/Object _ [comps/ErrorViewer {:error billing-acct-error}]))
                 (not billing-accounts) [comps/Spinner {:text "Loading billing accounts..."}]
                 :else
                 (if (empty? billing-accounts)
                   [:div {} "You do not have any billing accounts available."]
                   [:div {:style {:width 750}}
                    (when (:creating? @state)
                      [comps/Blocker {:banner "Creating billing account..."}])
                    [:div {:style {:fontSize "120%" :marginBottom "0.5ex"}}
                     "Select a billing account:"]
                    [:div {:style {:backgroundColor "white" :padding "1em"}}
                     [table/Table
                      {:columns [{:header "Account Name" :starting-width 300
                                  :content-renderer
                                  (fn [[name has-access]]
                                    (if has-access
                                      (style/create-link {:text name :onClick #(swap! state assoc :selected-account name)})
                                      name))}
                                 {:header "Firecloud Access?" :starting-width 150}]
                       :data billing-accounts
                       :row-style (fn [row-index [[acct-name _] _]]
                                    {:backgroundColor
                                     (cond (= acct-name (:selected-account @state)) "yellow"
                                           (even? row-index) (:background-gray style/colors)
                                           :else "#fff")})
                       :->row (fn [{:strs [accountName firecloudHasAccess]}]
                                [[accountName firecloudHasAccess]
                                 (if firecloudHasAccess "Yes" "No")])}]]
                    [:div {:style {:fontSize "120%" :margin "1em 0 0.5ex 0"}}
                     "Name:"]
                    [input/TextField {:ref "name-field"
                                      :style {:width "100%"}
                                      :predicates [(input/nonempty "Name")]}]
                    (style/create-validation-error-message (:validation-errors @state))
                    [comps/ErrorViewer {:error (:server-error @state)}]]))))
       :ok-button (when-not (empty? (:billing-accounts @state))
                    #(react/call :create-billing-project this))}])
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :get-billing-accounts this)
     ; register a function in the JS window
     ; this window's child can access the function by calling its window.opener
     (aset js/window sign-in/handler-fn-name
       (fn [message]
         (let [token (get message "access_token")]
           (reset! utils/access-token token)
           (utils/set-access-token-cookie token)
           (react/call :get-billing-accounts this)))))
   :component-will-unmount
   (fn []
     (js-delete js/window sign-in/handler-fn-name))
   :get-billing-accounts
   (fn [{:keys [state]}]
     (swap! state dissoc :billing-accounts :billing-acct-error)
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-billing-accounts)
        :on-done
        (fn [{:keys [success? get-parsed-response]}]
          (if success?
            (let [accts (get-parsed-response)]
              (swap! state assoc :billing-accounts accts :selected-account (get (first accts) "accountName")))
            (swap! state assoc :billing-acct-error (get-parsed-response))))}))
   :create-billing-project
   (fn [{:keys [props state refs]}]
     (let [account (:selected-account @state)]
       (if-not account
         (swap! state assoc :validation-errors ["Please select a billing account"])
         (let [[name & fails] (input/get-and-validate refs "name-field")]
           (swap! state assoc :validation-errors fails)
           (when-not fails
             (swap! state assoc :creating? true)
             (endpoints/call-ajax-orch
               {:endpoint endpoints/create-billing-project
                :payload {:projectName name :billingAccount account}
                :headers {"Content-Type" "application/json"}
                :on-done (fn [{:keys [success? get-parsed-response]}]
                           (swap! state dissoc :creating?)
                           (if success?
                             (do ((:on-success props))
                                 (modal/pop-modal))
                             (swap! state assoc :server-error (get-parsed-response))))}))))))})


(react/defc BillingProjectManagementPage
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [load-error members]} @state]
       (cond load-error [comps/ErrorViewer {:error load-error
                                            :expect {403 "You are not an owner of this billing project."}}]
             (not members) [comps/Spinner {:text "Loading project membership..."}]
             :else
             [:div {:style {:backgroundColor "white" :padding "1em"}}
              [table/Table
               {:toolbar (float-right [comps/Button {:text "Add User..." :style :add}])
                :columns [{:header "Email" :starting-width 300}
                          {:header "Role" :starting-width 100}
                          {:header "Actions" :starting-width 100
                           :filter-by :none :sort-by :none
                           :content-renderer
                           (fn [{:strs [email row]}]
                             (style/create-link {:text "Remove"
                                                 :onClick #(utils/log "remove")}))}]
                :data members
                :->row (fn [{:strs [email role] :as row}]
                         [email
                          role
                          row])}]])))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/list-billing-project-members (:project-name props))
        :on-done (fn [{:keys [success? get-parsed-response]}]
                   (swap! state assoc (if success? :members :load-error) (get-parsed-response)))}))})


(react/defc BillingProjectTable
  {:reload
   (fn [{:keys [this]}]
     (react/call :load-data this))
   :render
   (fn [{:keys [props state this]}]
     (cond
       (:error-message @state) (style/create-server-error-message (:error-message @state))
       (nil? (:projects @state)) [comps/Spinner {:text "Loading billing projects..."}]
       :else
       [table/Table
        {:columns [{:header "Project Name" :starting-width 300}
                   {:header "Role" :starting-width 300
                    :content-renderer
                    (fn [{:strs [projectName role]}]
                      [:span {}
                       role
                       (when (= role "Owner")
                         (style/create-link {:text "Click to manage"
                                             :style {:marginLeft "1em"}
                                             :onClick #((:on-select props) projectName)}))])}]
         :toolbar
         (float-right
           (when false ; hidden until implemented
             [comps/Button {:text "Create New Billing Project"
                            :onClick (fn []
                                       (modal/push-modal
                                         [CreateBillingProjectDialog {:on-success #(react/call :reload this)}]))}]))
         :data (:projects @state)
         :->row (fn [{:strs [projectName] :as row}]
                  [projectName
                   row])}]))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-data this))
   :load-data
   (fn [{:keys [state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-billing-projects)
        :on-done
        (fn [{:keys [success? get-parsed-response status-text]}]
          (if success?
            (swap! state assoc :projects (get-parsed-response))
            (swap! state assoc :error-message status-text)))}))})


(react/defc Page
  {:render
   (fn [{:keys [state]}]
     (let [{:keys [selected-project]} @state]
       [:div {:style {:padding "1em"}}
        [:div {:style {:fontSize "180%" :marginBottom "1em"}}
         [comps/Breadcrumbs {:crumbs [{:text "Billing Management"
                                       :onClick #(swap! state dissoc :selected-project)}
                                      (when selected-project
                                        {:text selected-project})]}]]
        (if selected-project
          [BillingProjectManagementPage {:project-name selected-project}]
          [BillingProjectTable {:on-select #(swap! state assoc :selected-project %)}])]))})
