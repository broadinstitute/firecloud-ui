(ns org.broadinstitute.firecloud-ui.page.workspace.summary.summary-tab
  (:require
    [clojure.string :refer [trim capitalize blank?]]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
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


(defn- view-summary [state props ws status owner? this on-view-attributes]
  (let [locked? (get-in ws ["workspace" "isLocked"])]
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
      [comps/StatusLabel {:text (capitalize (name status))
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
      (style/create-section-header "Workspace Owner")
      (style/create-paragraph
        [:div {}
         (interpose ", " (map #(style/render-email %) (ws "owners")))
         (when owner?
           [:span {}
            " ("
            (style/create-link
              #(swap! state assoc :editing-acl? true)
              "Sharing...")
            ")"])])
      (style/create-section-header "Google Bucket")
      (style/create-paragraph (get-in ws ["workspace" "bucketName"]))]
     (common/clear-both)]))

(react/defc Summary
  {:get-initial-state
   (fn []
     {:viewing-attributes? false})
   :render
   (fn [{:keys [state props this]}]
     (cond
       (nil? (:server-response @state))
       [comps/Spinner {:text "Loading workspace..."}]
       (get-in @state [:server-response :error-message])
       (style/create-server-error-message (get-in @state [:server-response :error-message]))
       :else
       (let [ws (get-in @state [:server-response :workspace])
             owner? (= "OWNER" (ws "accessLevel"))
             writer? (or (= "WRITER" (ws "accessLevel")) owner?)
             status (common/compute-status ws)]
         (if (:viewing-attributes? @state)
           (let [ws-response ((get-in @state [:server-response :workspace]) "workspace")]
             [AttributeViewer {:ws ws :writer? writer? :on-done #(swap! state dissoc :viewing-attributes?)
                               :attrs-list (mapv (fn [[k v]] [k v]) (ws-response "attributes"))
                               :workspace-id (:workspace-id props)}])
           (view-summary state props ws status owner? this #(swap! state assoc :viewing-attributes? true))))))
   :load-workspace
   (fn [{:keys [props state]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace (:workspace-id props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (swap! state assoc :server-response
                     (if success? {:workspace (get-parsed-response)}
                                  {:error-message status-text})))}))
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
                   (swap! state #(if success? (dissoc % :locking? :server-response) (dissoc % :locking?))))}))
   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-workspace this))
   :component-did-update
   (fn [{:keys [this state]}]
     (when (nil? (:server-response @state))
       (react/call :load-workspace this)))
   :component-will-receive-props
   (fn [{:keys [props next-props state]}]
     (utils/cljslog props next-props)
     (when-not (apply = (map :workspace-id [props next-props]))
       (swap! state assoc :server-response nil)))})


(defn render [workspace-id on-delete]
  (react/create-element Summary {:workspace-id workspace-id :on-delete on-delete}))
