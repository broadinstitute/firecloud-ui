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
      {:endpoint (endpoints/get-agora-entity-acl false (get-in props [:config :method]))
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (swap! state dissoc :loading?)
                  (if success?
                    (this :-check-users-and-show-sync new-users (get-parsed-response))
                    (swap! state assoc :show-alert-modal? true)))}))
   :render
   (fn [{:keys [props state]}]
     [:div {}
      (when (:loading? @state)
        [comps/Blocker {:banner "Checking method access..."}])
      (modals/show-modals
       state
       {:show-sync-modal?
        [sync-common/SynchronizeModal (merge {:method (get-in props [:config :method])}
                                             (select-keys @state [:users]))]
        :show-alert-modal?
        (sync-common/alert-modal {:method (get-in props [:config :method])})})])
   :-check-users-and-show-sync
   (fn [{:keys [state]} new-users parsed-acl]
     (let [existing-users (set (map :user parsed-acl))
           needing-access (set/difference new-users existing-users)]
       (when (seq needing-access)
         (swap! state assoc :users needing-access :show-sync-modal? true))))})
