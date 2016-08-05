(ns org.broadinstitute.firecloud-ui.page.workspace.summary.tab
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.markdown :refer [MarkdownView]]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.nav :as nav]
    [org.broadinstitute.firecloud-ui.page.workspace.monitor.common :refer [all-success? any-running? any-failed?]]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.acl-editor :refer [AclEditor]]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.attribute-editor :as attributes]
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
                     (swap! state assoc :server-error (get-parsed-response))))}))})


(defn- render-overlays [state]
  [:div {}
   (when (:deleting-attrs? @state)
     [comps/Blocker {:banner "Deleting Attributes..."}])
   (when (:updating-attrs? @state)
     [comps/Blocker {:banner "Updating Attributes..."}])
   (when (contains? @state :locking?)
     [comps/Blocker {:banner (if (:locking? @state) "Unlocking..." "Locking...")}])])


(defn- render-sidebar [state props refs this ws billing-projects owner? writer?]
  (let [locked? (get-in ws ["workspace" "isLocked"])
        status (common/compute-status ws)]
    [:div {:style {:flex "0 0 270px" :paddingRight 30}}
     [comps/StatusLabel {:text (str status
                                 (when (= status "Running")
                                   (str " (" (get-in ws ["workspaceSubmissionStats" "runningSubmissionsCount"]) ")")))
                         :icon (case status
                                 "Complete" [icons/CompleteIcon {:size 36}]
                                 "Running" [icons/RunningIcon {:size 36}]
                                 "Exception" [icons/ExceptionIcon {:size 36}])
                         :color (style/color-for-status status)}]
     (when (or owner? writer?)
       (if (not (:editing? @state))
         [comps/SidebarButton
          {:style :light :color :button-blue :margin :top
           :text "Edit" :icon :pencil
           :onClick #(swap! state assoc
                       :reserved-keys (vec (range 0 (count (:attrs-list @state))))
                       :orig-attrs (:attrs-list @state) :editing? true)}]
         [:div {}
          [comps/SidebarButton
           {:style :light :color :button-blue :margin :top
            :text "Save" :icon :document
            :onClick #(attributes/save-attributes state props this
                        (common/get-text refs "descriptionArea"))}]
          [comps/SidebarButton
           {:style :light :color :exception-red :margin :top
            :text "Cancel Editing" :icon :x
            :onClick #(swap! state assoc
                        :editing? false
                        :attrs-list (:orig-attrs @state))}]]))
     (when-not (:editing? @state)
       [comps/SidebarButton {:style :light :margin :top :color :button-blue
                             :text "Clone..." :icon :plus
                             :disabled? (when (empty? billing-projects) "No billing projects available")
                             :onClick #(modal/push-modal
                                        [WorkspaceCloner
                                         {:on-success (fn [namespace name]
                                                        (swap! state dissoc :cloning?)
                                                        ((:on-clone props) (str namespace ":" name)))
                                          :workspace-id (:workspace-id props)
                                          :description (get-in ws ["workspace" "attributes" "description"])
                                          :is-protected? (get-in ws ["workspace" "isProtected"])
                                          :billing-projects billing-projects}])}])
     (when-not (and owner? (:editing? @state))
       [comps/SidebarButton {:style :light :margin :top :color :button-blue
                             :text (if locked? "Unlock" "Lock") :icon :locked
                             :onClick #(react/call :lock-or-unlock this locked?)}])
     (when-not (and owner? (:editing? @state))
       [comps/SidebarButton {:style :light :margin :top :color :exception-red
                             :text "Delete" :icon :trash-can
                             :disabled? (if locked? "This workspace is locked")
                             :onClick #(modal/push-modal [DeleteDialog
                                                          {:workspace-id (:workspace-id props)
                                                           :on-delete (:on-delete props)}])}])]))


(defn- render-main [state props refs ws owner? bucket-access? submissions-count]
  (let [owners (ws "owners")]
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
                                            [AclEditor {:workspace-id (:workspace-id props)
                                                        :update-owners (fn [new-owners]
                                                                         (swap! state update-in [:server-response :workspace] assoc "owners" new-owners))}])})
             ")"])])
       (style/create-section-header "Created By")
       (style/create-paragraph
         [:div {} (get-in ws ["workspace" "createdBy"])]
         [:div {} (common/format-date (get-in ws ["workspace" "createdDate"]))])]
      [:div {:style {:flex "1 1 50%" :paddingLeft 10}}
       (style/create-section-header "Google Bucket")
       (style/create-paragraph
         (case bucket-access?
           nil [:div {:style {:position "absolute" :marginTop "-1.5em"}}
                [comps/Spinner {:height "1.5ex"}]]
           true (style/create-link {:text (get-in ws ["workspace" "bucketName"])
                                    :href (str "https://console.developers.google.com/storage/browser/" (get-in ws ["workspace" "bucketName"]) "/")
                                    :title "Click to open the Google Cloud Storage browser for this bucket"
                                    :target "_blank"})
           false (get-in ws ["workspace" "bucketName"])))
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
       (let [description (not-empty (get-in ws ["workspace" "attributes" "description"]))]
         (cond (:editing? @state) (react/create-element
                                    (style/create-text-area {:ref "descriptionArea"
                                                             :defaultValue description
                                                             :style {:width "100%"}
                                                             :rows 10}))
               description [MarkdownView {:text description}]
               :else [:span {:style {:fontStyle "italic"}} "No description provided"])))
     (attributes/view-attributes state refs)]))

(react/defc Summary
  {:refresh
   (fn [{:keys [state this]}]
     (swap! state dissoc :server-response :submission-response)
     (react/call :load-workspace this))
   :render
   (fn [{:keys [refs state props this]}]
     (let [server-response (:server-response @state)
           {:keys [workspace submissions-count billing-projects server-error]} server-response]
       (cond
         server-error
         (style/create-server-error-message server-error)
         (some nil? [workspace submissions-count billing-projects])
         [:div {:style {:textAlign "center" :padding "1em"}}
          [comps/Spinner {:text "Loading workspace..."}]]
         :else
         (let [owner? (= "OWNER" (workspace "accessLevel"))
               writer? (or owner? (= "WRITER" (workspace "accessLevel")))]
           [:div {:style {:margin "45px 25px" :display "flex"}}
            (render-overlays state)
            (render-sidebar state props refs this workspace billing-projects owner? writer?)
            (render-main state props refs workspace owner? (:bucket-access? props) submissions-count)]))))
   :load-workspace
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (let [workspace (get-parsed-response)
                           attributes (get-in workspace ["workspace" "attributes"])
                           new-state (update-in @state [:server-response]
                                       assoc :workspace workspace)
                           new-state (assoc new-state :attrs-list (vec (dissoc attributes "description")))]
                       (reset! state new-state))
                     (swap! state update-in [:server-response]
                       assoc :server-error status-text)))})
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/count-submissions (:workspace-id props))
        :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                   (if success?
                     (swap! state update-in [:server-response]
                       assoc :submissions-count (get-parsed-response))
                     (swap! state update-in [:server-response]
                       assoc :server-error status-text)))})
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-billing-projects)
        :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                   (if success?
                     (swap! state update-in [:server-response]
                       assoc :billing-projects (get-parsed-response))
                     (swap! state update-in [:server-response]
                       assoc :server-error status-text)))}))
   :lock-or-unlock
   (fn [{:keys [props state this]} locked-now?]
     (swap! state assoc :locking? locked-now?)
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/lock-or-unlock-workspace (:workspace-id props) locked-now?)
        :on-done (fn [{:keys [success? status-text status-code]}]
                   (when-not success?
                     (if (and (= status-code 409) (not locked-now?))
                       (js/alert "Could not lock workspace, one or more analyses are currently running")
                       (js/alert (str "Error: " status-text))))
                   (swap! state dissoc :locking?)
                   (react/call :refresh this))}))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-workspace this))})
