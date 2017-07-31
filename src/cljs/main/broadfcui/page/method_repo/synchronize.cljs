(ns broadfcui.page.method-repo.synchronize
  (:require
   [dmohs.react :as react]
   [clojure.set :as set]
   [broadfcui.common.components :as comps]
   [broadfcui.common.sync :as sync-common]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))


(react/defc SyncContainer
  {:check-synchronization
   (fn [{:keys [props state this]} new-users]
     (swap! state assoc :loading? true)
     (endpoints/call-ajax-orch
      {:endpoint (let [{:keys [namespace name snapshotId]} (get-in props [:config :method])]
                   (endpoints/get-agora-method namespace name snapshotId))
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (swap! state dissoc :loading?)
                  (let [method (get-parsed-response)
                        managers (set (:managers method))]
                    (if (contains? managers (utils/get-user-email))
                      (this :-check-users-and-show-sync new-users method)
                      (swap! state assoc :show-alert-modal? true :method method))))}))
   :render
   (fn [{:keys [props state]}]
     [:div {}
      (when (:loading? @state)
        [comps/Blocker {:banner "Checking method access..."}])
      (modals/show-modals
       state
       {:show-sync-modal? [sync-common/SynchronizeModal (select-keys @state [:method :users])]
        :show-alert-modal? (sync-common/alert-modal (select-keys @state [:method]))})])
   :-check-users-and-show-sync
   (fn [{:keys [state]} new-users method]
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-agora-entity-acl false method)
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (let [existing-users (set (map :user (get-parsed-response)))
                        needing-access (set/difference new-users existing-users)]
                    (when (seq needing-access)
                      (swap! state assoc
                             :users needing-access
                             :method method
                             :show-sync-modal? true))))}))})
