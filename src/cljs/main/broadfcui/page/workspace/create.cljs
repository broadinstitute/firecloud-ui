(ns broadfcui.page.workspace.create
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.input :as input]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
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
     [comps/OKCancelForm
      {:header "Create New Workspace"
       :ok-button {:text "Create Workspace" :onClick #(react/call :create-workspace this)}
       :get-first-element-dom-node #(@refs "project")
       :content
       (react/create-element
         [:div {:style {:marginBottom -20}}
          (when (:creating-wf @state)
            [comps/Blocker {:banner "Creating Workspace..."}])
          (style/create-form-label "Billing Project")
          (style/create-select
            {:ref "project" :value (:selected-project @state)
             :onChange #(swap! state assoc :selected-project (-> % .-target .-value))}
            (:billing-projects props))
          (style/create-form-label "Name")
          [input/TextField {:ref "wsName" :style {:width "100%"}
                            :predicates [(input/nonempty "Workspace name")
                                         (input/alphanumeric_- "Workspace name")]}]
          (style/create-textfield-hint "Only letters, numbers, underscores, and dashes allowed")
          (style/create-form-label "Description (optional)")
          (style/create-text-area {:style {:width "100%"} :rows 5 :ref "wsDescription"})
          [:div {:style {:marginBottom "1em"}}
           [comps/Checkbox
            {:ref "protected-check"
             :label "Workspace intended to contain NIH protected data"
             :disabled? (not= (:protected-option @state) :enabled)
             :disabled-text (case (:protected-option @state)
                              :not-loaded "Account status has not finished loading."
                              :not-available "This option is not available for your account."
                              nil)}]]
          [comps/ErrorViewer {:error (:server-error @state)}]
          (style/create-validation-error-message (:validation-errors @state))])}])
   :component-did-mount
   (fn [{:keys [state]}]
     (utils/ajax-orch
      "/nih/status"
      {:on-done (fn [{:keys [success? get-parsed-response]}]
                  (if (and success? (get (get-parsed-response false) "isDbgapAuthorized"))
                    (swap! state assoc :protected-option :enabled)
                    (swap! state assoc :protected-option :not-available)))}))
   :create-workspace
   (fn [{:keys [props state refs]}]
     (swap! state dissoc :server-error :validation-errors)
     (if-let [fails (input/validate refs "wsName")]
       (swap! state assoc :validation-errors fails)
       (let [project (nth (:billing-projects props) (int (:selected-project @state)))
             name (input/get-text refs "wsName")
             desc (common/get-text refs "wsDescription")
             attributes (if (clojure.string/blank? desc) {} {:description desc})
             protected? (react/call :checked? (@refs "protected-check"))]
         (swap! state assoc :creating-wf true)
         (endpoints/call-ajax-orch
          {:endpoint (endpoints/create-workspace project name)
           :payload {:namespace project :name name :attributes attributes :isProtected protected?}
           :headers utils/content-type=json
           :on-done (fn [{:keys [success? get-parsed-response]}]
                      (swap! state dissoc :creating-wf)
                      (if success?
                        (do (modal/pop-modal)
                          (nav/navigate (:nav-context props) (str project ":" name)))
                        (swap! state assoc :server-error (get-parsed-response false))))}))))})


(react/defc Button
  {:render
   (fn [{:keys [props]}]
     (assert (:nav-context props) "Missing :nav-context prop")
     [:div {:style {:display "inline"}}
      [comps/Button
       {:text "Create New Workspace..." :icon :add
        :disabled? (case (:disabled-reason props)
                     nil false
                     :not-loaded "Project billing data has not yet been loaded."
                     :no-billing [:div {:style {:textAlign "center"}} (str "You must have a billing project associated with your account"
                                      " to create a new workspace. To learn how to create a billing project, click ")
                                      [:a {:target "_blank"
                                           :href "https://software.broadinstitute.org/firecloud/guide/topic?name=firecloud-google"} "here"] "."]
                     "Project billing data failed to load.")
        :onClick #(modal/push-modal [CreateDialog {:billing-projects (:billing-projects props)
                                                   :nav-context (:nav-context props)}])}]])})
