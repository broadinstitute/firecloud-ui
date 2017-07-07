(ns broadfcui.page.workspace.summary.tab
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.markdown :refer [MarkdownView MarkdownEditor]]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.components.collapse :refer [Collapse]]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.nav :as nav]
    [broadfcui.page.workspace.monitor.common :as moncommon]
    [broadfcui.page.workspace.summary.acl-editor :refer [AclEditor]]
    [broadfcui.page.workspace.summary.attribute-editor :as attributes]
    [broadfcui.page.workspace.summary.catalog.wizard :refer [CatalogWizard]]
    [broadfcui.page.workspace.summary.library-utils :as library-utils]
    [broadfcui.page.workspace.summary.library-view :refer [LibraryView]]
    [broadfcui.page.workspace.summary.publish :as publish]
    [broadfcui.page.workspace.summary.workspace-cloner :refer [WorkspaceCloner]]
    [broadfcui.utils :as utils]
    ))


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
       :ok-button {:text "Delete" :onClick #(react/call :delete this)
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
                       {:keys [workspace billing-projects owner? writer? curator? catalog-with-read? can-share?
                               workspace-id request-refresh user-access-level]}]
  (let [{{:keys [isLocked library-attributes description authorizationDomain]} :workspace
         {:keys [runningSubmissionsCount]} :workspaceSubmissionStats} workspace
        status (common/compute-status workspace)
        publishable? (and curator? (or catalog-with-read? owner?))
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
      (when (and can-share? (not editing?))
        [comps/SidebarButton
         {:style :light :margin :top :color :button-primary
          :data-test-id "share-workspace-button"
          :text "Share..." :icon :share
          :onClick #(modal/push-modal
                     [AclEditor {:workspace-id workspace-id
                                 :user-access-level user-access-level
                                 :request-refresh request-refresh}])}])
      (when (not editing?)
        [comps/SidebarButton
         {:style :light :color :button-primary :margin :top
          :icon :catalog :text "Catalog Dataset..."
          :data-test-id "catalog-button"
          :onClick #(modal/push-modal [CatalogWizard (utils/restructure
                                                      library-schema
                                                      workspace
                                                      workspace-id
                                                      can-share?
                                                      owner?
                                                      curator?
                                                      writer?
                                                      catalog-with-read?
                                                      request-refresh)])}])
      (when (and publishable? (not editing?))
        (let [working-attributes (library-utils/get-initial-attributes workspace)
              questions (->> (range (count (:wizard library-schema)))
                             (map (comp first (partial library-utils/get-questions-for-page working-attributes library-schema)))
                             (apply concat))
              required-attributes (library-utils/find-required-attributes library-schema)]
          (if (:library:published library-attributes)
            [publish/UnpublishButton {:workspace-id workspace-id
                                      :request-refresh request-refresh}]
            [publish/PublishButton {:disabled? (cond
                                                 (empty? library-attributes)
                                                 "Dataset attributes must be created before publishing."
                                                 (seq (library-utils/validate-required
                                                       (library-utils/remove-empty-values working-attributes)
                                                       questions required-attributes))
                                                 "All required dataset attributes must be set before publishing.")
                                    :workspace-id workspace-id
                                    :request-refresh request-refresh}])))
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
                              new-description (react/call :get-text (@refs "description"))
                              new-tags (react/call :get-tags (@refs "tags-autocomplete"))]
                          (if error
                            (comps/push-error error)
                            (save-attributes {:new-attributes (assoc success :description new-description :tag:tags new-tags)
                                              :state state
                                              :workspace-id workspace-id
                                              :request-refresh request-refresh}))))}]
           [comps/SidebarButton
            {:style :light :color :exception-state :margin :top
             :text "Cancel Editing" :icon :cancel
             :onClick #(swap! state dissoc :editing?)}]]))
      (when-not editing?
        [comps/SidebarButton
         {:style :light :margin :top :color :button-primary
          :text "Clone..." :icon :clone :data-test-id "open-clone-workspace-modal-button"
          :disabled? (when (empty? billing-projects) (comps/no-billing-projects-message))
          :onClick #(modal/push-modal
                     [WorkspaceCloner
                      {:on-success (fn [namespace name]
                                     (swap! state dissoc :cloning?)
                                     (nav/go-to-path :workspace-summary
                                                     (utils/restructure namespace name)))
                       :workspace-id workspace-id
                       :description description
                       :auth-domain (:membersGroupName authorizationDomain)
                       :billing-projects billing-projects}])}])
      (when (and owner? (not editing?))
        [comps/SidebarButton {:style :light :margin :top :color :button-primary
                              :text (if isLocked "Unlock" "Lock")
                              :icon (if isLocked :unlock :lock)
                              :onClick #(react/call :lock-or-unlock this isLocked)}])
      (when (and owner? (not editing?))
        [comps/SidebarButton {:style :light :margin :top :color (if isLocked :text-lighter :exception-state)
                              :text "Delete" :icon :delete
                              :data-test-id "delete-workspace-button"
                              :disabled? (when isLocked "This workspace is locked.")
                              :onClick #(modal/push-modal
                                         [DeleteDialog {:workspace-id workspace-id}])}]))]))


(defn- render-main [{:keys [workspace curator? owner? writer? reader? can-share? catalog-with-read? bucket-access? editing? submissions-count
                            library-schema request-refresh workspace-id storage-cost user-access-level]}]
  (let [{:keys [owners]
         {:keys [createdBy createdDate bucketName description tags workspace-attributes library-attributes]} :workspace} workspace
        render-detail-box (fn [title & children]
                            [:div
                             {:style {:flexBasis "50%" :paddingRight "2rem" :marginBottom "2rem"}}
                             [:div {:style {:paddingBottom "0.5rem"}}
                              (style/create-section-header title)]
                             (map-indexed
                              (fn [i child]
                                (if (even? i)
                                  [:div {:style {:fontWeight 500 :paddingTop "0.5rem"}} child]
                                  [:div {:style {:fontSize "90%" :lineHeight 1.5}} child]))
                              children)])
        processed-tags (flatten (map :items (vals tags)))]
    [:div {:style {:flex "1 1 auto" :overflow "hidden"}}
     [:div {:style {:display "flex"}}
      (render-detail-box
       "Workspace Access"

       "Access Level"
       (style/prettify-access-level user-access-level)

       (str "Workspace Owner" (when (> (count owners) 1) "s"))
       (interpose ", " owners)

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
          true (style/create-link {:text bucketName
                                   :href (str moncommon/google-cloud-context bucketName "/")
                                   :style {:color "-webkit-link" :textDecoration "underline"}
                                   :title "Click to open the Google Cloud Storage browser for this bucket"
                                   :target "_blank"})
          false bucketName)
        (when (not reader?)
          [:div {:style {:lineHeight "initial"}}
           [:div {} (str "Total Estimated Storage Fee per month = " storage-cost)]
           [:div {:style {:fontSize "80%"}} (str "Note: the billing account associated with " (:namespace workspace-id) " will be charged.")]])]

       "Analysis Submissions"
       (let [count-all (apply + (vals submissions-count))]
         [:div {}
          (str count-all " Submission" (when-not (= 1 count-all) "s"))
          (when (pos? count-all)
            [:ul {:style {:marginTop 0}}
             (for [[status subs] (sort submissions-count)]
               [:li {} (str subs " " status)])])]))]
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
     (when-not (empty? library-attributes)
       [LibraryView (utils/restructure
                     library-attributes
                     library-schema
                     workspace
                     workspace-id
                     request-refresh
                     can-share?
                     owner?
                     curator?
                     writer?
                     catalog-with-read?)])
     [attributes/WorkspaceAttributeViewerEditor
      (merge {:ref "workspace-attribute-editor" :workspace-bucket bucketName}
             (utils/restructure editing? writer? workspace-attributes workspace-id request-refresh))]]))

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
               catalog-with-read? (and (or writer? (reader? workspace)) (:catalog workspace))
               user-access-level (:accessLevel workspace)
               derived (merge {:reader? (reader? (:workspace props)) :request-refresh #(react/call :refresh this)}
                              (utils/restructure owner? writer? can-share? catalog-with-read? user-access-level))]
           [:div {:style {:margin "2.5rem 1.5rem" :display "flex"}}
            (render-sidebar state refs this
                            (merge (select-keys props [:workspace :workspace-id])
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
                      (comps/push-error
                       "Could not lock workspace, one or more analyses are currently running")
                      (comps/push-error (str "Error: " status-text))))
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
