(ns org.broadinstitute.firecloud-ui.page.billing.billing-management
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.table-utils :refer [add-right]]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.nav :as nav]
    [org.broadinstitute.firecloud-ui.page.billing.create-project :refer [CreateBillingProjectDialog]]
    [org.broadinstitute.firecloud-ui.page.billing.manage-project :refer [BillingProjectManagementPage]]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(def ^:private project-refresh-interval-ms 10000)
(def ^:private project-status-creating "Creating")


(react/defc PendingProjectControl
  {:render
   (fn [{{:keys [project-name]} :props :keys [state]}]
     [:span {}
      [:span {:style {:fontWeight "normal"}} "pending: "]
      [:span {:style {:fontStyle "italic"}} project-name]
      (when (:busy @state)
        [comps/AnimatedEllipsis])])
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :-set-timeout this project-refresh-interval-ms
                 #(react/call :-refresh-status this)))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (dorun (map (fn [id] (js/clearTimeout id)) (vals (:timeouts @locals)))))
   :-refresh-status
   (fn [{{:keys [project-name on-status-change]} :props :keys [this state]}]
     (swap! state assoc :busy (.getTime (js/Date.)))
     (endpoints/get-billing-project-status
      project-name
      (fn [new-status]
        (if (and new-status (not= new-status project-status-creating))
          (do
            (swap! state dissoc :busy)
            (on-status-change new-status))
          ;; Ensure a minimum of 2000ms of animation.
          (let [request-time (- (.getTime (js/Date.)) (:busy @state))]
            (react/call :-set-timeout this (max 0 (- 2000 request-time))
                        #(swap! state dissoc :busy))
            (react/call :-set-timeout this project-refresh-interval-ms
                        #(react/call :-refresh-status this)))))))
   :-set-timeout
   (fn [{:keys [locals]} ms f]
     (swap! locals assoc-in [:timeouts f]
            (js/setTimeout (fn [] (swap! locals update-in [:timeouts] dissoc f) (f)) ms)))})


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
                    (fn [{:strs [projectName role creationStatus]}]
                      [:span {}
                       (cond
                         (= creationStatus project-status-creating)
                         [PendingProjectControl
                          {:project-name projectName
                           :on-status-change #(react/call :-handle-status-change this
                                                          projectName %)}]
                         (= role "Owner")
                         (style/create-link {:text projectName
                                             :onClick #((:on-select props) projectName)})
                         :else projectName)])}
                   {:header "Role" :starting-width :remaining}]
         :toolbar
         (add-right
          [comps/Button
           {:text "Create New Billing Project"
            :onClick (fn [_]
                       (if (-> @utils/google-auth2-instance (aget "currentUser") (js-invoke "get")
                               (js-invoke "hasGrantedScopes" "https://www.googleapis.com/auth/cloud-billing"))
                         (modal/push-modal
                          [CreateBillingProjectDialog
                           {:on-success #(react/call :reload this)}])
                         (fn [{:keys [this]} scopes-needed]
                           (utils/add-user-listener
                            ::billing
                            (fn [_]
                              (utils/remove-user-listener ::billing)
                              (modal/push-modal
                               [CreateBillingProjectDialog
                                {:on-success #(react/call :reload this)}])))
                           (js-invoke
                            @utils/google-auth2-instance
                            "grantOfflineAccess"
                            (clj->js {:redirect_uri "postmessage" :scope (clojure.string/join " " scopes-needed)})))))}])
         :data (:projects @state)
         :->row (fn [{:strs [role] :as row}]
                  [row
                   role])}]))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-data this))
   :load-data
   (fn [{:keys [state]}]
     (endpoints/get-billing-projects
      true
      (fn [err-text projects]
        (if err-text
          (swap! state assoc :error-message err-text)
          (swap! state assoc :projects projects)))))
   :-handle-status-change
   (fn [{:keys [state]} project-name new-status]
     (let [project-index (utils/first-matching-index
                          #(= (% "projectName") project-name)
                          (:projects @state))]
       (swap! state assoc-in [:projects project-index "creationStatus"] new-status)))})


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
