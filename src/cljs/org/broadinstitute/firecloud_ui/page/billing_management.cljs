(ns org.broadinstitute.firecloud-ui.page.billing-management
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


(react/defc CreateBillingProjectDialog
  {:render
   (fn [{:keys [state this]}]
     [modal/OKCancelForm
      {:header "Create Billing Project"
       :content
       (react/create-element
         (let [{:keys [billing-accounts billing-acct-error]} @state]
           (cond billing-acct-error [comps/ErrorViewer {:error billing-acct-error}]
                 (not billing-accounts) [comps/Spinner {:text "Loading billing accounts..."}]
                 :else
                 (if (empty? billing-accounts)
                   [:div {} "No billing accounts :( [todo: help text]"]
                   [:div {}
                    [:div {:style {:fontSize "120%" :marginBottom "0.5ex"}}
                     "Select a billing account:"]
                    [:div {:style {:width 750 :backgroundColor "white" :padding "1em"}}
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
       :ok-button #(react/call :create-billing-project this)}])
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-billing-accounts)
        :on-done
        (fn [{:keys [success? get-parsed-response]}]
          (swap! state assoc :billing-accounts [{"accountName" "billingAccounts/00473A-04A1D8-155CAB"
                                                 "firecloudHasAccess" false}
                                                {"accountName" "billingAccounts/foo-bar-baz"
                                                 "firecloudHasAccess" true}])
          #_(swap! state assoc (if success? :billing-accounts :billing-acct-error) (get-parsed-response)))}))
   :create-billing-project
   (fn [{:keys [props state refs]}]
     (let [account (:selected-account @state)]
       (if-not account
         (swap! state assoc :validation-errors ["Please select a billing account"])
         (let [[name & fails] (input/get-and-validate refs "name-field")]
           (swap! state assoc :validation-errors fails)
           (when-not fails
             (endpoints/call-ajax-orch
               {:endpoint endpoints/create-billing-project
                :payload {:projectName name :billingAccount account}
                :headers {"Content-Type" "application/json"}
                :on-done (fn [{:keys [success? get-parsed-response]}]
                           (if success?
                             (do ((:on-success props))
                                 (modal/pop-modal))
                             (swap! state assoc :server-error (get-parsed-response))))}))))))})


(react/defc BillingProjectTable
  {:reload
   (fn [{:keys [this]}]
     (react/call :load-data this))
   :render
   (fn [{:keys [state this]}]
     (cond
       (:error-message @state) (style/create-server-error-message (:error-message @state))
       (nil? (:projects @state)) [comps/Spinner {:text "Loading billing projects..."}]
       :else
       [table/Table
        {:columns [{:header "Project Name" :starting-width 300}
                   {:header "Role" :starting-width 300
                    :content-renderer
                    (fn [role]
                      [:span {}
                       role
                       (when (= role "Owner")
                         (style/create-link {:text "Click to manage"
                                             :style {:marginLeft "1em"}
                                             :onClick #(utils/log "manage")}))])}]
         :toolbar
         (float-right
           (when false ; hidden until implemented
             [comps/Button {:text "Create New Billing Project"
                            :onClick (fn []
                                       (modal/push-modal
                                         [CreateBillingProjectDialog {:on-success #(react/call :reload this)}]))}]))
         :data (:projects @state)
         :->row (fn [item]
                  [(item "projectName")
                   (item "role")])}]))
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
   (fn [{:keys []}]
     [:div {:style {:padding "1em"}}
      [:h2 {} "Billing Management"]
      [BillingProjectTable]])})
