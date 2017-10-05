(ns broadfcui.page.method-repo.redactor
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   ))


(react/defc- Redactor
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [config? dismiss]} props]
       [modals/OKCancelForm
        {:header "Confirm Redaction"
         :dismiss dismiss
         :content
         [:div {:style {:width 500}}
          (when (:redacting? @state)
            [comps/Blocker {:banner "Redacting..."}])
          [:p {} "Are you sure you want to redact this " (if config? "configuration" "method snapshot") "?"]
          (when-not config? [:div {}
                             [:small {} "Redacting this snapshot will remove all configurations that point to it from the Method Repository."
                              [:br] "Configurations in workspaces will not be affected."]])
          [comps/ErrorViewer {:error (:error @state)
                              :expect {401 "Unauthorized"}}]]
         :ok-button {:text "Redact" :onClick #(this :-redact)}}]))
   :-redact
   (fn [{:keys [props state]}]
     (let [{:keys [config? entity dismiss on-delete]} props]
       (swap! state assoc :redacting? true :error nil)
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/delete-agora-entity config? entity)
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :redacting?)
                    (if success?
                      (do (dismiss) (on-delete))
                      (swap! state assoc :error (get-parsed-response false))))})))})
