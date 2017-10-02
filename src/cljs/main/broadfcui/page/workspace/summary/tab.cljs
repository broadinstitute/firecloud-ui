(ns broadfcui.page.workspace.summary.tab
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.markdown :refer [MarkdownView MarkdownEditor]]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.collapse :refer [Collapse]]
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
   [broadfcui.utils :as utils]
   ))


(react/defc- DeleteDialog
  {:render
   (fn [{:keys [props state this]}]
     [comps/OKCancelForm
      {:header "Confirm Delete"
       :content
       [:div {}
        (when (:deleting? @state)
          [comps/Blocker {:banner "Deleting..."}])
        [:p {:style {:margin 0}} "Are you sure you want to delete this workspace?"]
        [:p {} (str "Deleting it will delete the associated bucket data"
                    (when (:published? props) " and unpublish the workspace from the Data Library")
                    ".")]
        [comps/ErrorViewer {:error (:server-error @state)}]]
       :ok-button {:text "Delete" :onClick #(this :delete)
                   :data-test-id "confirm-delete-workspace-button"}}])
   :delete
   (fn [{:keys [props state]}]
     (swap! state assoc :deleting? true :server-error nil)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/delete-workspace (:workspace-id props))
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (swap! state dissoc :deleting?)
                  (if success?
                    (do (modal/pop-modal) (nav/go-to-path :workspaces))
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
   (fn [{:keys [locals]}]
     (swap! locals assoc :label-id (gensym "status") :body-id (gensym "summary")))
   :render
   (fn [{:keys [state props this refs]}]
     (let [{:keys [server-response]} @state
           {:keys [workspace workspace-id request-refresh]} props
           {:keys [server-error]} server-response]
       [:div {}
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
               [comps/Blocker {:banner "Updating Attributes..."}])
             (when (contains? @state :locking?)
               [comps/Blocker {:banner (if (:locking? @state) "Locking..." "Unlocking...")}])]))]))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :refresh))
   :component-will-receive-props
   (fn [{:keys [props next-props state this]}]
     (swap! state dissoc :updating-attrs? :editing?)
     (when-not (= (:workspace-id props) (:workspace-id next-props))
       (this :refresh)))
   :-render-sidebar
   (fn [{:keys [props state locals refs this]}
        {:keys [catalog-with-read? owner? writer? can-share?]}]
     (let [{:keys [workspace workspace-id request-refresh]} props
           {:keys [label-id body-id]} @locals
           {:keys [editing?]
            {:keys [library-schema billing-projects curator?]} :server-response} @state
           {{:keys [isLocked library-attributes description authorizationDomain]} :workspace
            {:keys [runningSubmissionsCount]} :workspaceSubmissionStats} workspace
           status (common/compute-status workspace)
           publishable? (and curator? (or catalog-with-read? owner?))]
       [:div {:style {:flex "0 0 270px" :paddingRight 30}}
        (when (:cloning? @state)
          [create/CreateDialog
           {:dismiss #(swap! state dissoc :cloning?)
            :workspace-id workspace-id
            :description description
            :auth-domain (set (map :membersGroupName authorizationDomain))
            :billing-projects billing-projects}])
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
          [:div {:style {:width 270}}
           (when-not (and library-schema billing-projects (some? curator?))
             (comps/render-blocker "Loading..."))
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
               :onClick #(modal/push-modal
                          [CatalogWizard (utils/restructure library-schema workspace workspace-id can-share?
                                                            owner? curator? writer? catalog-with-read? request-refresh)])}])
           (when (and publishable? (not editing?))
             (let [working-attributes (library-utils/get-initial-attributes workspace)
                   questions (->> (range (count (:wizard library-schema)))
                                  (map (comp first (partial library-utils/get-questions-for-page working-attributes library-schema)))
                                  (apply concat))
                   required-attributes (library-utils/find-required-attributes library-schema)]
               (if (:library:published library-attributes)
                 [publish/UnpublishButton (utils/restructure workspace-id request-refresh)]
                 [publish/PublishButton
                  (merge (utils/restructure workspace-id request-refresh)
                         {:disabled? (cond (empty? library-attributes)
                                           "Dataset attributes must be created before publishing."
                                           (seq (library-utils/validate-required
                                                 (library-utils/remove-empty-values working-attributes)
                                                 questions required-attributes))
                                           "All required dataset attributes must be set before publishing.")})])))

           (when (or owner? writer?)
             (if (not editing?)
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
                                   new-description ((@refs "description") :get-text)
                                   new-tags ((@refs "tags-autocomplete") :get-tags)]
                               (if error
                                 (comps/push-error error)
                                 (this :-save-attributes (assoc success :description new-description :tag:tags new-tags)))))}]
                [buttons/SidebarButton
                 {:style :light :color :exception-state :margin :top
                  :text "Cancel Editing" :icon :cancel
                  :onClick #(swap! state dissoc :editing?)}]]))
           (when-not editing?
             [buttons/SidebarButton
              {:data-test-id "open-clone-workspace-modal-button"
               :style :light :margin :top :color :button-primary
               :text "Clone..." :icon :clone
               :disabled? (when (empty? billing-projects) (comps/no-billing-projects-message))
               :onClick #(swap! state assoc :cloning? true)}])
           (when (and owner? (not editing?))
             [buttons/SidebarButton
              {:style :light :margin :top :color :button-primary
               :text (if isLocked "Unlock" "Lock")
               :icon (if isLocked :unlock :lock)
               :onClick #(this :-lock-or-unlock isLocked)}])
           (when (and owner? (not editing?))
             (let [published? (:library:published library-attributes)
                   publisher? (and curator? (or catalog-with-read? owner?))]
               [buttons/SidebarButton
                {:data-test-id "delete-workspace-button"
                 :style :light :margin :top :color (if isLocked :text-lighter :exception-state)
                 :text "Delete" :icon :delete
                 :disabled? (cond isLocked
                                  "This workspace is locked."
                                  (and published? (not publisher?))
                                  {:type :error :header "Alert" :icon-color :warning-state
                                   :text [:div {}
                                          [:p {:style {:margin 0}}
                                           "This workspace is published in the Data Library and cannot be deleted. "
                                           "Contact a library curator to ask them to first unpublish the workspace."]
                                          [:p {}
                                           "If you are unable to contact a curator, contact help@firecloud.org."]]})
                 :onClick #(modal/push-modal
                            [DeleteDialog (utils/restructure workspace-id published?)])}]))]}]]))
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
             nil [:div {:style {:position "absolute" :marginTop "-1.5em"}}
                  [comps/Spinner {:height "1.5ex"}]]
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
                 :else [:div {}
                        (for [tag processed-tags]
                          [:div {:style {:display "inline-block" :background (:tag-background style/colors)
                                         :color (:tag-foreground style/colors) :margin "0.1rem 0.1rem"
                                         :borderRadius 3 :padding "0.2rem 0.5rem"}} tag])])]}]

        (when editing? [:div {:style {:marginBottom "10px"}} common/PHI-warning])

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
            [comps/Spinner {:text "Loading Dataset Attributes"
                            :style {:marginBottom "2rem"}}]
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
                    (do
                      (swap! state dissoc :updating-attrs?)
                      (comps/push-error-response (get-parsed-response false)))))}))
   :-lock-or-unlock
   (fn [{:keys [props state]} locked-now?]
     (swap! state assoc :locking? (not locked-now?))
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/lock-or-unlock-workspace (:workspace-id props) locked-now?)
       :on-done (fn [{:keys [success? status-text status-code]}]
                  (when-not success?
                    (if (and (= status-code 409) (not locked-now?))
                      (comps/push-error
                       "Could not lock workspace, one or more analyses are currently running")
                      (comps/push-error (str "Error: " status-text))))
                  (swap! state dissoc :locking?)
                  ((:request-refresh props)))}))
   :refresh
   (fn [{:keys [state refs]}]
     (swap! state dissoc :server-response)
     (when-let [component (@refs "storage-estimate")] (component :refresh))
     ((@refs "submission-count") :refresh)
     (endpoints/get-billing-projects
      (fn [err-text projects]
        (if err-text
          (swap! state update :server-response assoc :server-error err-text)
          (swap! state update :server-response
                 assoc :billing-projects (map :projectName projects)))))
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
