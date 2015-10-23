(ns org.broadinstitute.firecloud-ui.page.workspace.summary.tab
  (:require
    [clojure.string :refer [trim capitalize blank?]]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.workspace.monitor.common :refer [all-success?]]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.acl-editor :refer [AclEditor]]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.attribute-editor :refer [AttributeViewer]]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.workspace-cloner :refer [WorkspaceCloner]]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- render-tags [tags]
  (let [tagstyle {:marginRight 13 :borderRadius 2 :padding "5px 13px"
                  :backgroundColor (:tag-background style/colors)
                  :color (:tag-foreground style/colors)
                  :display "inline-block" :fontSize "94%"}]
    [:div {}
     (map (fn [tag] [:span {:style tagstyle} tag]) tags)]))


(defn- view-summary [state props ws submissions status owner? this on-view-attributes]
  (let [locked? (get-in ws ["workspace" "isLocked"])
        owners (ws "owners")]
    [:div {:style {:margin "45px 25px"}}
     (when (:deleting? @state)
       [comps/Blocker {:banner "Deleting..."}])
     (when (contains? @state :locking?)
       [comps/Blocker {:banner (if (:locking? @state) "Unlocking..." "Locking...")}])
     (when (:editing-acl? @state)
       [AclEditor {:workspace-id (:workspace-id props)
                   :dismiss-self #(swap! state dissoc :editing-acl?)
                   :update-owners #(swap! state update-in [:server-response :workspace] assoc "owners" %)}])
     (when (:cloning? @state)
       [WorkspaceCloner {:dismiss #(swap! state dissoc :cloning?)
                         :workspace-id (:workspace-id props)}])
     [:div {:style {:float "left" :width 290 :marginRight 40}}
      ;; TODO - make the width of the float-left dynamic
      [comps/StatusLabel {:text (str status
                                  (when (= status "Running")
                                    (str " (" (get-in ws ["workspaceSubmissionStats" "runningSubmissionsCount"]) ")")))
                          :icon (case status
                                  "Complete" [comps/CompleteIcon {:size 36}]
                                  "Running" [comps/RunningIcon {:size 36}]
                                  "Exception" [comps/ExceptionIcon {:size 36}])
                          :color (style/color-for-status status)}]
      [comps/SidebarButton {:style :light :margin :top :color :button-blue
                            :text "View attributes" :icon :document
                            :onClick on-view-attributes}]
      [comps/SidebarButton {:style :light :margin :top :color :button-blue
                            :text "Clone..." :icon :plus
                            :onClick #(swap! state assoc :cloning? true)}]
      (when owner?
        [comps/SidebarButton {:style :light :margin :top :color :button-blue
                              :text (if locked? "Unlock" "Lock") :icon :locked
                              :onClick #(react/call :lock-or-unlock this locked?)}])
      (when owner?
        [comps/SidebarButton {:style :light :margin :top :color :exception-red
                              :text "Delete" :icon :trash-can
                              :disabled? (if locked? "This workspace is locked")
                              :onClick #(when (js/confirm "Are you sure?")
                                         (swap! state assoc :deleting? true)
                                         (react/call :delete this))}])]
     [:div {:style {:marginLeft 330}}
      (style/create-section-header (str "Workspace Owner" (when (> (count owners) 1) "s")))
      (style/create-paragraph
        [:div {}
         (interpose ", " (ws "owners"))
         (when owner?
           [:span {}
            " ("
            (style/create-link
              #(swap! state assoc :editing-acl? true)
              "Sharing...")
            ")"])])
      (style/create-section-header "Description")
      (style/create-paragraph
        (or (get-in ws ["workspace" "attributes" "description"])
          [:span {:style {:fontStyle "oblique"}} "No description provided"]))
      (style/create-section-header "Google Bucket")
      (style/create-paragraph (get-in ws ["workspace" "bucketName"]))
      (style/create-section-header "Created By")
      (style/create-paragraph
        [:div {} (get-in ws ["workspace" "createdBy"])]
        [:div {} (common/format-date (get-in ws ["workspace" "createdDate"]))])
      (style/create-section-header "Submissions")
      (style/create-paragraph
        (let [fail-count (->> submissions
                           (filter (complement all-success?))
                           count)]
          (str (count submissions) " Submissions"
            (when (pos? fail-count)
              (str " (" fail-count " failed)")))))]
     (common/clear-both)]))

(react/defc Summary
  {:get-initial-state
   (fn []
     {:viewing-attributes? false})
   :render
   (fn [{:keys [state props this]}]
     (cond
       (and (:server-response @state) (:submission-response @state))
       (let [{:keys [workspace workspace-error]} (:server-response @state)
             {:keys [submissions submissions-error]} (:submission-response @state)]
         (cond workspace-error (style/create-server-error-message workspace-error)
               submissions-error (style/create-server-error-message submissions-error)
               :else
               (let [owner? (= "OWNER" (workspace "accessLevel"))
                     writer? (or (= "WRITER" (workspace "accessLevel")) owner?)
                     status (common/compute-status workspace)]
                 (if (:viewing-attributes? @state)
                   [AttributeViewer {:ws workspace :writer? writer? :on-done #(swap! state dissoc :viewing-attributes?)
                                     :attrs-list (mapv (fn [[k v]] [k v])
                                                   (dissoc (get-in workspace ["workspace" "attributes"]) "description"))
                                     :workspace-id (:workspace-id props)}]
                   (view-summary state props workspace submissions status owner? this
                     #(swap! state assoc :viewing-attributes? true))))))
       :else [comps/Spinner {:text "Loading workspace..."}]))
   :load-workspace
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (swap! state assoc :server-response
                     (if success? {:workspace (get-parsed-response)}
                                  {:workspace-error status-text})))})
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/list-submissions (:workspace-id props))
        :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                   (swap! state assoc :submission-response
                     (if success?
                       {:submissions (get-parsed-response)}
                       {:submissions-error status-text})))}))
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
   (fn [{:keys [props next-props state]}]
     (when-not (apply = (map :workspace-id [props next-props]))
       (swap! state dissoc :server-response :submission-response)))})


(defn render [workspace-id on-delete]
  (react/create-element Summary {:workspace-id workspace-id :on-delete on-delete}))
