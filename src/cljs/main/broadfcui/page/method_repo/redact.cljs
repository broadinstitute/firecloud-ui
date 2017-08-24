(ns broadfcui.page.method-repo.redact
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   ))


(react/defc- Redactor
  {:render
   (fn [{:keys [props state this]}]
     [modals/OKCancelForm
      {:header "Confirm redaction"
       :dismiss (:dismiss props)
       :content
       [:div {:style {:width 500}}
        (when (:redacting? @state)
          [comps/Blocker {:banner "Redacting..."}])
        [:div {:style {:marginBottom "1em"}}
         (str "Are you sure you want to redact this " (if (:config? props) "configuration" "method") "?")]
        [comps/ErrorViewer {:error (:error @state)
                            :expect {401 "Unauthorized"}}]]
       :ok-button {:text "Redact" :onClick #(this :-redact)}}])
   :-redact
   (fn [{:keys [props state]}]
     (swap! state assoc :redacting? true :error nil)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/delete-agora-entity (:config? props) (:entity props))
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (swap! state dissoc :redacting?)
                  (if success?
                    (do ((:dismiss props)) ((:on-delete props)))
                    (swap! state assoc :error (get-parsed-response false))))}))})
