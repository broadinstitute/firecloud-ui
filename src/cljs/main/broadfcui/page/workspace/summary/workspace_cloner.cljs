(ns broadfcui.page.workspace.summary.workspace-cloner
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.input :as input]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.config :as config]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.utils :as utils]
    ))

(react/defc WorkspaceCloner
  {:get-initial-state
   (fn [{:keys [props]}]
     {:selected-project (first (:billing-projects props))})
   :render
   (fn [{:keys [props refs state this]}]
     [comps/OKCancelForm
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
         [input/TextField {:ref "name" :autoFocus true
                           :style {:width "100%"}
                           :defaultValue (get-in props [:workspace-id :name])
                           :placeholder "Required"
                           :predicates [(input/nonempty "Workspace name")
                                        (input/alphanumeric_- "Workspace name")]}]
         (style/create-textfield-hint "Only letters, numbers, underscores, and dashes allowed")
         (style/create-form-label "Description (optional)")
         (style/create-text-area {:style {:width "100%"} :rows 5 :ref "wsDescription"
                                  :defaultValue (:description props)})
         [:div {:style {:display "flex"}}
          (style/create-form-label (str "Authorization Domain" (when-not (:auth-domain props) " (optional)")))
          (common/render-info-box
            {:text [:div {} [:strong {} "Note:"]
                    [:div {} "An Authorization Domain can only be set when creating the workspace.
                     Once set, it cannot be changed."]
                    (style/create-link {:href "https://software.broadinstitute.org/firecloud/documentation/article?id=9524"
                                        :target "_blank"
                                        :text "Read more about Authorization Domains"})]})]
         (if-let [auth-domain (:auth-domain props)]
           [:div {:style {:fontStyle "italic" :fontSize "80%"}}
            "The cloned workspace will automatically inherit the Authorization Domain "
            [:strong {} auth-domain] " from this workspace"]
           (style/create-select
            {:ref "auth-domain"
             :defaultValue -1
             :onChange #(swap! state assoc :selected-auth-domain (-> % .-target .-value))}
            (:groups @state)
            "Select a Group..."))
         (style/create-validation-error-message (:validation-error @state))
         [comps/ErrorViewer {:error (:error @state)
                             :expect {409 "A workspace with this name already exists in this project"}}]])}])
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/get-groups
      (fn [success? parsed-response]
        (swap! state assoc :groups
               (swap! state assoc :groups
                      (conj (map :groupName parsed-response)
                            "None")))))
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
             selected-auth-domain-index (int (:selected-auth-domain @state))
             auth-domain (if (:auth-domain props)
                           {:authorizationDomain {:membersGroupName (:auth-domain props)}}
                           (when (> selected-auth-domain-index 0)
                             {:authorizationDomain
                              {:membersGroupName (nth (:groups @state) selected-auth-domain-index)}}))]
         (swap! state assoc :working? true :validation-error nil :error nil)
         (endpoints/call-ajax-orch
          {:endpoint (endpoints/clone-workspace (:workspace-id props))
           :payload (conj {:namespace project :name name :attributes attributes} auth-domain)
           :headers utils/content-type=json
           :on-done (fn [{:keys [success? get-parsed-response]}]
                      (swap! state dissoc :working?)
                      (if success?
                        (do (modal/pop-modal) ((:on-success props) project name))
                        (swap! state assoc :error (get-parsed-response false))))}))))})
