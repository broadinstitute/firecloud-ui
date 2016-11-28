(ns org.broadinstitute.firecloud-ui.page.workspace.summary.tab
  (:require
    [clojure.set :refer [difference]]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.markdown :refer [MarkdownView MarkdownEditor]]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.nav :as nav]
    [org.broadinstitute.firecloud-ui.page.workspace.monitor.common :as moncommon :refer [all-success? any-running? any-failed?]]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.acl-editor :refer [AclEditor]]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.attribute-editor :as attributes]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.library :as library]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.workspace-cloner :refer [WorkspaceCloner]]
    [org.broadinstitute.firecloud-ui.utils :as utils]))


(defn- render-tags [tags]
  (let [tagstyle {:marginRight 13 :borderRadius 2 :padding "5px 13px"
                  :backgroundColor (:tag-background style/colors)
                  :color (:tag-foreground style/colors)
                  :display "inline-block" :fontSize "94%"}]
    [:div {}
     (map (fn [tag] [:span {:style tagstyle} tag]) tags)]))


(react/defc DeleteDialog
  {:render
   (fn [{:keys [state this]}]
     [modal/OKCancelForm
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
                  (modal/push-error-response (get-parsed-response false))))}))


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
                                 "Exception" [icons/ExceptionIcon {:size 36}])
                         :color (style/color-for-status status)}]
     [:div {:ref "sidebar"}]
     (style/create-unselectable
       :div {:style {:position (when-not sidebar-visible? "fixed")
                     :top (when-not sidebar-visible? 0)
                     :width 270}}
       (when (and curator? writer? (not editing?))
         [library/CatalogButton {:library-schema library-schema
                                 :workspace workspace
                                 :workspace-id workspace-id
                                 :request-refresh request-refresh}])
       (when (and curator? owner? (not editing?))
         (if (:library:published library-attributes)
           [library/UnpublishButton {:workspace-id workspace-id
                                     :request-refresh request-refresh}]
           [library/PublishButton {:disabled? (when (empty? library-attributes)
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
                             (modal/push-error-text error)
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
                               :disabled? (when (empty? billing-projects)
                                            "There are no billing projects available for your account. To create a
                                            billing project, choose the 'Billing' option from the dropdown in the top right.")
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


(defn- render-main [{:keys [workspace curator? owner? writer? bucket-access? editing? submissions-count library-schema request-refresh workspace-id]}]
  (let [{:keys [owners]
         {:keys [createdBy createdDate bucketName description workspace-attributes library-attributes]} :workspace} workspace]
    [:div {:style {:flex "1 1 auto" :overflow "hidden"}}
     [:div {:style {:flex "1 1 auto" :display "flex"}}
      [:div {:style {:flex "1 1 50%"}}
       (style/create-section-header (str "Workspace Owner" (when (> (count owners) 1) "s")))
       (style/create-paragraph
         [:div {}
          (interpose ", " owners)
          (when owner?
            [:span {}
             " ("
             (style/create-link {:text "Sharing..."
                                 :onClick #(modal/push-modal
                                            [AclEditor {:workspace-id workspace-id
                                                        :request-refresh request-refresh}])})
             ")"])])
       (style/create-section-header "Created By")
       (style/create-paragraph
         [:div {} createdBy]
         [:div {} (common/format-date createdDate)])]
      [:div {:style {:flex "1 1 50%" :paddingLeft 10}}
       (style/create-section-header "Google Bucket")
       (style/create-paragraph
         (case bucket-access?
           nil [:div {:style {:position "absolute" :marginTop "-1.5em"}}
                [comps/Spinner {:height "1.5ex"}]]
           true (style/create-link {:text bucketName
                                    :href (str moncommon/google-cloud-context bucketName "/")
                                    :style {:color "-webkit-link" :textDecoration "underline"}
                                    :title "Click to open the Google Cloud Storage browser for this bucket"
                                    :target "_blank"})
           false bucketName))
       (style/create-section-header "Analysis Submissions")
       (style/create-paragraph
         (let [count-all (apply + (vals submissions-count))]
           [:div {}
            (str count-all " Submission" (when-not (= 1 count-all) "s"))
            (when (pos? count-all)
              [:ul {:style {:marginTop "0"}}
               (for [[status subs] (sort submissions-count)]
                 [:li {} (str subs " " status)])])]))]]
     (style/create-section-header "Description")
     (style/create-paragraph
       (let [description (not-empty description)]
         (cond editing? (react/create-element [MarkdownEditor {:ref "description" :initial-text description}])
               description [MarkdownView {:text description}]
               :else [:span {:style {:fontStyle "italic"}} "No description provided"])))
     (when-not (empty? library-attributes)
       [library/LibraryAttributeViewer {:library-attributes library-attributes
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
               derived {:owner? owner? :writer? writer? :request-refresh #(react/call :refresh this)}]
           [:div {:style {:margin "45px 25px" :display "flex"}}
            (render-sidebar state refs this
                            (merge (select-keys props [:workspace :workspace-id :on-clone :on-delete])
                                   (select-keys server-response [:billing-projects :curator?])
                                   derived))
            (render-main (merge (select-keys props [:workspace :workspace-id :bucket-access?])
                                (select-keys @state [:editing?])
                                (select-keys server-response [:submissions-count :library-schema :curator?])
                                (select-keys derived [:owner? :writer? :request-refresh])))
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
                       (modal/push-error-text "Could not lock workspace, one or more analyses are currently running")
                       (modal/push-error-text (str "Error: " status-text))))
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
                     (swap! state update-in [:server-response]
                            assoc :submissions-count (get-parsed-response false))
                     (swap! state update-in [:server-response]
                            assoc :server-error status-text)))})
     (endpoints/get-billing-projects
      (fn [err-text projects]
        (if err-text
          (swap! state update-in [:server-response] assoc :server-error err-text)
          (swap! state update-in [:server-response]
                 assoc :billing-projects (map #(% "projectName") projects)))))
     (endpoints/get-library-attributes
       (fn [{:keys [success? get-parsed-response]}]
         (if success?
           (let [response (utils/keywordize-keys (get-parsed-response false))]
             (swap! state update-in [:server-response] assoc :library-schema
                    (-> response
                        (assoc :required (map keyword (:required response)))
                        (assoc :propertyOrder (map keyword (:propertyOrder response))))))
           (swap! state update-in [:server-response] assoc :server-error "Unable to load library schema"))))
     (endpoints/call-ajax-orch
       {:endpoint endpoints/get-library-curator-status
        :on-done (fn [{:keys [success? get-parsed-response]}]
                   (if success?
                     (swap! state update-in [:server-response] assoc :curator? (get (get-parsed-response false) "curator"))
                     (swap! state update-in [:server-response] assoc :server-error "Unable to determine curator status")))}))})
