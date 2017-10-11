(ns broadfcui.page.workspace.summary.synchronize
  (:require
   [dmohs.react :as react]
   [clojure.set :as set]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.common.components :as comps]
   [broadfcui.common.method.messages :as messages]
   [broadfcui.common.method.sync :as sync-common]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.modals :as modals]
   [broadfcui.utils :as utils]
   ))


(react/defc- SyncModal
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [owned-methods unowned-methods dismiss]} props]
       [modals/OKCancelForm
        {:header (if owned-methods "Synchronize Access to Methods" "Unable to Grant Method Access")
         :dismiss dismiss
         :show-cancel? owned-methods
         :content
         [:div {:style {:maxWidth 670}}
          (when (:granting? @state)
            [comps/Blocker {:banner "Granting permission..."}])
          (when owned-methods
            [:div {}
             messages/methods-repo-group-alert
             "In order to allow the users you have added to this workspace to run the methods configured
              for it, you must grant them access to the following:"
             [:ul {} (map (fn [method] [:li {} (sync-common/get-method-display (:method method))]) owned-methods)]])
          (when unowned-methods
            [:div {}
             (if owned-methods
               "Additionally, users will need to request access to the following methods:"
               "In order to run the methods configured for this workspace, users will need to request
                access to the following:")
             [:table {:style {:marginTop "1rem"}}
              [:thead {}
               [:tr {:style {:fontWeight "bold"}}
                [:td {} "Method"]
                [:td {:style {:paddingLeft "1rem"}} "Owners"]]]
              [:tbody {}
               (map (fn [method]
                      [:tr {}
                       [:td {:style {:verticalAlign "top"}}
                        (sync-common/get-method-display method)]
                       [:td {:style {:verticalAlign "top" :paddingLeft "1rem"}}
                        (map (fn [owner] [:div {} owner])
                             (:managers method))]])
                    unowned-methods)]]])
          [comps/ErrorViewer {:error (:grant-error @state)}]]
         :ok-button [buttons/Button
                     (if owned-methods
                       {:text "Grant Read Permission"
                        :onClick #(this :-grant-permission)}
                       {:text "OK"
                        :onClick dismiss})]}]))
   :-grant-permission
   (fn [{:keys [props state]}]
     (let [{:keys [owned-methods dismiss]} props]
       (swap! state assoc :grant-error nil :granting? true)
       (endpoints/call-ajax-orch
        {:endpoint endpoints/multi-grant-method-acl
         :payload (mapv (fn [{:keys [method new-users]}]
                          {:method {:methodNamespace (:namespace method)
                                    :methodName (:name method)
                                    :methodVersion (:snapshotId method)}
                           :acls (mapv (fn [user] {:user user :role "READER"})
                                       new-users)})
                        owned-methods)
         :headers utils/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :granting?)
                    (if success?
                      (dismiss)
                      (swap! state assoc :grant-error (get-parsed-response false))))})))})


(react/defc SyncContainer
  {:check-synchronization
   (fn [{:keys [props this]} new-users]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-permission-report (:workspace-id props))
       :payload {:users new-users}
       :headers utils/content-type=json
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (this :-perform-sync-logic (get-parsed-response))
                    (comps/push-error-response (get-parsed-response false))))}))
   :render
   (fn [{:keys [state]}]
     [:div {}
      (when (:show-sync-modal? @state)
        [SyncModal (merge (select-keys @state [:owned-methods :unowned-methods])
                          {:dismiss #(swap! state dissoc :show-sync-modal?)})])])
   :-perform-sync-logic
   (fn [{:keys [state]} parsed-perms-report]
     (let [workspace-users (->> (:workspaceACL parsed-perms-report) keys (map name) set)
           methods (:referencedMethods parsed-perms-report)
           private-methods (remove (comp :public :method) methods)
           by-ownership (group-by (comp empty? :acls) private-methods)
           private-owned (by-ownership false)
           private-unowned (by-ownership true)
           access-needed-by-method (->> private-owned
                                        (map (fn [method]
                                               (assoc (select-keys method [:method])
                                                 :new-users (not-empty (set/difference
                                                                        workspace-users
                                                                        (->> (:acls method) (map :user) set))))))
                                        (filter (comp some? :new-users))
                                        not-empty)]
       (when (or (seq access-needed-by-method) (seq private-unowned))
         (swap! state assoc
                :show-sync-modal? true
                :unowned-methods (not-empty (distinct (map :method private-unowned)))
                :owned-methods access-needed-by-method))))})
