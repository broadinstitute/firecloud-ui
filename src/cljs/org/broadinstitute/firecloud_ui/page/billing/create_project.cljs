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
              :permissions-required
              [:div {:style {:textAlign "center"}}
               "Billing permissions are not enabled." [:br] [:br]
               [comps/Button {:text "Enable Billing Permissions"
                              :onClick #(react/call :.enable-billing-permissions this
                                                    (:details error))}]]
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
                                      (even? row-index) (:background-light style/colors)
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
     (react/call :get-billing-accounts this))
   :.enable-billing-permissions
   (fn [{:keys [this]} scopes-needed]
     (utils/add-user-listener
      ::billing
      (fn [_]
        (utils/remove-user-listener ::billing)
        (react/call :get-billing-accounts this)))
     (js-invoke
      @utils/google-auth2-instance
      "grantOfflineAccess"
      (clj->js {:redirect_uri "postmessage" :scope (clojure.string/join " " scopes-needed)})))
   :get-billing-accounts
   (fn [{:keys [state]}]
     (swap! state dissoc :billing-accounts :error)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-billing-accounts)
       :on-done
       (fn [{:keys [success? status-code raw-response]}]
         (let [[parsed parse-error?] (utils/parse-json-string raw-response false false)
               [message message-parse-error?] (when (and (not success?) (not parse-error?))
                                                (utils/parse-json-string
                                                 (parsed "message") true false))]
           (cond
             (and success? (not parse-error?))
             (swap! state assoc
                    :billing-accounts parsed :selected-account (get (first parsed) "accountName"))
             success?
             (swap! state assoc :error {:code :parse-error :details raw-response})
             parse-error?
             (swap! state assoc :error
                    {:code :unknown :status-code status-code :details raw-response})
             (and (= status-code 403) (contains? message :requiredScopes))
             (swap! state assoc :error {:code :permissions-required
                                        :details (:requiredScopes message)})
             :else
             (swap! state assoc :error {:code status-code :details parsed}))))}))
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
