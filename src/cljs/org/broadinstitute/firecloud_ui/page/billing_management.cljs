(ns org.broadinstitute.firecloud-ui.page.billing-management
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.nav :as nav]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

(react/defc Table
  {:reload
   (fn [{:keys [state]}]
       (swap! state dissoc :projects))
   :render
   (fn [{:keys [props state]}]
       (cond
         (:hidden? props) nil
         (:error-message @state) (style/create-server-error-message (:error-message @state))
         (nil? (:projects @state))
         [comps/Spinner {:text "Loading billing projects..."}]
         :else
         [table/Table
          {:columns [{:header "Project Name" :starting-width 300}
                     {:header "Role" :starting-width 300}]
           :toolbar
            (fn [built-in]
                [:div {}
                 [:div {:style {:float "left"}} built-in]
                 [:div {:style {:float "right"}}
                  [comps/Button {:text "Create New Billing Project"
                                 :disabled? true
                                 :onClick #(swap! state assoc :foo "bar")}]]
                 (common/clear-both)])
           :data (:projects @state)
           :->row (fn [item]
                      [item
                       "Member"])}]))
   :component-did-mount #(react/call :load-data (:this %))
   :load-data
   (fn [{:keys [this state]}]
       (when-not (some (or @state {}) [:projects :error-message])
                 (endpoints/call-ajax-orch
                   {:endpoint (endpoints/get-billing-projects)
                    :on-done
                              (fn [{:keys [success? get-parsed-response status-text]}]
                                  (if success?
                                    (swap! state assoc :projects (get-parsed-response))
                                    (swap! state assoc :error-message status-text)))})))})

(react/defc BillingProjectTable
  {:render
   (fn [{:keys [this props state refs]}]
       [:div {}
        (if-let [item (:selected-item @state)]
                [:div {}
                 (style/create-link {:text "Billing Management"
                                     :onClick #(swap! state dissoc :selected-item)})
                 (icons/font-icon {:style {:verticalAlign "middle" :margin "0 1ex 0 1ex"}} :angle-right)]
                [:h2 {} "Billing Management"])
        [Table {:ref "table"}]])})

(react/defc Page
  {:render
   (fn [{:keys [state]}]
       [:div {:style {:padding "1em"}}
        [BillingProjectTable]])})