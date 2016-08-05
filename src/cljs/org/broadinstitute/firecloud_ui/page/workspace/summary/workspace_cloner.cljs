(ns org.broadinstitute.firecloud-ui.page.workspace.summary.workspace-cloner
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.input :as input]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

(react/defc WorkspaceCloner
  {:get-initial-state
   (fn [{:keys [props]}]
     {:selected-project (first (:billing-projects props))})
   :render
   (fn [{:keys [props refs state this]}]
     [modal/OKCancelForm
      {:header "Clone Workspace to:"
       :ok-button {:text "Clone" :onClick #(react/call :do-clone this)}
       :get-first-element-dom-node #(@refs "project")
       :content
       (react/create-element
         [:div {}
          (when (:working? @state)
            [comps/Blocker {:banner "Cloning..."}])
          (style/create-form-label "Billing Project")
          (style/create-select {:ref "project"
                                :value (:selected-project @state)
                                :onChange #(swap! state assoc :selected-project (-> % .-target .-value))}
                               (:billing-projects props))
          (style/create-form-label "Name")
          [input/TextField {:ref "name"
                            :style {:width "100%"}
                            :defaultValue (get-in props [:workspace-id :name])
                            :placeholder "Required"
                            :predicates [(input/nonempty "Workspace name")
                                         (input/alphanumeric_- "Workspace name")]}]
          (style/create-textfield-hint "Only letters, numbers, underscores, and dashes allowed")
          (style/create-form-label "Description (optional)")
          (style/create-text-area {:style {:width "100%"} :rows 5 :ref "wsDescription"
                                   :defaultValue (:description props)})
          (if (:is-protected? props)
            [:div {} "Cloned workspace will automatically be protected because this workspace is protected."]
            [comps/Checkbox
             {:ref "protected-check"
              :label "Workspace intended to contain NIH protected data"
              :disabled? (not= (:protected-option @state) :enabled)
              :disabled-text (case (:protected-option @state)
                               :not-loaded "Account status has not finished loading."
                               :not-available "This option is not available for your account."
                               nil)}])
          (style/create-validation-error-message (:validation-error @state))
          [comps/ErrorViewer {:error (:error @state)
                              :expect {409 "A workspace with this name already exists in this project"}}]])}])
   :component-did-mount
   (fn [{:keys [state]}]
     (utils/ajax-orch
       "/nih/status"
       {:on-done (fn [{:keys [success? get-parsed-response]}]
                     (if (and success? (get (get-parsed-response) "isDbgapAuthorized"))
                       (swap! state assoc :protected-option :enabled)
                       (swap! state assoc :protected-option :not-available)))}))
   :do-clone
   (fn [{:keys [props refs state]}]
     (if-let [fails (input/validate refs "name")]
       (swap! state assoc :validation-error fails)
       (let [name (input/get-text refs "name")
             project (nth (:billing-projects props) (int (:selected-project @state)))
             desc (common/get-text refs "wsDescription")
             attributes (if (or (:description props) (not (clojure.string/blank? desc)))
                          {:description desc}
                          {})
             protected? (or (:is-protected? props) (react/call :checked? (@refs "protected-check")))]
         (swap! state assoc :working? true :validation-error nil :error nil)
         (endpoints/call-ajax-orch
           {:endpoint (endpoints/clone-workspace (:workspace-id props))
            :payload {:namespace project :name name :attributes attributes :isProtected protected?}
            :headers {"Content-Type" "application/json"}
            :on-done (fn [{:keys [success? get-parsed-response]}]
                       (swap! state dissoc :working?)
                       (if success?
                         ((:on-success props) project name)
                         (swap! state assoc :error (get-parsed-response))))}))))})
