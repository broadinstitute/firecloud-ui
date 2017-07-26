(ns broadfcui.page.workspace.summary.synchronize
  (:require
   [dmohs.react :as react]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.common.components :as comps]
   [broadfcui.components.modals :as modals]
   [broadfcui.utils :as utils]
   ))


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
      (when-let [banner (:banner @state)]
        [comps/Blocker {:banner banner}])
      (when (:show-sync-modal? @state)
        [modals/OKCancelForm
         {:header "Synchronize Access to Methods"
          :content
          [:div {} "Hi!"]
          :ok-button [comps/Button {:text "Grant Read Permission"
                                    :onClick #(this :-grant-permission)}]}])])
   :-perform-sync-logic
   (fn [{:keys [state]} parsed-perms-report]
     (utils/log parsed-perms-report)
     (let [new-users (map name (keys (:workspaceACL parsed-perms-report)))
           methods (:referencedMethods parsed-perms-report)
           private-methods (remove :public methods)
           methods-we-dont-own (filter (comp empty? :acls) methods)
           methods-we-own (filter (comp seq :acls) methods)]
       (utils/log methods-we-dont-own)))})
