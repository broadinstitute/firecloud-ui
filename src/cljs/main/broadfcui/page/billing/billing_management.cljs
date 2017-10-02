(ns broadfcui.page.billing.billing-management
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.links :as links]
   [broadfcui.common.management-utils :as management-utils]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.page.billing.create-project :refer [CreateBillingProjectDialog]]
   [broadfcui.page.workspace.monitor.common :as moncommon]
   [broadfcui.utils :as utils]
   ))


(def ^:private project-refresh-interval-ms 10000)
(def ^:private project-status-creating "Creating")
(def ^:private project-status-ready "Ready")
(def ^:private project-status-error "Error")


(react/defc- PendingProjectControl
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


(react/defc- BillingProjectTable
  {:reload
   (fn [{:keys [this]}]
     (react/call :load-data this))
   :render
   (fn [{:keys [state this]}]
     (cond
       (:error-message @state) (style/create-server-error-message (:error-message @state))
       (nil? (:projects @state)) [comps/Spinner {:text "Loading billing projects..."}]
       :else
       (let [projects (->>
                       (:projects @state)
                       (group-by :projectName)
                       (map (fn [[_ v]]
                              (assoc (first v) :roles (sort (map :role v))))))]
         [Table
          {:data-test-id "billing-project-table"
           :data projects
           :body {:behavior {:reorderable-columns? false}
                  :style table-style/table-light
                  :data-props {:row (fn [project] {:data-test-id (str (:projectName project) "-row")})}
                  :columns
                  [{:id "Status Icon" :initial-width 16
                    :resizable? false :sortable? false :filterable? false
                    :column-data :creationStatus
                    :render
                    (fn [creation-status]
                      [:div {:title creation-status :style {:height table-style/table-icon-size}}
                       (moncommon/icon-for-project-status creation-status)])}
                   {:header "Project Name" :initial-width 500 :sort-initial :asc
                    :as-text :projectName :sort-by :text
                    :render
                    (fn [{:keys [projectName roles creationStatus message]}]
                      [:span {}
                       (cond
                         (= creationStatus project-status-creating)
                         [PendingProjectControl
                          {:project-name projectName
                           :on-status-change (partial this :-handle-status-change projectName)}]
                         (and (= creationStatus project-status-ready) (contains? (set roles) "Owner"))
                         (links/create-internal {:data-test-id (str projectName "-link")
                                                 :href (nav/get-link :billing-project projectName)}
                                                projectName)
                         :else projectName)
                       (when message
                         [:div {:style {:float "right" :position "relative"
                                        :height table-style/table-icon-size}}
                          (common/render-info-box
                           {:text [:div {} [:strong {} "Message:"] [:br] message]})])])}
                   {:header "Role" :initial-width :auto
                    :column-data #(clojure.string/join ", " (:roles %))}]}
           :toolbar
           {:get-items
            (constantly
             [flex/spring
              [buttons/Button
               {:data-test-id "begin-create-billing-project"
                :text "Create New Billing Project..."
                :onClick
                (fn []
                  (if (-> @utils/auth2-atom (aget "currentUser") (js-invoke "get")
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
                       @utils/auth2-atom
                       "grantOfflineAccess"
                       (clj->js {:redirect_uri "postmessage"
                                 :scope "https://www.googleapis.com/auth/cloud-billing"})))))}]])}}])))
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
           updated-project (assoc project :creationStatus new-status :message message)]
       (swap! state assoc-in [:projects project-index] updated-project)))})


(react/defc- Page
  {:render
   (fn [{:keys [props]}]
     (let [{:keys [project-name]} props]
       [:div {:style style/thin-page-style}
        [:div {:style {:marginBottom "1rem" :fontSize "1.1rem"}}
         [:div {:style {:fontSize "1.2em"}} (when project-name "Billing Project: ")
          [:span {:style {:fontWeight 500}} (if project-name project-name "Billing Management")]]]
        (if project-name
          [management-utils/MembershipManagementPage
           {:admin-term "Owner"
            :user-term "User"
            :group-name project-name
            :add-endpoint #(endpoints/add-billing-project-user {:project-id project-name
                                                                :role %1
                                                                :user-email %2})
            :delete-endpoint #(endpoints/delete-billing-project-user {:project-id project-name
                                                                      :role %1
                                                                      :user-email %2})
            :table-data identity
            :add-member-footer [:div {:style {:marginBottom "1em"}}
                                "Warning: Adding any user to this project will mean
                                they can incur costs to the billing associated with this project."]
            :list-endpoint endpoints/list-billing-project-members}]
          [BillingProjectTable])]))})

(defn add-nav-paths []
  (nav/defpath
   :billing
   {:component Page
    :regex #"billing"
    :make-props (fn [_] {})
    :make-path (fn [] "billing")})
  (nav/defpath
   :billing-project
   {:component Page
    :regex #"billing/([^/]+)"
    :make-props (fn [project-name] (utils/restructure project-name))
    :make-path (fn [project-name] (str "billing/" project-name))}))
