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
   (fn [{:keys [props state this]}]
     [dialog/OKCancelForm
      {:header "Create Billing Project"
       :dismiss-self #(modal/pop-modal)
       :content
       (react/create-element
         (if (empty? (:billing-accounts props))
           [:div {} "No billing accounts :( [todo: help text]"]
           [:div {}
            [:div {:style {:fontSize "120%" :marginBottom "0.5ex"}}
             "Select a billing account:"]
            [:div {:style {:width 500 :backgroundColor "white" :padding "1em"}}
             [table/Table
              {:width :narrow
               :columns [{:header "Account Name" :starting-width 300
                          :content-renderer
                          (fn [[name has-access]]
                            (if has-access
                              (style/create-link {:text name :onClick #(swap! state assoc :selected-account name)})
                              name))}
                         {:header "Firecloud Access?" :starting-width 150}]
               :data (:billing-accounts props)
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
            (style/create-validation-error-message (:validation-errors @state))]))
       :ok-button [comps/Button {:text "OK" :onClick #(react/call :create-billing-project this)}]}])
   :create-billing-project
   (fn [{:keys [state refs]}]
     (let [account (:selected-account @state)]
       (if-not account
         (swap! state assoc :validation-errors ["Please select a billing account"])
         (do
           (swap! state dissoc :validation-errors)
           (let [[name & fails] (input/get-and-validate refs "name-field")]
             (if fails
               (swap! state assoc :validation-errors fails)
               (utils/log "Success:" name)))))))})


(react/defc Table
  {:reload
   (fn [{:keys [this]}]
     (react/call :load-data this))
   :render
   (fn [{:keys [state]}]
     (cond
       (:error-message @state) (style/create-server-error-message (:error-message @state))
       (nil? (:projects @state)) [comps/Spinner {:text "Loading billing projects..."}]
       :else
       [table/Table
        {:columns [{:header "Project Name" :starting-width 300}
                   {:header "Role" :starting-width 300}]
         :toolbar
         (float-right
           (when false ; hidden until implemented
             [comps/Button {:text "Create New Billing Project"
                            :disabled? (cond (:billing-acct-error @state) (:billing-acct-error @state)
                                             (nil? (:billing-accounts @state)) "Loading billing accounts")
                            :onClick #(modal/push-modal [CreateBillingProjectDialog {:billing-accounts (:billing-accounts @state)}])}]))
         :data (:projects @state)
         :->row (fn [item]
                  [(item "projectName")
                  (item "role")])}]))
   :component-did-mount
   (fn [{:keys [this state]}]
     (react/call :load-data this)
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-billing-accounts)
        :on-done
        (fn [{:keys [success? get-parsed-response]}]
          (swap! state assoc :billing-accounts [{"accountName" "billingAccounts/00473A-04A1D8-155CAB"
                                                 "firecloudHasAccess" false}
                                                {"accountName" "billingAccounts/foo-bar-baz"
                                                 "firecloudHasAccess" true}])
          #_(swap! state assoc (if success? :billing-accounts :billing-acct-error) (get-parsed-response)))}))
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
      [Table]])})
