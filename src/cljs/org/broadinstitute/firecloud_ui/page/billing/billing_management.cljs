(ns org.broadinstitute.firecloud-ui.page.billing.billing-management
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.table-utils :refer [float-right]]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.nav :as nav]
    [org.broadinstitute.firecloud-ui.page.billing.create-project :refer [CreateBillingProjectDialog]]
    [org.broadinstitute.firecloud-ui.page.billing.manage-project :refer [BillingProjectManagementPage]]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

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
        {:columns [{:header "Project Name" :starting-width 400
                    :as-text #(% "projectName") :sort-by :text
                    :content-renderer
                    (fn [{:strs [projectName role]}]
                      (if (= role "Owner")
                        (style/create-link {:text projectName :onClick #((:on-select props) projectName)})
                        projectName))}
                   {:header "Role" :starting-width 100}]
         :toolbar
         (float-right
           (when false ; hidden until implemented
             [comps/Button {:text "Create New Billing Project"
                            :onClick (fn []
                                       (modal/push-modal
                                         [CreateBillingProjectDialog {:on-success #(react/call :reload this)}]))}]))
         :data (:projects @state)
         :->row (fn [{:strs [role] :as row}]
                  [row
                   role])}]))
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
   (fn [{:keys [props]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           selected-project (not-empty (:segment nav-context))]
       [:div {:style {:padding "1em"}}
        [:div {:style {:fontSize "180%" :marginBottom "1em"}}
         [comps/Breadcrumbs {:crumbs [{:text "Billing Management" :onClick #(nav/back nav-context)}
                                      (when selected-project {:text selected-project})]}]]
        (if selected-project
          [BillingProjectManagementPage {:project-name selected-project}]
          [BillingProjectTable {:on-select #(nav/navigate nav-context %)}])]))})
