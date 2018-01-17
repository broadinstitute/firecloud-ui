(ns broadfcui.page.workspace.summary.tab
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.markdown :refer [MarkdownView MarkdownEditor]]
   [broadfcui.common.style :as style]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.collapse :refer [Collapse]]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.components.sticky :refer [Sticky]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.page.workspace.create :as create]
   [broadfcui.page.workspace.monitor.common :as moncommon]
   [broadfcui.page.workspace.summary.acl-editor :refer [AclEditor]]
   [broadfcui.page.workspace.summary.attribute-editor :as attributes]
   [broadfcui.page.workspace.summary.catalog.wizard :refer [CatalogWizard]]
   [broadfcui.page.workspace.summary.library-utils :as library-utils]
   [broadfcui.page.workspace.summary.library-view :refer [LibraryView]]
   [broadfcui.page.workspace.summary.publish :as publish]
   [broadfcui.page.workspace.summary.synchronize :as ws-sync]
   [broadfcui.page.workspace.workspace-common :as ws-common]
   [broadfcui.user-info :as user-info]
   [broadfcui.utils :as utils]
   ))


(react/defc- DeleteDialog
  {:render
   (fn [{:keys [props state this]}]
     [modals/OKCancelForm
      {:header "Confirm Delete"
       :content
       [:div {}
        (when (:deleting? @state)
          (blocker "Deleting..."))
        [:p {:style {:margin 0}} "Are you sure you want to delete this workspace?"]
        [:p {} (str "Deleting it will delete the associated bucket data"
                    (when (:published? props) " and unpublish the workspace from the Data Library")
                    ".")]
        [comps/ErrorViewer {:error (:server-error @state)}]]
       :dismiss (:dismiss props)
       :ok-button {:text "Delete" :onClick #(this :delete)}}])
   :delete
   (fn [{:keys [props state]}]
     (utils/multi-swap! state (assoc :deleting? true) (dissoc :server-error))
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/delete-workspace (:workspace-id props))
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (swap! state dissoc :deleting?)
                  (if success?
                    (nav/go-to-path :workspaces)
                    (swap! state assoc :server-error (get-parsed-response false))))}))})

(react/defc- StorageCostEstimate
  {:render
   (fn [{:keys [state props]}]
     (let [{:keys [workspace-id]} props]
       [:div {:style {:lineHeight "initial"}}
        [:div {} "Estimated Monthly Storage Fee: " (or (:response @state) "Loading...")]
        [:div {:style {:fontSize "80%"}} (str "Note: the billing account associated with " (:namespace workspace-id) " will be charged.")]]))
   :refresh
   (fn [{:keys [state props]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/storage-cost-estimate (:workspace-id props))
       :on-done (fn [{:keys [success? status-text raw-response]}]
                  (let [[response parse-error?] (utils/parse-json-string raw-response false false)]
                    (swap! state assoc :response
                           (if parse-error?
                             (str "Error parsing JSON response with status: " status-text)
                             (let [key (if success? "estimate" "message")]
                               (get response key (str "Error: \"" key "\" not found in JSON response with status: " status-text)))))))}))})

(react/defc- SubmissionCounter
  {:render
   (fn [{:keys [state]}]
     (let [{:keys [server-error submissions-count]} @state]
       (cond
         server-error
         [:div {} server-error]
         submissions-count
         (let [count-all (apply + (vals submissions-count))]
           [:div {}
            (str count-all " Submission" (when-not (= 1 count-all) "s"))
            (when (pos? count-all)
              [:ul {:style {:marginTop 0}}
               (for [[status subs] (sort submissions-count)]
                 [:li {} (str subs " " status)])])])
         :else
         [:div {} "Loading..."])))
   :refresh
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/count-submissions (:workspace-id props))
       :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                  (if success?
                    (swap! state assoc :submissions-count (get-parsed-response false))
                    (swap! state assoc :server-error status-text)))}))})


(react/defc Summary
  {:component-will-mount
   (fn [{:keys [this locals]}]
     (swap! locals assoc :label-id (gensym "status") :body-id (gensym "summary"))
     (add-watch user-info/saved-ready-billing-project-names :ws-summary #(.forceUpdate this)))
   :render
   (fn [{:keys [state props this refs]}]
     (let [{:keys [server-response popup-error]} @state
           {:keys [workspace workspace-id request-refresh]} props
           {:keys [server-error]} server-response]
       [:div {:data-test-id "summary-tab"
              :data-test-state (cond (or (:updating-attrs? @state) (contains? @state :locking?))
                                     "updating"
                                     ;; TODO: "loading" state
                                     :else "ready")}
        (when popup-error
          (modals/render-error {:text popup-error :dismiss #(swap! state dissoc :popup-error)}))
        (when-let [error-response (:error-response @state)]
          (modals/render-error-response {:error-response error-response
                                         :dismiss #(swap! state dissoc :error-response)}))
        [ws-sync/SyncContainer {:ref "sync-container" :workspace-id workspace-id}]
        (if server-error
          (style/create-server-error-message server-error)
          (let [user-access-level (:accessLevel workspace)
                auth-domain (get-in workspace [:workspace :authorizationDomain])
                derived (merge {:can-share? (:canShare workspace)
                                :can-compute? (:canCompute workspace)
                                :owner? (common/access-greater-than-equal-to? user-access-level "OWNER")
                                :writer? (common/access-greater-than-equal-to? user-access-level "WRITER")
                                :catalog-with-read? (and (common/access-greater-than-equal-to? user-access-level "READER") (:catalog workspace))}
                               (utils/restructure user-access-level auth-domain))]
            [:div {:style {:margin "2.5rem 1.5rem" :display "flex"}}
             (when (:sharing? @state)
               [AclEditor
                (merge (utils/restructure user-access-level request-refresh workspace-id)
                       {:dismiss #(swap! state dissoc :sharing?)
                        :on-users-added (fn [new-users]
                                          ((@refs "sync-container") :check-synchronization new-users))})])
             (this :-render-sidebar derived)
             (this :-render-main derived)
             (when (:updating-attrs? @state)
               (blocker "Updating Attributes..."))
             (when (contains? @state :locking?)
               (blocker (if (:locking? @state) "Locking..." "Unlocking...")))]))]))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :refresh))
   :component-will-receive-props
   (fn [{:keys [props next-props state this]}]
     (swap! state dissoc :updating-attrs? :editing?)
     (when-not (= (:workspace-id props) (:workspace-id next-props))
       (this :refresh)))
   :component-will-unmount
   (fn []
     (remove-watch user-info/saved-ready-billing-project-names :ws-summary))
   :-render-sidebar
   (fn [{:keys [props state locals refs this]}
        {:keys [catalog-with-read? owner? writer? can-share?]}]
     (let [{:keys [workspace workspace-id request-refresh]} props
           {:keys [label-id body-id]} @locals
           {:keys [editing? billing-loaded?]
            {:keys [library-schema curator? billing-error?]} :server-response} @state
           {{:keys [isLocked library-attributes description authorizationDomain]} :workspace
            {:keys [runningSubmissionsCount]} :workspaceSubmissionStats} workspace
           billing-projects @user-info/saved-ready-billing-project-names
           status (common/compute-status workspace)
           published? (:library:published library-attributes)
           publisher? (and curator? (or catalog-with-read? owner?))
           publishable? (and curator? (or catalog-with-read? owner?))
           show-publish-message (fn [p] (swap! state assoc :showing-publish-message? true :publish-message p))]
       [:div {:style {:flex "0 0 270px" :paddingRight 30}}
        (when (:cloning? @state)
          [create/CreateDialog
           {:dismiss #(swap! state dissoc :cloning?)
            :workspace-id workspace-id
            :description description
            :auth-domain (set (map :membersGroupName authorizationDomain))}])
        (when (:deleting? @state)
          [DeleteDialog (assoc (utils/restructure workspace-id published?)
                          :dismiss #(swap! state dissoc :deleting?))])
        (when (:showing-publish-message? @state)
          (modals/render-message (assoc (:publish-message @state)
                                   :dismiss #(swap! state dissoc :showing-publish-message? :publish-message))))
        [:span {:id label-id}
         [comps/StatusLabel {:text (str status
                                        (when (= status "Running")
                                          (str " (" runningSubmissionsCount ")")))
                             :icon (case status
                                     "Complete" [icons/CompleteIcon {:size 36}]
                                     "Running" [icons/RunningIcon {:size 36}]
                                     "Exception" [icons/ExceptionIcon {:size 32}])
                             :color (style/color-for-status status)}]]
        [Sticky
         {:sticky-props {:data-check-every 1
                         :data-top-anchor (str label-id ":bottom")
                         :data-btm-anchor (str body-id ":bottom")}
          :contents
          (let [ready? (and library-schema billing-projects (some? curator?))]
            [:div {:data-test-id "sidebar"
                   :data-test-state (if ready? "ready" "loading")
                   :style {:width 270}}
             (when-not ready?
               (blocker "Loading..."))
             (when (:showing-catalog-wizard? @state)
               [CatalogWizard
                (assoc (utils/restructure library-schema workspace workspace-id can-share?
                                          owner? curator? writer? catalog-with-read? request-refresh)
                  :dismiss #(swap! state dissoc :showing-catalog-wizard?))])
             (when (and can-share? (not editing?))
               [buttons/SidebarButton
                {:data-test-id "share-workspace-button"
                 :style :light :margin :top :color :button-primary
                 :text "Share..." :icon :share
                 :onClick #(swap! state assoc :sharing? true)}])
             (when (not editing?)
               [buttons/SidebarButton
                {:data-test-id "catalog-button"
                 :style :light :color :button-primary :margin :top
                 :icon :catalog :text "Catalog Dataset..."
                 :onClick #(swap! state assoc :showing-catalog-wizard? true)}])
             (when (and publishable? (not editing?))
               (let [working-attributes (library-utils/get-initial-attributes workspace)
                     questions (->> (range (count (:wizard library-schema)))
                                    (map (comp first (partial library-utils/get-questions-for-page working-attributes library-schema)))
                                    (apply concat))
                     required-attributes (library-utils/find-required-attributes library-schema)]
                 (if (:library:published library-attributes)
                   [publish/UnpublishButton (utils/restructure workspace-id request-refresh show-publish-message)]
                   [publish/PublishButton
                    (merge (utils/restructure workspace-id request-refresh show-publish-message)
                           {:disabled? (cond (empty? library-attributes)
                                             "Dataset attributes must be created before publishing."
                                             (seq (library-utils/validate-required
                                                   (library-utils/remove-empty-values working-attributes)
                                                   questions required-attributes))
                                             "All required dataset attributes must be set before publishing.")})])))

           (when (or owner? writer?)
             (if-not editing?
               [buttons/SidebarButton
                {:style :light :color :button-primary :margin :top
                 :text "Edit" :icon :edit
                 :onClick #(swap! state assoc :editing? true)}]
               [:div {}
                [buttons/SidebarButton
                 {:style :light :color :button-primary :margin :top
                  :text "Save" :icon :done
                  :onClick (fn [_]
                             (let [{:keys [success error]} ((@refs "workspace-attribute-editor") :get-attributes)
                                   new-description ((@refs "description") :get-trimmed-text)
                                   new-tags ((@refs "tags-autocomplete") :get-tags)]
                               (if error
                                 (swap! state assoc :popup-error error)
                                 (this :-save-attributes (assoc success :description new-description :tag:tags new-tags)))))}]
                [buttons/SidebarButton
                 {:style :light :color :state-exception :margin :top
                  :text "Cancel Editing" :icon :cancel
                  :onClick #(swap! state dissoc :editing?)}]]))
           (when-not editing?
             [buttons/SidebarButton
              {:data-test-id "open-clone-workspace-modal-button"
               :style :light :margin :top :color :button-primary
               :text (if (or billing-loaded? billing-error?)
                         "Clone..."
                         (spinner {:style {:margin 0}} "Getting billing info..."))
                 :icon :clone
               :disabled? (cond
                              (not billing-loaded?) "Project billing data has not yet been loaded."
                              billing-error? "Unable to load billing projects from the server."
                              (empty? billing-projects) (comps/no-billing-projects-message))
               :onClick #(swap! state assoc :cloning? true)}])
           (when (and owner? (not editing?))
             [buttons/SidebarButton
              {:style :light :margin :top :color :button-primary
               :text (if isLocked "Unlock" "Lock")
               :icon (if isLocked :unlock :lock)
               :onClick #(this :-lock-or-unlock isLocked)}])
           (when (and owner? (not editing?))
               [buttons/SidebarButton
                {:data-test-id "delete-workspace-button"
                 :style :light :margin :top :color (if isLocked :text-lighter :state-exception)
                 :text "Delete" :icon :delete
                 :disabled? (cond isLocked
                                  "This workspace is locked."
                                  (and published? (not publisher?))
                                  {:type :error :header "Alert" :icon-color :state-warning
                                   :text [:div {}
                                          [:p {:style {:margin 0}}
                                           "This workspace is published in the Data Library and cannot be deleted. "
                                           "Contact a library curator to ask them to first unpublish the workspace."]
                                          [:p {}
                                           "If you are unable to contact a curator, contact help@firecloud.org."]]})
                 :onClick #(swap! state assoc :deleting? true)}])])}]]))
   :-render-main
   (fn [{:keys [props state locals]}
        {:keys [user-access-level auth-domain can-share? owner? curator? writer? catalog-with-read?]}]
     (let [{:keys [workspace workspace-id bucket-access? request-refresh]} props
           {:keys [editing? server-response]} @state
           {:keys [library-schema]} server-response
           {:keys [body-id]} @locals
           {:keys [owners]
            {:keys [createdBy createdDate bucketName description tags workspace-attributes library-attributes]} :workspace} workspace
           render-detail-box (fn [title & children]
                               [:div {:style {:flexBasis "50%" :paddingRight "2rem" :marginBottom "2rem"}}
                                [:div {:style {:paddingBottom "0.5rem"}}
                                 (style/create-section-header title)]
                                (map-indexed
                                 (fn [i child]
                                   (if (even? i)
                                     (style/create-subsection-label child)
                                     (style/create-subsection-contents child)))
                                 children)])
           processed-tags (flatten (map :items (vals tags)))]
       [:div {:style {:flex "1 1 auto" :overflow "hidden"} :id body-id}
        [:div {:style {:display "flex" :paddingLeft icons/fw-icon-width}}
         (render-detail-box
          "Workspace Access"

          "Access Level"
          [:span {:data-test-id "workspace-access-level"}
           (style/prettify-access-level user-access-level)]

          (str "Workspace Owner" (when (> (count owners) 1) "s"))
          (string/join ", " owners)

          "Authorization Domain"
          (if-not (empty? auth-domain)
            [:span {:data-test-id "auth-domain-groups"}
             (string/join ", " (map :membersGroupName auth-domain))]
            "None")

          "Created By"
          [:div {}
           [:div {} createdBy]
           [:div {} (common/format-date createdDate)]])

         (render-detail-box
          "Storage & Analysis"

          "Google Bucket"
          [:div {}
           (case bucket-access?
             nil [:div {:style {:position "absolute" :marginTop "-1.5em"}} (spinner)]
             true (links/create-external {:href (str moncommon/google-cloud-context bucketName "/")
                                          :title "Click to open the Google Cloud Storage browser for this bucket"}
                                         bucketName)
             false bucketName)
           (when writer?
             [StorageCostEstimate {:workspace-id workspace-id :ref "storage-estimate"}])]

          "Analysis Submissions"
          [SubmissionCounter {:workspace-id workspace-id :ref "submission-count"}])]
        [Collapse
         {:style {:marginBottom "2rem"}
          :title (style/create-section-header "Tags")
          :contents
          [:div {:style {:marginTop "1rem" :fontSize "90%" :lineHeight 1.5}}
           (cond editing? (react/create-element [comps/TagAutocomplete
                                                 {:tags processed-tags :ref "tags-autocomplete"}])
                 (empty? processed-tags) [:em {} "No tags provided"]
                 :else [:div {} (map style/render-tag processed-tags)])]}]

        (when editing? [:div {:style {:marginBottom "10px"}} ws-common/PHI-warning])

        [Collapse
         {:style {:marginBottom "2rem"}
          :title (style/create-section-header "Description")
          :contents
          [:div {:style {:marginTop "1rem" :fontSize "90%" :lineHeight 1.5}}
           (let [description (not-empty description)]
             (cond editing? (react/create-element [MarkdownEditor
                                                   {:ref "description" :initial-text description}])
                   description [MarkdownView {:text description}]
                   :else [:span {:style {:fontStyle "italic"}} "No description provided"]))]}]
        (when (seq library-attributes)
          (if-not library-schema
            (spinner {:style {:marginBottom "2rem"}} "Loading Dataset Attributes...")
            [LibraryView (utils/restructure library-attributes library-schema workspace workspace-id
                                            request-refresh can-share? owner? curator? writer? catalog-with-read?)]))
        [attributes/WorkspaceAttributeViewerEditor
         (merge {:ref "workspace-attribute-editor" :workspace-bucket bucketName}
                (utils/restructure editing? writer? workspace-attributes workspace-id request-refresh))]]))
   :-save-attributes
   (fn [{:keys [props state]} new-attributes]
     (swap! state assoc :updating-attrs? true)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/set-workspace-attributes (:workspace-id props))
       :payload new-attributes
       :headers utils/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    ((:request-refresh props))
                    (swap! state assoc :updating-attrs? nil :error-response (get-parsed-response false))))}))
   :-lock-or-unlock
   (fn [{:keys [props state]} locked-now?]
     (swap! state assoc :locking? (not locked-now?))
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/lock-or-unlock-workspace (:workspace-id props) locked-now?)
       :on-done (fn [{:keys [success? status-text status-code]}]
                  (when-not success?
                    (if (and (= status-code 409) (not locked-now?))
                      (swap! state assoc :popup-error "Could not lock workspace, one or more analyses are currently running")
                      (swap! state assoc :popup-error (str "Error: " status-text))))
                  (swap! state dissoc :locking?)
                  ((:request-refresh props)))}))
   :refresh
   (fn [{:keys [state refs]}]
     (swap! state dissoc :server-response)
     (when-let [component (@refs "storage-estimate")] (component :refresh))
     ((@refs "submission-count") :refresh)
     (user-info/reload-billing-projects
      (fn [err-text]
        (if err-text
          (swap! state update :server-response assoc :server-error "Unable to load billing projects" :billing-error? true)
          (swap! state assoc :billing-loaded? true))))
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
                    (swap! state update :server-response assoc :server-error "Unable to determine curator status")))}))})
