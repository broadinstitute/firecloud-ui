(ns org.broadinstitute.firecloud-ui.page.method-repo.redact
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    ))


(react/defc Redacter
  {:render
   (fn [{:keys [props state this]}]
     [dialog/Dialog
      {:width 550
       :dismiss-self (:dismiss-self props)
       :content
       (react/create-element
         [dialog/OKCancelForm
          {:header "Confirm redact"
           :dismiss-self (:dismiss-self props)
           :content
           [:div {}
            (when (:redacting? @state)
              [comps/Blocker {:banner "Redacting..."}])
            [:p {} (str "Are you sure you want to redact this method"
                     (when (:is-config? props) " configuration") "?")]
            [comps/ErrorViewer {:error (:error @state)
                                :expect {401 "Unauthorized"}}]]
           :ok-button [comps/Button {:text "Redact" :onClick #(react/call :redact this)}]}])}])
   :component-did-mount
   (fn []
     (common/scroll-to-top 100))
   :redact
   (fn [{:keys [props state]}]
     (swap! state assoc :redacting? true :error nil)
     (let [[namespace name snapshotId] (map #(get-in props [:entity %]) ["namespace" "name" "snapshotId"])]
       (endpoints/call-ajax-orch
         {:endpoint (endpoints/delete-agora-entity (:is-config? props) namespace name snapshotId)
          :on-done (fn [{:keys [success? get-parsed-response]}]
                     (swap! state dissoc :redacting?)
                     (if success?
                       ((:on-delete props))
                       (swap! state assoc :error (get-parsed-response))))})))})
