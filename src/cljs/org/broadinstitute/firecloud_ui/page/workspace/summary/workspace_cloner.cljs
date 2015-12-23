(ns org.broadinstitute.firecloud-ui.page.workspace.summary.workspace-cloner
  (:require
    [clojure.string :refer [blank?]]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.dialog :as dialog]
    [org.broadinstitute.firecloud-ui.common.input :as input]
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
     [dialog/Dialog
      {:width 400 :dismiss-self (:dismiss props)
       :content
       (react/create-element
         [dialog/OKCancelForm
          {:header "Clone Workspace to:"
           :dismiss-self (:dismiss props)
           :content
           (react/create-element
             [:div {}
              (when (:working? @state)
                [comps/Blocker {:banner "Cloning..."}])
              (style/create-form-label "Google Project")
              (style/create-select {:ref "project"
                                    :value (:selected-project @state)
                                    :onChange #(swap! state assoc :selected-project (-> % .-target .-value))}
                (:billing-projects props))
              (style/create-form-label "Name")
              [input/TextField {:ref "name"
                                :style {:width "100%"}
                                :defaultValue (get-in props [:workspace-id :name])
                                :placeholder "Required"
                                :predicates [(input/nonempty "Workspace name")]}]
              (style/create-validation-error-message (:validation-error @state))
              [comps/ErrorViewer {:error (:error @state)
                                  :expect {409 "A workspace with this name already exists in this project"}}]])
           :ok-button
           (react/create-element
             [comps/Button {:text "OK" :ref "okButton"
                            :onClick #(react/call :do-clone this)}])}])
       :get-first-element-dom-node #(@refs "project")
       :get-last-element-dom-node #(react/find-dom-node (@refs "okButton"))}])
   :component-did-mount
   (fn []
     (common/scroll-to-top 100))
   :do-clone
   (fn [{:keys [props refs state]}]
     (if-let [fails (input/validate refs "name")]
       (swap! state assoc :validation-error fails)
       (let [name (input/get-text refs "name")
             project (:selected-project @state)]
         (swap! state assoc :working? true :validation-error nil :error nil)
         (endpoints/call-ajax-orch
           {:endpoint (endpoints/clone-workspace (:workspace-id props))
            :payload {:namespace project :name name}
            :headers {"Content-Type" "application/json"}
            :on-done (fn [{:keys [success? get-parsed-response]}]
                       (swap! state dissoc :working?)
                       (if success?
                         ((:on-success props) project name)
                         (swap! state assoc :error (get-parsed-response))))}))))})
