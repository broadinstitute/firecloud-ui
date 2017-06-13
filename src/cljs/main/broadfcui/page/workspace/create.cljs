(ns broadfcui.page.workspace.create
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.input :as input]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.components.modals :as modals]
   [broadfcui.config :as config]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.nav :as nav]
    [broadfcui.utils :as utils]
    ))


(react/defc CreateDialog
  {:get-initial-state
   (fn [{:keys [props]}]
     {:selected-project (first (:billing-projects props))
      :protected-option :not-loaded})
   :render
   (fn [{:keys [props state refs this]}]
     [modals/OKCancelForm
      {:header "Create New Workspace"
       :ok-button {:text "Create Workspace" :onClick #(react/call :create-workspace this)
                   :data-test-id "create-workspace-button"}
       :dismiss (:dismiss props)
       :get-first-element-dom-node #(@refs "project")
       :content
       (react/create-element
        [:div {:style {:marginBottom -20}}
         (when (:creating-wf @state)
           [comps/Blocker {:banner "Creating Workspace..."}])
         (style/create-form-label "Billing Project")
         (style/create-select
          {:ref "project" :value (:selected-project @state)
           :data-test-id "billing-project-select"
           :onChange #(swap! state assoc :selected-project (-> % .-target .-value))}
          (:billing-projects props))
         (style/create-form-label "Name")
         [input/TextField {:ref "wsName" :autoFocus true :style {:width "100%"}
                           :data-test-id "workspace-name-input"
                           :predicates [(input/nonempty "Workspace name")
                                        (input/alphanumeric_- "Workspace name")]}]
         (style/create-textfield-hint "Only letters, numbers, underscores, and dashes allowed")
         (style/create-form-label "Description (optional)")
         (style/create-text-area {:style {:width "100%"} :rows 5 :ref "wsDescription"
                                  :data-test-id "workspace-description-text-field"})
         [:div {:style {:display "flex"}}
          (style/create-form-label "Authorization Domain (optional)")
          (common/render-info-box
           {:text [:div {} [:strong {} "Note:"]
                   [:div {} "An Authorization Domain can only be set when creating a workspace.
                   Once set, it cannot be changed."]
                   (style/create-link {:href "https://software.broadinstitute.org/firecloud/documentation/article?id=9524"
                                       :target "_blank"
                                       :text "Read more about Authorization Domains"})]})]
         (style/create-select
          {:ref "auth-domain"
           :defaultValue -1
           :onChange #(swap! state assoc :selected-auth-domain (-> % .-target .-value))
           :data-test-id "workspace-auth-domain-select"}
          (:groups @state)
          "Select a Group...")
         [comps/ErrorViewer {:error (:server-error @state)}]
         (style/create-validation-error-message (:validation-errors @state))])}])
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/get-groups
      (fn [success? parsed-response]
        (swap! state assoc :groups
               (conj (map :groupName parsed-response)
                     "None")))))
   :create-workspace
   (fn [{:keys [props state refs]}]
     (swap! state dissoc :server-error :validation-errors)
     (if-let [fails (input/validate refs "wsName")]
       (swap! state assoc :validation-errors fails)
       (let [project (nth (:billing-projects props) (int (:selected-project @state)))
             name (input/get-text refs "wsName")
             desc (common/get-text refs "wsDescription")
             attributes (if (clojure.string/blank? desc) {} {:description desc})
             selected-auth-domain-index (int (:selected-auth-domain @state))
             auth-domain (when (> selected-auth-domain-index 0)
                           {:authorizationDomain
                            {:membersGroupName (nth (:groups @state) selected-auth-domain-index)}})]
         (swap! state assoc :creating-wf true)
         (endpoints/call-ajax-orch
          {:endpoint (endpoints/create-workspace project name)
           :payload (conj {:namespace project :name name :attributes attributes} auth-domain)
           :headers utils/content-type=json
           :on-done (fn [{:keys [success? get-parsed-response]}]
                      (swap! state dissoc :creating-wf)
                      (if success?
                        (do (modal/pop-modal)
                            (nav/go-to-path :workspace-summary {:namespace project :name name}))
                        (swap! state assoc :server-error (get-parsed-response false))))}))))})


(react/defc Button
  {:render
   (fn [{:keys [props state]}]
     [:div {:style {:display "inline"}}
      (when (:modal? @state)
        [CreateDialog (merge (select-keys props [:billing-projects])
                             {:dismiss #(swap! state dissoc :modal?)})])
      [comps/Button
       {:text (case (:disabled-reason props)
                :not-loaded [comps/Spinner {:text "Getting billing info..." :style {:margin 0}}]
                "Create New Workspace...")
        :icon :add-new
        :data-test-id "open-create-workspace-modal-button"
        :disabled? (case (:disabled-reason props)
                     nil false
                     :not-loaded "Project billing data has not yet been loaded."
                     :no-billing (comps/no-billing-projects-message)
                     "Project billing data failed to load.")
        :onClick #(swap! state assoc :modal? true)}]])})
