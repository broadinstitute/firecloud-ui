(ns broadfcui.page.workspace.summary.tab
  (:require
    [clojure.set :refer [difference]]
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.markdown :refer [MarkdownView MarkdownEditor]]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.page.workspace.monitor.common :as moncommon :refer [all-success? any-running? any-failed?]]
    [broadfcui.page.workspace.summary.acl-editor :refer [AclEditor]]
    [broadfcui.page.workspace.summary.attribute-editor :as attributes]
    [broadfcui.page.workspace.summary.catalog.wizard :refer [CatalogWizard]]
    [broadfcui.page.workspace.summary.publish :as publish]
    [broadfcui.page.workspace.summary.library-view :refer [LibraryView]]
    [broadfcui.page.workspace.summary.workspace-cloner :refer [WorkspaceCloner]]
    [broadfcui.utils :as utils]))


(react/defc DeleteDialog
  {:render
   (fn [{:keys [state this]}]
     [comps/OKCancelForm
       {:header "Confirm Delete"
        :content
        [:div {}
         (when (:deleting? @state)
           [comps/Blocker {:banner "Deleting..."}])
         [:p {:style {:margin 0}} "Are you sure you want to delete this workspace?"]
         [:p {} "Bucket data will be deleted too."]
         [comps/ErrorViewer {:error (:server-error @state)}]]
        :ok-button {:text "Delete" :onClick #(react/call :delete this)}}])
   :delete
   (fn [{:keys [props state]}]
     (swap! state assoc :deleting? true :server-error nil)
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/delete-workspace (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response]}]
                   (swap! state dissoc :deleting?)
                   (if success?
                     (do (modal/pop-modal) ((:on-delete props)))
                     (swap! state assoc :server-error (get-parsed-response false))))}))})


(defn- save-attributes [{:keys [new-attributes state workspace-id request-refresh]}]
  (swap! state assoc :updating-attrs? true)
  (endpoints/call-ajax-orch
    {:endpoint (endpoints/set-workspace-attributes workspace-id)
     :payload new-attributes
     :headers utils/content-type=json
     :on-done (fn [{:keys [success? get-parsed-response]}]
                (swap! state dissoc :updating-attrs? :editing?)
                (if success?
                  (request-refresh)
                  (comps/push-error-response (get-parsed-response false))))}))


(defn- render-sidebar [state refs this
                       {:keys [workspace billing-projects owner? writer? curator?
                               workspace-id on-clone on-delete request-refresh]}]
  (let [{{:keys [isLocked library-attributes description isProtected]} :workspace
         {:keys [runningSubmissionsCount]} :workspaceSubmissionStats} workspace
        status (common/compute-status workspace)
        {:keys [sidebar-visible? editing?]
         {:keys [library-schema]} :server-response} @state]
    [:div {:style {:flex "0 0 270px" :paddingRight 30}}
     [comps/StatusLabel {:text (str status
                                 (when (= status "Running")
                                   (str " (" runningSubmissionsCount ")")))
                         :icon (case status
                                 "Complete" [icons/CompleteIcon {:size 36}]
                                 "Running" [icons/RunningIcon {:size 36}]
                                 "Exception" [icons/ExceptionIcon {:size 32}])
                         :color (style/color-for-status status)}]
     [:div {:ref "sidebar"}]
     (style/create-unselectable
       :div {:style {:position (when-not sidebar-visible? "fixed")
                     :top (when-not sidebar-visible? 0)
                     :width 270}}
       (when (and curator? writer? (not editing?))
         [comps/SidebarButton
          {:style :light :color :button-primary :margin :top
           :icon :catalog :text "Catalog Dataset..."
           :onClick #(modal/push-modal [CatalogWizard {:library-schema library-schema
                                                       :workspace workspace
                                                       :workspace-id workspace-id
                                                       :request-refresh request-refresh}])}])
       (when (and curator? owner? (not editing?))
         (if (:library:published library-attributes)
           [publish/UnpublishButton {:workspace-id workspace-id
                                     :request-refresh request-refresh}]
           [publish/PublishButton {:disabled? (when (empty? library-attributes)
                                                "Dataset attributes must be created before publishing.")
                                   :workspace-id workspace-id
                                   :request-refresh request-refresh}]))
       (when (or owner? writer?)
         (if (not editing?)
           [comps/SidebarButton
            {:style :light :color :button-primary :margin :top
             :text "Edit" :icon :edit
             :onClick #(swap! state assoc :editing? true)}]
           [:div {}
            [comps/SidebarButton
             {:style :light :color :button-primary :margin :top
              :text "Save" :icon :done
              :onClick (fn [_]
                         (let [{:keys [success error]} (react/call :get-attributes (@refs "workspace-attribute-editor"))
                               new-description (react/call :get-text (@refs "description"))]
                           (if error
                             (comps/push-error-text error)
                             (save-attributes {:new-attributes (assoc success :description new-description)
                                               :state state
                                               :workspace-id workspace-id
                                               :request-refresh request-refresh}))))}]
            [comps/SidebarButton
             {:style :light :color :exception-state :margin :top
              :text "Cancel Editing" :icon :cancel
              :onClick #(swap! state dissoc :editing?)}]]))
       (when-not editing?
         [comps/SidebarButton {:style :light :margin :top :color :button-primary
                               :text "Clone..." :icon :clone
                               :disabled? (if (empty? billing-projects) [comps/NoBillingProjectsMessage] false)
                               :onClick #(modal/push-modal
                                          [WorkspaceCloner
                                           {:on-success (fn [namespace name]
                                                          (swap! state dissoc :cloning?)
                                                          (on-clone (str namespace ":" name)))
                                            :workspace-id workspace-id
                                            :description description
                                            :is-protected? isProtected
                                            :billing-projects billing-projects}])}])
       (when (and owner? (not editing?))
         [comps/SidebarButton {:style :light :margin :top :color :button-primary
                               :text (if isLocked "Unlock" "Lock")
                               :icon (if isLocked :unlock :lock)
                               :onClick #(react/call :lock-or-unlock this isLocked)}])
       (when (and owner? (not editing?))
         [comps/SidebarButton {:style :light :margin :top :color (if isLocked :text-lighter :exception-state)
                               :text "Delete..." :icon :delete
                               :disabled? (if isLocked "This workspace is locked.")
                               :onClick #(modal/push-modal [DeleteDialog {:workspace-id workspace-id
                                                                          :on-delete on-delete}])}]))]))


(defn- render-main [{:keys [workspace curator? owner? writer? reader? can-share? bucket-access? editing? submissions-count
                            user-access-level library-schema request-refresh workspace-id storage-cost]}]
  (let [{:keys [owners]
         {:keys [createdBy createdDate bucketName description workspace-attributes library-attributes realm]} :workspace} workspace
        realm-name (:realmName realm)
        render-detail-box (fn [order title & children]
                            [:div {:style {:flexBasis "50%" :order order}}
                             (style/create-section-header title)
                             children])]
    [:div {:style {:flex "1 1 auto" :overflow "hidden"}}
     [:div {:style {:display "flex" :flexWrap "wrap"}}
      (render-detail-box
        1
        [common/FoundationTooltip {:text (str "Workspace Owner" (when (> (count owners) 1) "s"))
                                   :tooltip "Hey look, a tooltip"}]
        (style/create-paragraph
          [:div {}
           (interpose ", " owners)
           (when can-share?
             [:span {}
              " ("
              (style/create-link {:text "Sharing..."
                                  :onClick #(modal/push-modal
                                              [AclEditor {:workspace-id workspace-id
                                                          :user-access-level user-access-level
                                                          :request-refresh request-refresh}])})
              ")"])]
          (when realm-name
            [:div {:style {:paddingTop "0.5rem"}}
             [:div {:style {:fontStyle "italic"}} "Access restricted to realm:"]
             [:div {} realm-name]])))
      (render-detail-box
        3
        [common/FoundationTooltip {:text "Created By"
                                   :tooltip "Another tooltip, I'm such an incredibly cool dude"
                                   :position "right"}]
        (style/create-paragraph
          [:div {} createdBy]
          [:div {} (common/format-date createdDate)]))
      (render-detail-box
        2
        [common/FoundationTooltip {:text "Google Bucket"
                                   :tooltip "It's a tooltip party, and everyone's invited"
                                   :position "left"}]
        (style/create-paragraph
          (case bucket-access?
            nil [:div {:style {:position "absolute" :marginTop "-1.5em"}}
                 [comps/Spinner {:height "1.5ex"}]]
            true (style/create-link {:text bucketName
                                     :href (str moncommon/google-cloud-context bucketName "/")
                                     :style {:color "-webkit-link" :textDecoration "underline"}
                                     :title "Click to open the Google Cloud Storage browser for this bucket"
                                     :target "_blank"})
            false bucketName)
          (when (not reader?)
            [:div {}
             [:div {} (str "Total Estimated Storage Fee per month = " storage-cost)]
             [:div {:style {:fontSize "80%"}} (str "Note: the billing account associated with " (:namespace workspace-id) " will be charged.")]])))
      (render-detail-box
        4
        [common/FoundationTooltip {:text "Analysis Submissions"
                                   :tooltip "Yup, it sure is tooltippy in here"
                                   :position "top"}]
        (style/create-paragraph
          (let [count-all (apply + (vals submissions-count))]
            [:div {}
             (str count-all " Submission" (when-not (= 1 count-all) "s"))
             (when (pos? count-all)
               [:ul {:style {:marginTop 0}}
                (for [[status subs] (sort submissions-count)]
                 [:li {} (str subs " " status)])])])))]

    (when editing? [:div {:style {:marginBottom "10px"}} common/PHI-warning])

     (style/create-section-header
      [common/FoundationProperTooltip {:text "Description"
                                       :tooltip "A short summary or explanation"}])
     (style/create-paragraph
       (let [description (not-empty description)]
         (cond editing? (react/create-element [MarkdownEditor {:ref "description" :initial-text description}])
               description [MarkdownView {:text description}]
               :else [:span {:style {:fontStyle "italic"}} "No description provided"])))
     (when-not (empty? library-attributes)
       [LibraryView {:library-attributes library-attributes
                     :library-schema library-schema
                     :workspace workspace
                     :workspace-id workspace-id
                     :request-refresh request-refresh
                     :can-edit? (and curator? owner? (not editing?))}])
     [attributes/WorkspaceAttributeViewerEditor {:ref "workspace-attribute-editor"
                                                 :editing? editing?
                                                 :writer? writer?
                                                 :workspace-attributes workspace-attributes
                                                 :workspace-bucket bucketName
                                                 :workspace-id workspace-id
                                                 :request-refresh request-refresh}]]))

(defn- reader? [workspace]
  (= "READER" (:accessLevel workspace)))

(react/defc Summary
  {:get-initial-state
   (fn []
     {:sidebar-visible? true})
   :render
   (fn [{:keys [refs state props this]}]
     (let [{:keys [server-response]} @state
           {:keys [workspace]} props
           {:keys [submissions-count billing-projects library-schema curator? server-error]} server-response]
       (cond
         server-error
         (style/create-server-error-message server-error)
         (some nil? [workspace submissions-count billing-projects library-schema curator?])
         [:div {:style {:textAlign "center" :padding "1em"}}
          [comps/Spinner {:text "Loading workspace..."}]]
         :else
         (let [owner? (or (= "PROJECT_OWNER" (:accessLevel workspace)) (= "OWNER" (:accessLevel workspace)))
               writer? (or owner? (= "WRITER" (:accessLevel workspace)))
               can-share? (:canShare workspace)
               user-access-level (:accessLevel workspace)
               derived {:owner? owner? :writer? writer? :reader? (reader? (:workspace props))
                        :can-share? can-share? :user-access-level user-access-level :request-refresh #(react/call :refresh this)}]
           [:div {:style {:margin "45px 25px" :display "flex"}}
            (render-sidebar state refs this
                            (merge (select-keys props [:workspace :workspace-id :on-clone :on-delete])
                                   (select-keys server-response [:billing-projects :curator?])
                                   derived))
            (render-main (merge (select-keys props [:workspace :workspace-id :bucket-access?])
                                (select-keys @state [:editing?])
                                (select-keys server-response [:submissions-count :library-schema :curator? :storage-cost])
                                derived))
            (when (:updating-attrs? @state)
              [comps/Blocker {:banner "Updating Attributes..."}])
            (when (contains? @state :locking?)
              [comps/Blocker {:banner (if (:locking? @state) "Unlocking..." "Locking...")}])]))))
   :lock-or-unlock
   (fn [{:keys [props state this]} locked-now?]
     (swap! state assoc :locking? locked-now?)
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/lock-or-unlock-workspace (:workspace-id props) locked-now?)
        :on-done (fn [{:keys [success? status-text status-code]}]
                   (when-not success?
                     (if (and (= status-code 409) (not locked-now?))
                       (comps/push-error-text
                        "Could not lock workspace, one or more analyses are currently running")
                       (comps/push-error-text (str "Error: " status-text))))
                   (swap! state dissoc :locking?)
                   (react/call :refresh this))}))
   :component-did-mount
   (fn [{:keys [state refs locals this]}]
     (react/call :refresh this)
     (swap! locals assoc :scroll-handler
            (fn []
              (when-let [sidebar (@refs "sidebar")]
                (let [visible (< (.-scrollY js/window) (.-offsetTop sidebar))]
                  (when-not (= visible (:sidebar-visible? @state))
                    (swap! state assoc :sidebar-visible? visible))))))
     (.addEventListener js/window "scroll" (:scroll-handler @locals)))
   :component-did-update
   (fn [{:keys [locals]}]
     ((:scroll-handler @locals)))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (.removeEventListener js/window "scroll" (:scroll-handler @locals)))
   :refresh
   (fn [{:keys [props state]}]
     (swap! state dissoc :server-response)
     ((:request-refresh props))
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/count-submissions (:workspace-id props))
        :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                   (if success?
                     (swap! state update :server-response assoc :submissions-count (get-parsed-response false))
                     (swap! state update :server-response assoc :server-error status-text)))})
     (endpoints/get-billing-projects
      (fn [err-text projects]
        (if err-text
          (swap! state update :server-response assoc :server-error err-text)
          (swap! state update :server-response
                 assoc :billing-projects (map #(% "projectName") projects)))))
     (endpoints/get-library-attributes
       (fn [{:keys [success? get-parsed-response]}]
         (if success?
           (let [response (get-parsed-response)]
             (swap! state update :server-response assoc :library-schema
                    (-> response
                        (update-in [:display :primary] (partial map keyword))
                        (update-in [:display :secondary] (partial map keyword)))))
           (swap! state update :server-response assoc :server-error "Unable to load library schema"))))
     (endpoints/call-ajax-orch
       {:endpoint endpoints/get-library-curator-status
        :on-done (fn [{:keys [success? get-parsed-response]}]
                   (if success?
                     (swap! state update :server-response assoc :curator? (:curator (get-parsed-response)))
                     (swap! state update :server-response assoc :server-error "Unable to determine curator status")))})
     (when (not (reader? (:workspace props)))
       (endpoints/call-ajax-orch
         {:endpoint (endpoints/storage-cost-estimate (:workspace-id props))
          :on-done (fn [{:keys [success? status-text raw-response]}]
                     (let [[response parse-error?] (utils/parse-json-string raw-response false false)]
                       (swap! state update :server-response assoc :storage-cost
                              (if parse-error?
                                (str "Error parsing JSON response with status: " status-text)
                                (let [key (if success? "estimate" "message")]
                                  (get response key (str "Error: \"" key "\" not found in JSON response with status: " status-text)))))))})))})
