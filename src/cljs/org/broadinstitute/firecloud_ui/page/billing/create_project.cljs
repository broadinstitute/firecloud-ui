(ns org.broadinstitute.firecloud-ui.page.billing.create-project
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.input :as input]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
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
               [:div {:style {:fontSize "120%"}}
                "1. Enter a unique name:"]
               [input/TextField {:ref "name-field"
                                 :style {:width "100%" :marginTop "1em" :marginBottom 0}
                                 :predicates [{:test #(re-matches #"[a-z0-9\-]*" %) :message "Name contains invalid characters"}
                                              {:test #(<= 6 (count %) 30) :message "Name must be 6-30 characters long"}
                                              {:test #(re-matches #"[a-z]" (first %)) :message "Name must start with a letter"}]}]
               (style/create-validation-error-message (:validation-errors @state))
               [:div {:style {:marginBottom "1.5em"
                              :color (if (:validation-errors @state) (:exception-state style/colors) (:text-lighter style/colors))
                              :fontSize "0.8em"}}
                "Lowercase letters, numbers, and hypens only, 6-30 characters, must start with a letter."]
               [:div {:style {:fontSize "120%"}}
                "2. Select a billing account:"]
               [:div {:style {:fontSize "80%" :fontStyle "italic"}} "To grant FireCloud access to an account, enter its console and add billing@firecloud.org as a Billing Administrator."]
               (let [simple-th (fn [text]
                                 [:th {:style {:textAlign "left" :padding "0 0.5rem"}} text])
                     simple-td (fn [text]
                                 [:td {:style {:padding "0 0.5rem" :borderTop style/standard-line}} text])]
                 [:form {:style {:margin "1em 0 1em 0"}}
                  [:table {:style {:width "100%" :borderCollapse "collapse"}}
                   [:thead {} [:tr {}
                               (simple-th "")
                               (simple-th "Account Name")
                               (simple-th "Account ID")
                               (simple-th "FC Access")
                               (simple-th "Console")]]
                   [:tbody {}
                    (map (fn [account]
                           [:tr {:style {:borderTop style/standard-line}}
                            (simple-td [:input {:type "radio" :value (account "accountName")
                                                :disabled (not (account "firecloudHasAccess"))
                                                :id (account "accountName")
                                                :onChange (fn [event]
                                                            (when (aget event "target" "checked")
                                                              (swap! state assoc :selected-account (account "accountName"))))}])
                            (simple-td [:label {:htmlFor (account "accountName")} (account "displayName")])
                            (simple-td [:label {:htmlFor (account "accountName")} (account "accountName")])
                            (simple-td (if (account "firecloudHasAccess") "Yes" "No"))
                            (simple-td [:a
                                        {:href (str
                                                "https://console.developers.google.com/billing/"
                                                (second (clojure.string/split (account "accountName") #"/")))
                                         :target "_blank"}
                                        (icons/icon {:style {:textDecoration "none" :color (:button-primary style/colors)}} :new-window)])])
                         billing-accounts)]]])
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
                    :selected-account nil)
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
