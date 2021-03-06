(ns broadfcui.page.workspace.method-configs.synchronize
  (:require
   [dmohs.react :as react]
   [clojure.set :as set]
   [broadfcui.common.method.sync :as sync-common]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   [broadfcui.utils.user :as user]
   ))


(defonce ^:private sync-flag (atom false))

(defn flag-synchronization []
  (reset! sync-flag true))

(defn- check-synchronization []
  (let [val @sync-flag]
    (reset! sync-flag false)
    val))


(react/defc SyncContainer
  {:render
   (fn [{:keys [state]}]
     [:div {}
      (blocker (:banner @state))
      (when-let [error (:sync-error @state)]
        (modals/render-error {:text error :dismiss #(swap! state dissoc :sync-error)}))
      (modals/show-modals
       state
       {:show-sync-modal?
        [sync-common/SynchronizeModal (assoc (select-keys @state [:method :users]) :flavor "workspace")]
        :show-alert-modal?
        (sync-common/alert-modal (assoc (select-keys @state [:method]) :flavor "workspace"))})])
   :component-did-mount
   (fn [{:keys [props state this]}]
     (when (check-synchronization)
       (swap! state assoc :banner "Checking permissions...")
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/get-permission-report (:workspace-id props))
         :payload {:configs [(:config-id props)]}
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                    (swap! state dissoc :banner)
                    (if success?
                      (this :-perform-sync-logic (get-parsed-response))
                      (swap! state assoc :sync-error status-text)))})))
   :-perform-sync-logic
   (fn [{:keys [state]} parsed-perms-report]
     (let [method-report (first (:referencedMethods parsed-perms-report))]
       (when-not (get-in method-report [:method :public])
         (let [workspace-users (->> parsed-perms-report :workspaceACL keys (map name) set)
               me (user/get-email)
               method-owner? (-> (get-in method-report [:method :managers]) set (contains? me))
               can-share? (get-in parsed-perms-report [:workspaceACL (keyword me) :canShare])
               method-users (when method-owner? (->> method-report :acls (map :user) set))
               unauthed-users (set/difference workspace-users method-users)]
           (cond (and method-owner? can-share? (seq unauthed-users))
                 (swap! state assoc
                        :show-sync-modal? true
                        :method (:method method-report)
                        :users unauthed-users)

                 (not (and method-owner? can-share?))
                 (swap! state assoc
                        :show-alert-modal? true
                        :method (:method method-report)))))))})
