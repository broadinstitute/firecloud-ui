(ns broadfcui.page.workspace.create
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.input :as input]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.components.modals :as modals]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.nav :as nav]
    [broadfcui.utils :as utils]
    [broadfcui.common.icons :as icons]
    ))


(react/defc CreateDialog
  {:get-initial-state
   (fn [{:keys [props]}]
     {:selected-project (first (:billing-projects props))
      :show-selector? true
      :selected-groups []
      :protected-option :not-loaded})
   :render
   (fn [{:keys [props state refs this]}]
     [modals/OKCancelForm
      {:header "Create New Workspace"
       :ok-button {:text "Create Workspace" :onClick #(react/call :create-workspace this)}
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
           :onChange #(swap! state assoc :selected-project (-> % .-target .-value))}
          (:billing-projects props))
         (style/create-form-label "Name")
         [input/TextField {:ref "wsName" :autoFocus true :style {:width "100%"}
                           :predicates [(input/nonempty "Workspace name")
                                        (input/alphanumeric_- "Workspace name")]}]
         (style/create-textfield-hint input/hint-alphanumeric_-)
         (style/create-form-label "Description (optional)")
         (style/create-text-area {:style {:width "100%"} :rows 5 :ref "wsDescription"})
         [:div {:style {:display "flex"}}
          (style/create-form-label "Authorization Domain (optional)")
          (common/render-info-box
           {:text [:div {} [:strong {} "Note:"]
                   [:div {} "An Authorization Domain can only be set when creating a Workspace.
                   Once set, it cannot be changed."]
                   (style/create-link {:href "https://software.broadinstitute.org/firecloud/documentation/article?id=9524"
                                       :target "_blank"
                                       :text [:span {:style {:white-space "pre"}}
                                              "Read more about Authorization Domains"
                                              icons/external-link-icon]})]})]
         (if (nil? (:all-groups @state))
           [comps/Spinner {:text "Loading Groups..." :style {:margin 0}}]
           [:div {}
            (map-indexed
             (fn [i opt]
               [:div {}
                [:div {:style {:float "left" :width "90%"}}
                 (style/create-select
                  {:disabled true :defaultValue opt}
                  [opt])]
                [:div {:style {:float "right"}}
                 (if (contains? (:locked-groups @state) opt)
                   (icons/icon {:style {:color (:text-lightest style/colors)
                                        :verticalAlign "middle" :fontSize 22
                                        :padding "0.25rem 0.5rem"}}
                               :lock)
                   (icons/icon {:style {:color (:text-lightest style/colors)
                                        :verticalAlign "middle" :fontSize 22
                                        :cursor "pointer" :padding "0.25rem 0.5rem"}
                                :onClick #(swap! state update :selected-groups utils/delete i)}
                               :remove))]])
             (:selected-groups @state))
            (when (not-empty (clojure.set/difference (:all-groups @state) (set (:selected-groups @state))))
              [:div {}
                [:div {:style {:visibility (if (:show-selector? @state) "visible" "hidden") :float "left" :width "90%"}}
                 (style/create-identity-select-name
                  {:ref "auth-domain-selector" :defaultValue -1
                   :onChange #(do
                                (swap! state update :selected-groups conj (-> % .-target .-value))
                                (swap! state assoc :show-selector? false))}
                  (clojure.set/difference (:all-groups @state) (set (:selected-groups @state)))
                  "Select a Group (optional)...")]
                [:div {:style {:float "right" :visibility (if (:show-selector? @state) "hidden" "visible")}}
                 (icons/icon {:style {:color (:text-lightest style/colors)
                                      :verticalAlign "middle" :fontSize 22
                                      :cursor "pointer" :padding "0.25rem 0.5rem"}
                              :onClick #(swap! state assoc :show-selector? true)}
                             :add)]])])
         [comps/ErrorViewer {:error (:server-error @state)}]
         (style/create-validation-error-message (:validation-errors @state))])}])
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/get-groups
      (fn [success? parsed-response]
        (swap! state assoc :all-groups
               (set (map :groupName parsed-response))))))
   :create-workspace
   (fn [{:keys [props state refs]}]
     (swap! state dissoc :server-error :validation-errors)
     (if-let [fails (input/validate refs "wsName")]
       (swap! state assoc :validation-errors fails)
       (let [project (nth (:billing-projects props) (int (:selected-project @state)))
             name (input/get-text refs "wsName")
             desc (common/get-text refs "wsDescription")
             attributes (if (clojure.string/blank? desc) {} {:description desc})
             auth-domain {:authorizationDomain (map
                                                (fn [group-name]
                                                  {:membersGroupName group-name})
                                                (:selected-groups @state))}]
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
        :disabled? (case (:disabled-reason props)
                     nil false
                     :not-loaded "Project billing data has not yet been loaded."
                     :no-billing (comps/no-billing-projects-message)
                     "Project billing data failed to load.")
        :onClick #(swap! state assoc :modal? true)}]])})
