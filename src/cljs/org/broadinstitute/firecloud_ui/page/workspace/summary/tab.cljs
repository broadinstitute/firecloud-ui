(ns org.broadinstitute.firecloud-ui.page.workspace.summary.tab
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.nav :as nav]
    [org.broadinstitute.firecloud-ui.page.workspace.monitor.common :refer [all-success?]]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.acl-editor :refer [AclEditor]]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.attribute-editor :as attributes]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.workspace-cloner :refer [WorkspaceCloner]]
    ))


(defn- render-tags [tags]
  (let [tagstyle {:marginRight 13 :borderRadius 2 :padding "5px 13px"
                  :backgroundColor (:tag-background style/colors)
                  :color (:tag-foreground style/colors)
                  :display "inline-block" :fontSize "94%"}]
    [:div {}
     (map (fn [tag] [:span {:style tagstyle} tag]) tags)]))


(react/defc DeleteDialog
  {:render
   (fn [{:keys [state props this]}]
     [dialog/Dialog
      {:width 500 :dismiss-self (:dismiss-self props)
       :content
       (react/create-element
         [:div {}
          (when (:deleting? @state)
            [comps/Blocker {:banner "Deleting..."}])
          [dialog/OKCancelForm
           {:dismiss-self (:dismiss-self props) :header "Confirm Delete"
            :content
            [:div {}
             [:p {:style {:margin 0}} "Are you sure you want to delete this workspace?"]
             [:p {} "Bucket data will be deleted too."]
             [comps/ErrorViewer {:error (:server-error @state)}]]
            :ok-button [comps/Button {:text "Delete" :onClick #(react/call :delete this)}]}]])}])
   :component-did-mount
   (fn []
     (common/scroll-to-top 100))
   :delete
   (fn [{:keys [props state]}]
     (swap! state assoc :deleting? true :server-error nil)
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/delete-workspace (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response]}]
                   (swap! state dissoc :deleting?)
                   (if success?
                     ((:on-delete props))
                     (swap! state assoc :server-error (get-parsed-response))))}))})


(defn- render-overlays [state props]
  [:div {}
   (when (:show-delete-dialog? @state)
     [DeleteDialog
      {:dismiss-self #(swap! state dissoc :show-delete-dialog?)
       :workspace-id (:workspace-id props)
       :on-delete (:on-delete props)}])
   (when (:deleting-attrs? @state)
     [comps/Blocker {:banner "Deleting Attributes..."}])
   (when (:updating-attrs? @state)
     [comps/Blocker {:banner "Updating Attributes..."}])
   (when (contains? @state :locking?)
     [comps/Blocker {:banner (if (:locking? @state) "Unlocking..." "Locking...")}])
   (when (:editing-acl? @state)
     [AclEditor {:workspace-id (:workspace-id props)
                 :dismiss-self #(swap! state dissoc :editing-acl?)
                 :update-owners #(swap! state update-in [:server-response :workspace] assoc "owners" %)}])
   (when (:cloning? @state)
     [WorkspaceCloner {:dismiss #(swap! state dissoc :cloning?)
                       :on-success (fn [namespace name]
                                     (swap! state dissoc :cloning?)
                                     (nav/navigate (:nav-context props) (str namespace ":" name)))
                       :workspace-id (:workspace-id props)}])])


(defn- render-sidebar [state props refs this ws owner? writer?]
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
                             :onClick #(swap! state assoc :cloning? true)}])
     (when-not (and owner? (:editing? @state))
       [comps/SidebarButton {:style :light :margin :top :color :button-blue
                             :text (if locked? "Unlock" "Lock") :icon :locked
                             :onClick #(react/call :lock-or-unlock this locked?)}])
     (when-not (and owner? (:editing? @state))
       [comps/SidebarButton {:style :light :margin :top :color :exception-red
                             :text "Delete" :icon :trash-can
                             :disabled? (if locked? "This workspace is locked")
                             :onClick #(swap! state assoc :show-delete-dialog? true)}])]))


(defn- render-main [state refs ws owner? submissions]
  (let [owners (ws "owners")]
    [:div {:style {:flex "1 1 auto" :display "flex"}}
     [:div {:style {:flex "1 1 50%"}}
      (style/create-section-header (str "Workspace Owner" (when (> (count owners) 1) "s")))
      (style/create-paragraph
        [:div {}
         (interpose ", " owners)
         (when owner?
           [:span {}
            " ("
            (style/create-link
              #(swap! state assoc :editing-acl? true)
              "Sharing...")
            ")"])])
      (style/create-section-header "Description")
      (style/create-paragraph
        (let [description (get-in ws ["workspace" "attributes" "description"])]
          (cond (:editing? @state) (react/create-element
                                     (style/create-text-area {:ref "descriptionArea"
                                                              :defaultValue description
                                                              :style {:width 400}
                                                              :rows 5}))
                description description
                :else [:span {:style {:fontStyle "oblique"}} "No description provided"])))
      (attributes/view-attributes state refs)]
     [:div {:style {:flex "1 1 50%" :paddingLeft 10}}
      (style/create-section-header "Created By")
      (style/create-paragraph
        [:div {} (get-in ws ["workspace" "createdBy"])]
        [:div {} (common/format-date (get-in ws ["workspace" "createdDate"]))])
      (style/create-section-header "Google Bucket")
      (style/create-paragraph (get-in ws ["workspace" "bucketName"]))
      (style/create-section-header "Analysis Submissions")
      (style/create-paragraph
        (let [fail-count (->> submissions
                           (filter (complement all-success?))
                           count)]
          (str (count submissions) " Submissions"
            (when (pos? fail-count)
              (str " (" fail-count " failed)")))))]]))


(react/defc Summary
  {:get-initial-state
   (fn []
     {:viewing-attributes? false
      :load-counter 0})
   :render
   (fn [{:keys [refs state props this]}]
     (cond
       (and (:server-response @state) (:submission-response @state))
       (let [{:keys [workspace workspace-error]} (:server-response @state)
             {:keys [submissions submissions-error]} (:submission-response @state)]
         (cond workspace-error (style/create-server-error-message workspace-error)
               submissions-error (style/create-server-error-message submissions-error)
               :else
               (let [owner? (= "OWNER" (workspace "accessLevel"))
                     writer? (or (= "WRITER" (workspace "accessLevel")) owner?)]
                 [:div {:style {:margin "45px 25px" :display "flex"}}
                  (render-overlays state props)
                  (render-sidebar state props refs this workspace owner? writer?)
                  (render-main state refs workspace owner? submissions)])))
       :else [:div {:style {:textAlign "center" :padding "1em"}}
              [comps/Spinner {:text "Loading workspace..."}]]))
   :load-workspace
   (fn [{:keys [props state]}]
     (when (zero? (:load-counter @state))
       (swap! state assoc :load-counter 2)
       (endpoints/call-ajax-orch
         {:endpoint (endpoints/get-workspace (:workspace-id props))
          :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                     (swap! state assoc :server-response
                       (if success?
                         {:workspace (get-parsed-response)}
                         {:workspace-error status-text}))
                     (if success?
                       (let [response (:server-response @state)
                             attributes (get-in response
                                          [:workspace "workspace" "attributes" ])
                             attrs-list (mapv (fn [[k v]] [k v])
                                          (dissoc attributes "description"))]
                         (swap! state assoc :attrs-list attrs-list))
                       (swap! state dissoc :attrs-list))
                     (swap! state update-in [:load-counter] dec))})
       (endpoints/call-ajax-orch
         {:endpoint (endpoints/list-submissions (:workspace-id props))
          :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                     (swap! state assoc :submission-response
                       (if success?
                         {:submissions (get-parsed-response)}
                         {:submissions-error status-text})
                       (swap! state update-in [:load-counter] dec)))})))
   :delete
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/delete-workspace (:workspace-id props))
        :on-done (fn [{:keys [success? status-text]}]
                   (swap! state dissoc :deleting?)
                   (if success?
                     ((:on-delete props))
                     (js/alert (str "Error on delete: " status-text))))}))
   :lock-or-unlock
   (fn [{:keys [props state]} locked-now?]
     (swap! state assoc :locking? locked-now?)
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/lock-or-unlock-workspace (:workspace-id props) locked-now?)
        :on-done (fn [{:keys [success? status-text status-code]}]
                   (when-not success?
                     (if (and (= status-code 409) (not locked-now?))
                       (js/alert "Could not lock workspace, one or more analyses are currently running")
                       (js/alert (str "Error: " status-text))))
                   (swap! state #(if success?
                                  (dissoc % :locking? :server-response :submission-response)
                                  (dissoc % :locking?))))}))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-workspace this))
   :component-did-update
   (fn [{:keys [this state]}]
     (when (nil? (:server-response @state))
       (react/call :load-workspace this)))
   :component-will-receive-props
   (fn [{:keys [props next-props state this]}]
     (swap! state dissoc :server-response :submission-response)
     (react/call :load-workspace this))})


(defn render [workspace-id on-delete nav-context]
  (react/create-element Summary {:key workspace-id
                                 :nav-context nav-context
                                 :workspace-id workspace-id
                                 :on-delete on-delete}))
