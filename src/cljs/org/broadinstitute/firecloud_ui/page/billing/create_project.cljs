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
        (let [{:keys [billing-accounts billing-acct-error get-error-details]} @state]
          (cond billing-acct-error
                (if (= billing-acct-error 403)
                  [:div {:style {:textAlign "center"}}
                   "Billing permissions are not enabled." [:br] [:br]
                   [comps/Button {:text "Enable Billing Permissions"
                                  :onClick #(react/call :.enable-billing-permissions this)}]]
                  [comps/ErrorViewer {:error (get-error-details)}])
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
   (fn [{:keys [this]}]
     (-> @utils/google-auth2-instance
         (.grantOfflineAccess (clj->js {:redirect_uri "postmessage"
                                        :scope "https://www.googleapis.com/auth/cloud-billing"}))
         (.then (fn [response]
                  ;; At some point, the access token gets replaced with a new one, but it doesn't
                  ;; happen right away, so this dummy delay is necessary.
                  (js/setTimeout
                   #(react/call :get-billing-accounts this)
                   1000)))))
   :get-billing-accounts
   (fn [{:keys [state]}]
     (swap! state dissoc :billing-accounts :billing-acct-error :get-error-details)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-billing-accounts)
       :on-done
       (fn [{:keys [success? status-code get-parsed-response]}]
         (if success?
           (let [accts (get-parsed-response false)]
             (swap! state assoc :billing-accounts accts :selected-account (get (first accts) "accountName")))
           (swap! state assoc
                  :billing-acct-error status-code
                  :get-error-details (partial get-parsed-response false))))}))
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
