(ns broadfcui.page.billing.create-project
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.input :as input]
   [broadfcui.common.style :as style]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   ))

(react/defc CreateBillingProjectDialog
  {:render
   (fn [{:keys [props state this]}]
     [modals/OKCancelForm
      {:header "Create Billing Project"
       :content
       (react/create-element
        (let [{:keys [billing-accounts error]} @state]
          (cond
            (not (or billing-accounts error)) (spinner "Loading billing accounts...")
            error
            (case (:code error)
              (:unknown :parse-error)
              [:div {:style {:color (:state-exception style/colors)}}
               "Error:" [:br] (:details error)]
              [comps/ErrorViewer {:error (:details error)}])
            :else
            (if (empty? billing-accounts)
              [:div {} "You do not have any billing accounts available. "
               (links/create-external {:href (config/billing-account-guide-url)}
                 "Learn how to create a billing account.")]
              [:div {:style {:width 750}
                     :data-test-id "create-billing-project-form"}
               (when (:creating? @state)
                 (blocker "Creating billing project..."))
               [:div {:style {:fontSize "120%"}}
                "1. Enter a unique name:"]
               [input/TextField {:ref "name-field" :autoFocus true
                                 :data-test-id "project-name-input"
                                 :style {:width "100%" :marginTop "1em" :marginBottom 0}
                                 :predicates [{:test #(<= 6 (count %) 30) :message "Name must be 6-30 characters long"}
                                              {:test #(re-matches #"[a-z0-9\-]*" %) :message "Name contains invalid characters"}
                                              {:test #(and (not (string/blank? %)) (re-matches #"[a-z]" (first %))) :message "Name must start with a letter"}]}]
               (style/create-validation-error-message (:validation-errors @state))
               [:div {:style {:marginBottom "1.5em"
                              :color (if (:validation-errors @state) (:state-exception style/colors) (:text-lighter style/colors))
                              :fontSize "0.8em"}}
                "Lowercase letters, numbers, and hypens only, 6-30 characters, must start with a letter."]
               [:div {:style {:fontSize "120%"}}
                "2. Select a billing account:"]
               [:div {:style {:fontSize "80%" :fontStyle "italic"}} "To grant FireCloud access to an account, enter its console and add billing@firecloud.org as a Billing Account User."]
               (let [simple-th (fn [text]
                                 [:th {:style {:textAlign "left" :padding "0 0.5rem"}} text])
                     simple-td (fn [for text]
                                 [:td {:style {:borderTop style/standard-line}}
                                  [:label {:htmlFor for :style {:display "block" :padding "0 0.5rem"}} text]])]
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
                            [:td {:style {:borderTop style/standard-line}}
                             [:input {:type "radio" :value (account "accountName")
                                      :data-test-id (str (account "displayName") "-radio")
                                      :name "billing-account-select"
                                      :disabled (not (account "firecloudHasAccess"))
                                      :id (account "accountName")
                                      :onChange (fn [event]
                                                  (when (aget event "target" "checked")
                                                    (swap! state assoc :selected-account (account "accountName"))))}]]
                            (simple-td (account "accountName") (account "displayName"))
                            (simple-td (account "accountName") (account "accountName"))
                            (simple-td (account "accountName") (if (account "firecloudHasAccess") "Yes" "No"))
                            [:td {:style {:borderTop style/standard-line}}
                             [:a
                              {:href (str
                                      "https://console.developers.google.com/billing/"
                                      (second (string/split (account "accountName") #"/")))
                               :target "_blank"}
                              (icons/render-icon {:style {:textDecoration "none" :color (:button-primary style/colors)}} :new-window)]]])
                         billing-accounts)]]
                  (style/create-validation-error-message (:account-errors @state))])
               [comps/ErrorViewer {:error (:server-error @state)}]]))))
       :dismiss (:dismiss props)
       :ok-button (when (seq (:billing-accounts @state))
                    #(this :create-billing-project))}])
   :component-did-mount
   (fn [{:keys [this]}]
     (this :get-billing-accounts))
   :get-billing-accounts
   (fn [{:keys [state]}]
     (swap! state dissoc :billing-accounts :error)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-billing-accounts)
       :on-done
       (fn [{:keys [success? status-code raw-response]}]
         (let [[parsed parse-error?] (utils/parse-json-string raw-response false false)]
           (if (and success? (not parse-error?))
             (utils/multi-swap! state (assoc :billing-accounts parsed) (dissoc :selected-account))
             (swap! state assoc
                    :error {:code status-code :details (if parse-error? raw-response parsed)}))))}))
   :create-billing-project
   (fn [{:keys [props state refs]}]
     (let [{:keys [dismiss on-success]} props
           account (:selected-account @state)]
       (when-not account
         (swap! state assoc :account-errors ["Please select a billing account"]))
       (let [[name & fails] (input/get-and-validate refs "name-field")]
         (swap! state assoc :validation-errors fails)
         (when-not (or fails (not account))
           (swap! state assoc :creating? true)
           (endpoints/call-ajax-orch
            {:endpoint endpoints/create-billing-project
             :payload {:projectName name :billingAccount account}
             :headers ajax/content-type=json
             :on-done (fn [{:keys [success? get-parsed-response]}]
                        (swap! state dissoc :creating?)
                        (if success?
                          (do (dismiss) (on-success))
                          (swap! state assoc :server-error (get-parsed-response false))))})))))})
