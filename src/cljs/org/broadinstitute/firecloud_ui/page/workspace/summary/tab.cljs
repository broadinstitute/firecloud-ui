(ns org.broadinstitute.firecloud-ui.page.workspace.summary.tab
  (:require
    [clojure.string :refer [trim capitalize blank?]]
    [dmohs.react :as react]
    [clojure.set :as set-ops]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.nav :as nav]
    [org.broadinstitute.firecloud-ui.page.workspace.monitor.common :refer [all-success?]]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.acl-editor :refer [AclEditor]]
    [org.broadinstitute.firecloud-ui.page.workspace.summary.attribute-editor :as attributes]
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


(defn- view-summary [state props ws submissions status owner? writer?
                     this on-view-attributes nav-context refs]
  (let [locked? (get-in ws ["workspace" "isLocked"])
        owners (ws "owners")
        editing? (:editing? @state)]
    [:div {:style {:margin "45px 25px"}}
     (when (:deleting-attrs? @state)
       [comps/Blocker {:banner "Deleting Attributes..."}])
     (when (:updating-attrs? @state)
       [comps/Blocker {:banner "Updating Attributes..."}])
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
                         :on-success (fn [namespace name]
                                       (swap! state dissoc :cloning?)
                                       (nav/navigate nav-context (str namespace ":" name)))
                         :workspace-id (:workspace-id props)}])
     [:div {:style {:float "left" :width 290 :marginRight 40}}
      ;; TODO - make the width of the float-left dynamic
      [comps/StatusLabel {:text (str status
                                  (when (= status "Running")
                                    (str " (" (get-in ws ["workspaceSubmissionStats" "runningSubmissionsCount"]) ")")))
                          :icon (case status
                                  "Complete" [icons/CompleteIcon {:size 36}]
                                  "Running" [icons/RunningIcon {:size 36}]
                                  "Exception" [icons/ExceptionIcon {:size 36}])
                          :color (style/color-for-status status)}]
      (when-not editing?
        [comps/SidebarButton {:style :light :margin :top :color :button-blue
                            :text "Clone..." :icon :plus
                            :onClick #(swap! state assoc :cloning? true)}])
      (when-not (and owner? editing?)
        [comps/SidebarButton {:style :light :margin :top :color :button-blue
                              :text (if locked? "Unlock" "Lock") :icon :locked
                              :onClick #(react/call :lock-or-unlock this locked?)}])
      (when-not (and owner? editing?)
        [comps/SidebarButton {:style :light :margin :top :color :exception-red
                              :text "Delete" :icon :trash-can
                              :disabled? (if locked? "This workspace is locked")
                              :onClick #(when (js/confirm
                                                "Are you sure?\nBucket data will also be deleted.")
                                         (swap! state assoc :deleting? true)
                                         (react/call :delete this))}])
      (when (or owner? writer?)
        (if (not editing?)
          [comps/SidebarButton
           {:style :light :color :button-blue :margin :top
            :text "Edit attributes" :icon :pencil
            :onClick #(swap! state assoc
                       :reserved-keys (vec (range 0 (count (:attrs-list @state))))
                       :orig-attrs (:attrs-list @state) :editing? true)}]
          [:div {}
           [comps/SidebarButton
            {:style :light :color :button-blue :margin :top
             :text "Save Attributes" :icon :document
             :onClick #(let
                        [orig-keys (mapv first (:orig-attrs @state))
                         curr-keys (mapv first (:attrs-list @state))
                         curr-vals (mapv second (:attrs-list @state))
                         valid-keys? (every? pos? (map count curr-keys))
                         valid-vals? (every? pos? (map count curr-vals))
                         to-delete (vec (set-ops/difference
                                          (set orig-keys)
                                          (set curr-keys)))
                         workspace-id (:workspace-id props)
                         make-delete-map-fn (fn [k]
                                              {:op "RemoveAttribute"
                                               :attributeName k})
                         make-update-map-fn (fn [p]
                                              {:op "AddUpdateAttribute"
                                               :attributeName (first p)
                                               :addUpdateAttribute (second p)})
                         del-mapv (mapv make-delete-map-fn to-delete)
                         up-mapv (mapv make-update-map-fn (:attrs-list @state))
                         update-orch-fn (fn [add-update-ops]
                                          (swap! state assoc :updating-attrs? true)
                                          (endpoints/call-ajax-orch
                                            {:endpoint (endpoints/update-workspace-attrs
                                                         workspace-id)
                                             :payload add-update-ops
                                             :headers {"Content-Type" "application/json"}
                                             :on-done (fn [{:keys [success? xhr]}]
                                                        (swap! state dissoc :updating-attrs?)
                                                        (if-not success?
                                                          (do
                                                            (js/alert (str "Exception:\n"
                                                                        (.-statusText xhr)))
                                                            (swap! state dissoc :orig-attrs)
                                                            (react/call :load-workspace this))))}))
                         del-orch-fn (fn [del-ops]
                                       (swap! state assoc :deleting-attrs? true)
                                       (endpoints/call-ajax-orch
                                         {:endpoint (endpoints/update-workspace-attrs
                                                      workspace-id)
                                          :payload del-ops
                                          :headers {"Content-Type" "application/json"}
                                          :on-done (fn [{:keys [success? xhr]}]
                                                     (swap! state dissoc :deleting-attrs?)
                                                     (if-not success?
                                                       (do
                                                         (js/alert (str "Exception:\n"
                                                                     (.-statusText xhr)))
                                                         (swap! state assoc
                                                           :attrs-list (:orig-attrs @state))
                                                         (swap! state dissoc :orig-attrs)
                                                         (react/call :load-workspace this))
                                                       (when-not (empty? up-mapv)
                                                         (update-orch-fn  up-mapv))))}))
                         uniq-keys? (== (count curr-keys) (count (distinct curr-keys)))]
                        (cond
                          (not valid-keys?) (js/alert "Empty attribute keys are not allowed!")
                          (not valid-vals?) (js/alert "Empty attribute values are not allowed!")
                          (not uniq-keys?) (js/alert "Unique keys must be used!")
                          :else (do
                                  (if (empty? to-delete)
                                    (when-not (empty? up-mapv)
                                      (update-orch-fn  up-mapv))
                                    (del-orch-fn del-mapv))
                                  (swap! state assoc :editing? false))))}]
           [comps/SidebarButton
            {:style :light :color :exception-red :margin :top
             :text "Cancel Attribute Editing" :icon :x
             :onClick #(swap! state assoc
                        :editing? false
                        :attrs-list (:orig-attrs @state))}]]))]
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
              (str " (" fail-count " failed)")))))
      (attributes/view-attributes state refs)]
     (common/clear-both)]))

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
                     writer? (or (= "WRITER" (workspace "accessLevel")) owner?)
                     status (common/compute-status workspace)]
                 (view-summary state props workspace submissions status owner? writer?
                     this #(swap! state assoc :viewing-attributes? true) (:nav-context props) refs))))
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
                             attrs-list (mapv (fn [[k v]] [k v]) attributes)]
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
  (react/create-element Summary {:nav-context nav-context
                                 :workspace-id workspace-id
                                 :on-delete on-delete}))
