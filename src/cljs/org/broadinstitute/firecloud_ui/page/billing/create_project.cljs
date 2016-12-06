(ns org.broadinstitute.firecloud-ui.page.billing.create-project
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.input :as input]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
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
        (let [{:keys [billing-accounts error]} @state]
          (cond
            (not (or billing-accounts error)) [comps/Spinner {:text "Loading billing accounts..."}]
            error
            (case (:code error)
              (:unknown :parse-error)
              [:div {:style {:color (:exception-state style/colors)}}
               "Error:" [:br] (:details error)]
              [comps/ErrorViewer {:error (:details error)}])
            :else
            (if (empty? billing-accounts)
              [:div {} "You do not have any billing accounts available."]
              [:div {:style {:width 750}}
               (when (:creating? @state)
                 [comps/Blocker {:banner "Creating billing account..."}])
               [:div {:style {:fontSize "120%" :margin "1em 0 1em 0"}}
                "1. Choose a unique name:"]
               [input/TextField {:ref "name-field"
                                 :style {:width "100%" :marginBottom 0}
                                 :predicates [{:test #(re-matches #"[a-z0-9\-]*" %) :message "Name contains invalid characters"}
                                              {:test #(<= 6 (count %) 30) :message "Name must be 6-30 characters long"}
                                              {:test #(re-matches #"[a-z]" (first %)) :message "Name must start with a letter"}]}]
               (style/create-validation-error-message (:validation-errors @state))
               [:div {:style {:marginBottom "1em"
                              :color (if (:validation-errors @state) (:exception-state style/colors) (:text-lighter style/colors))
                              :fontSize "0.8em"}}
                "Lowercase letters, numbers, and hypens only, 6-30 characters, must start with a letter."]
               [:div {:style {:fontSize "120%"}}
                "2. Select a billing account:"]
               [:div {:style {:backgroundColor "white" :padding "1em" :margin "1em 0 1em 0"}}
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
                                      (even? row-index) (:background-light style/colors)
                                      :else "#fff")})
                  :->row (fn [{:strs [accountName firecloudHasAccess]}]
                           [[accountName firecloudHasAccess]
                            (if firecloudHasAccess "Yes" "No")])}]]
               [comps/ErrorViewer {:error (:server-error @state)}]]))))
       :ok-button (when-not (empty? (:billing-accounts @state))
                    #(react/call :create-billing-project this))}])
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :get-billing-accounts this))
   :get-billing-accounts
   (fn [{:keys [state]}]
     (swap! state dissoc :billing-accounts :error)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-billing-accounts)
       :on-done
       (fn [{:keys [success? status-code raw-response]}]
         (let [[parsed parse-error?] (utils/parse-json-string raw-response false false)]
           (if (and success? (not parse-error?))
             (swap! state assoc
                    :billing-accounts parsed
                    :selected-account (get (first parsed) "accountName"))
             (swap! state assoc
                    :error {:code status-code :details (if parse-error? raw-response parsed)}))))}))
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
               :headers utils/content-type=json
               :on-done (fn [{:keys [success? get-parsed-response]}]
                          (swap! state dissoc :creating?)
                          (if success?
                            (do ((:on-success props))
                                (modal/pop-modal))
                            (swap! state assoc :server-error (get-parsed-response false))))}))))))})
