(ns broadfcui.page.workspace.summary.synchronize
  (:require
   [dmohs.react :as react]
   [clojure.set :as set]
   [clojure.string :as string]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.common.components :as comps]
   [broadfcui.components.modals :as modals]
   [broadfcui.utils :as utils]
   ))


(defn- get-method-display [{:keys [namespace name snapshotId]}]
  (str namespace "/" name " snapshot " snapshotId))


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
             "In order to allow the users you have added to this workspace to run the methods configured
              for it, you must grant them access to the following:"
             [:ul {} (map (fn [method] [:li {} (get-method-display (:method method))]) owned-methods)]])
          (when unowned-methods
            [:div {}
             (if owned-methods
               "Additionally, users will need to request access to the following methods:"
               "In order to run the methods configured for this workspace, users will need to request
                access to the following:")
             [:ul {} (map (fn [method]
                            [:li {}
                             [:div {} (get-method-display method)]
                             [:div {} (str "Owner"
                                           (when (> (count (:managers method)) 1) "s")
                                           ": " (string/join ", " (:managers method)))]])
                          unowned-methods)]])
          [comps/ErrorViewer {:error (:grant-error @state)}]]
         :ok-button [comps/Button
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
   (fn [{:keys [state this]}]
     [:div {}
      (when (:show-sync-modal? @state)
        [SyncModal (merge (select-keys @state [:owned-methods :unowned-methods])
                          {:dismiss #(swap! state dissoc :show-sync-modal?)})])])
   :-perform-sync-logic
   (fn [{:keys [state]} parsed-perms-report]
     (let [new-users (->> (:workspaceACL parsed-perms-report) keys (map name) set)
           methods (:referencedMethods parsed-perms-report)
           private-methods (remove (comp :public :method) methods)
           private-unowned (filter (comp empty? :acls) private-methods)
           private-owned (filter (comp seq :acls) private-methods)
           access-needed-by-method (map (fn [method]
                                          (assoc (select-keys method [:method])
                                            :new-users (set/difference new-users
                                                                       (->> (:acls method) (map :user) set))))
                                        private-owned)]
       (when (or (seq access-needed-by-method) (seq private-unowned))
         (swap! state assoc
                :show-sync-modal? true
                :unowned-methods (not-empty (map :method private-unowned))
                :owned-methods (not-empty access-needed-by-method)))))})
