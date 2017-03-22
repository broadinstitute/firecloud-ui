(ns broadfcui.page.billing.billing-management
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.flex-utils :as flex]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.common.table.table :refer [Table]]
    [broadfcui.common.table.prefabs :refer [LightTable]]
    [broadfcui.common.table.style :as table-style]
    [broadfcui.common.table.utils :as table-utils]
    [broadfcui.common.table-utils :refer [add-right]]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.nav :as nav]
    [broadfcui.page.billing.create-project :refer [CreateBillingProjectDialog]]
    [broadfcui.page.billing.manage-project :refer [BillingProjectManagementPage]]
    [broadfcui.page.workspace.monitor.common :as moncommon]
    [broadfcui.utils :as utils]
    ))


(def ^:private project-refresh-interval-ms 10000)
(def ^:private project-status-creating "Creating")
(def ^:private project-status-ready "Ready")
(def ^:private project-status-error "Error")


(react/defc PendingProjectControl
  {:render
   (fn [{{:keys [project-name]} :props :keys [state]}]
     [:span {}
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
      (fn [new-status message]
        (if (and new-status (not= new-status project-status-creating))
          (do
            (swap! state dissoc :busy)
            (on-status-change new-status message))
          ;; Ensure a minimum of 2000ms of animation.
          (let [request-time (- (.getTime (js/Date.)) (:busy @state))]
            (react/call :-set-timeout this (max 0 (- 2000 request-time))
                        #(swap! state dissoc :busy))
            (react/call :-set-timeout this project-refresh-interval-ms
                        #(react/call :-refresh-status this)))))))
   :-set-timeout
   (fn [{:keys [locals]} ms f]
     (swap! locals assoc-in [:timeouts f]
            (js/setTimeout (fn [] (swap! locals update :timeouts dissoc f) (f)) ms)))})


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
       [LightTable
        {:table {:data-source (table-utils/local (:projects @state))
                 :columns [{:id "Status Icon" :initial-width 16
                            :resizable? false :sortable? false :filterable? false
                            :column-data :creationStatus
                            :render
                            (fn [creation-status]
                              [:div {:title creation-status :style {:height table-style/table-icon-size}}
                               (moncommon/icon-for-project-status creation-status)])}
                           {:header "Project Name" :initial-width 500 :sort-initial :asc
                            :as-text :projectName
                            :render
                            (fn [{:keys [projectName role creationStatus message]}]
                              [:span {}
                               (cond
                                 (= creationStatus project-status-creating)
                                 [PendingProjectControl
                                  {:project-name projectName
                                   :on-status-change (partial this :-handle-status-change projectName)}]
                                 (and (= creationStatus project-status-ready) (= role "Owner"))
                                 (style/create-link {:text projectName
                                                     :onClick #((:on-select props) projectName)})
                                 :else projectName)
                               (when message
                                 [:div {:style {:float "right" :position "relative"
                                                :height table-style/table-icon-size}}
                                  (common/render-info-box
                                   {:text [:div {} [:strong {} "Message:"] [:br] message]})])])}
                           {:header "Role" :initial-width :auto :column-data :role}]}
         :toolbar-items
         [flex/spring
          [comps/Button
           {:text "Create New Billing Project"
            :onClick (fn []
                       (if (-> @utils/google-auth2-instance (aget "currentUser") (js-invoke "get")
                               (js-invoke "hasGrantedScopes" "https://www.googleapis.com/auth/cloud-billing"))
                         (modal/push-modal
                          [CreateBillingProjectDialog
                           {:on-success #(react/call :reload this)}])
                         (do
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
                            (clj->js {:redirect_uri "postmessage" :scope "https://www.googleapis.com/auth/cloud-billing"})))))}]]}]))
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
   (fn [{:keys [state]} project-name new-status message]
     (let [project-index (utils/first-matching-index
                          #(= (:projectName %) project-name)
                          (:projects @state))
           project (get-in @state [:projects project-index])
           updated-project (assoc project "creationStatus" new-status "message" message)]
       (swap! state assoc-in [:projects project-index] updated-project)))})


(react/defc Page
  {:render
   (fn [{:keys [props]}]
     (let [nav-context (nav/parse-segment (:nav-context props))
           selected-project (not-empty (:segment nav-context))]
       [:div {:style {:padding "1em"}}
        [:div {:style {:marginBottom "1rem" :fontSize "1.1rem"}}
         [comps/Breadcrumbs {:crumbs [{:text "Billing Management" :onClick #(nav/back nav-context)}
                                      (when selected-project {:text selected-project})]}]]
        (if selected-project
          [BillingProjectManagementPage {:project-name selected-project}]
          [BillingProjectTable {:on-select #(nav/navigate nav-context %)}])]))})
