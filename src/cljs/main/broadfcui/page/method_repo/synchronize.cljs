(ns broadfcui.page.method-repo.synchronize
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))


(react/defc SyncModal
  {:render
   (fn [{:keys [props]}]
     (let [{:keys [dismiss method owner? new-users]} props]
       [modals/OKCancelForm
        {:header (if owner? "Synchronize Access" "Unable to Grant Method Access")
         :dismiss dismiss
         :content
         [:div {} "hi!"]}]))})


(react/defc SyncContainer
  {:check-synchronization
   (fn [{:keys [props state locals]} new-users]
     (swap! state assoc :loading? true)
     (endpoints/call-ajax-orch
      {:endpoint (let [{:keys [namespace name snapshotId]} (get-in props [:config :method])]
                   (endpoints/get-agora-method namespace name snapshotId))
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (swap! locals assoc :owner? success? :new-users new-users)
                  (swap! state assoc :show-sync-modal? true :loading? false))}))
   :render
   (fn [{:keys [props state locals]}]
     [:div {}
      (when (:loading? @state)
        [comps/Blocker {:banner "Checking method access..."}])
      (when (:show-sync-modal? @state)
        [SyncModal (merge {:dismiss #(swap! state dissoc :show-sync-modal?)
                           :method (get-in props [:config :method])}
                          (select-keys @locals [:owner? :new-users]))])])})
