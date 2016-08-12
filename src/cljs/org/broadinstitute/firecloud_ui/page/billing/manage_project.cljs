(ns org.broadinstitute.firecloud-ui.page.billing.manage-project
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.input :as input]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.table-utils :refer [float-right]]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

(react/defc BillingProjectManagementPage
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [load-error members]} @state]
       (cond load-error [comps/ErrorViewer {:error load-error
                                            :expect {403 "You are not an owner of this billing project."}}]
             (not members) [comps/Spinner {:text "Loading project membership..."}]
             :else
             [:div {:style {:backgroundColor "white" :padding "1em"}}
              [table/Table
               {:toolbar (float-right [comps/Button {:text "Add User..." :style :add}])
                :columns [{:header "Email" :starting-width 300}
                          {:header "Role" :starting-width 100}
                          {:header "Actions" :starting-width 100
                           :filter-by :none :sort-by :none
                           :content-renderer
                           (fn [{:strs [email row]}]
                             (style/create-link {:text "Remove"
                                                 :onClick #(utils/log "remove")}))}]
                :data members
                :->row (fn [{:strs [email role] :as row}]
                         [email
                          role
                          row])}]])))
   :component-did-mount
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/list-billing-project-members (:project-name props))
        :on-done (fn [{:keys [success? get-parsed-response]}]
                   (swap! state assoc (if success? :members :load-error) (get-parsed-response)))}))})
