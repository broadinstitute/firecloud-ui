(ns org.broadinstitute.firecloud-ui.page.billing-management
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.overlay :as dialog]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.table-utils :refer [float-right]]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

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
                            :disabled? true
                            :onClick #(swap! state assoc :foo "bar")}]))
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
      [Table]])})
