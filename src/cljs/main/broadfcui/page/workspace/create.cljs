(ns broadfcui.page.workspace.create
  (:require
   [dmohs.react :as react]
   [clojure.set :as set]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.input :as input]
   [broadfcui.common.links :as links]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   ))


(react/defc- CreateDialog
  {:get-initial-state
   (fn [{:keys [props]}]
     {:selected-project (first (:billing-projects props))
      :selected-groups (or (vec (:auth-domain props)) [])
      :protected-option :not-loaded})
   :render
   (fn [{:keys [props state refs this]}]
     (let [{:keys [creating-ws selected-project server-error validation-errors]} @state
           {:keys [workspace-id]} props]
       [modals/OKCancelForm
        {:header (if workspace-id "Clone Workspace" "Create New Workspace")
         :ok-button {:text (if workspace-id "Clone Workspace" "Create Workspace")
                     :data-test-id "create-workspace-button"
                     :onClick (if workspace-id #(this :-do-clone) #(this :-create-workspace))}
         :dismiss (:dismiss props)
         :get-first-element-dom-node #(@refs "project")
         :content
         (react/create-element
          [:div {:style {:marginBottom -20}}
           (when creating-ws
             [comps/Blocker {:banner (if workspace-id "Cloning Workspace..." "Creating Workspace...")}])
           (style/create-form-label "Billing Project")
           (style/create-select
            {:ref "project" :value selected-project
             :data-test-id "billing-project-select"
             :onChange #(swap! state assoc :selected-project (-> % .-target .-value))}
            (:billing-projects props))
           (style/create-form-label "Name")
           [input/TextField {:ref "wsName" :autoFocus true :style {:width "100%"}
                             :defaultValue (when workspace-id (str (:name workspace-id) "_copy"))
                             :data-test-id "workspace-name-input"
                             :predicates [(input/nonempty "Workspace name")
                                          (input/alphanumeric_- "Workspace name")]}]
           (style/create-textfield-hint input/hint-alphanumeric_-)
           (style/create-form-label "Description (optional)")
           (style/create-text-area {:style {:width "100%"} :rows 5 :ref "wsDescription"
                                    :data-test-id "workspace-description-text-field"
                                    :defaultValue (:description props)})
           [:div {:style {:display "flex"}}
            (style/create-form-label "Authorization Domain (optional)")
            (common/render-info-box
             {:text [:div {} [:strong {} "Note:"]
                     [:div {}
                      "An Authorization Domain can only be set when creating a Workspace.
                       Once set, it cannot be changed."]
                     [:span {:style {:white-space "pre"}}
                      (links/create-external
                       {:href "https://software.broadinstitute.org/firecloud/documentation/article?id=9524"}
                       "Read more about Authorization Domains")]]})]
           (when (:auth-domain props)
             [:div {:style {:fontStyle "italic" :fontSize "80%" :paddingBottom "0.25rem"}}
              "The cloned Workspace will automatically inherit the Authorization Domain from this Workspace."
              [:div {} "You may add Groups to the Authorization Domain, but you may not remove existing ones."]])
           (this :-auth-domain-builder)
           [comps/ErrorViewer {:error server-error}]
           (style/create-validation-error-message validation-errors)])}]))
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/get-groups
      (fn [_ parsed-response]
        (swap! state assoc :all-groups
               (apply sorted-set (map :groupName parsed-response))))))
   :-create-workspace
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
         (swap! state assoc :creating-ws true)
         (endpoints/call-ajax-orch
          {:endpoint (endpoints/create-workspace project name)
           :payload (conj {:namespace project :name name :attributes attributes} auth-domain)
           :headers utils/content-type=json
           :on-done (fn [{:keys [success? get-parsed-response]}]
                      (swap! state dissoc :creating-ws)
                      (if success?
                        (do (modal/pop-modal)
                            (nav/go-to-path :workspace-summary {:namespace project :name name}))
                        (swap! state assoc :server-error (get-parsed-response false))))}))))
   :-do-clone
   (fn [{:keys [props refs state]}]
     (swap! state dissoc :server-error :validation-errors)
     (if-let [fails (input/validate refs "wsName")]
       (swap! state assoc :validation-errors fails)
       (let [name (input/get-text refs "wsName")
             project (nth (:billing-projects props) (int (:selected-project @state)))
             desc (common/get-text refs "wsDescription")
             attributes (if (or (:description props) (not (clojure.string/blank? desc)))
                          {:description desc}
                          {})
             auth-domain {:authorizationDomain (map
                                                (fn [group-name]
                                                  {:membersGroupName group-name})
                                                (:selected-groups @state))}]
         (swap! state assoc :creating-ws true)
         (endpoints/call-ajax-orch
          {:endpoint (endpoints/clone-workspace (:workspace-id props))
           :payload (conj {:namespace project :name name :attributes attributes} auth-domain)
           :headers utils/content-type=json
           :on-done (fn [{:keys [success? get-parsed-response]}]
                      (swap! state dissoc :creating-ws)
                      (if success?
                        (do (modal/pop-modal)
                          (nav/go-to-path :workspace-summary {:namespace project :name name}))
                        (swap! state assoc :server-error (get-parsed-response false))))}))))
   :-auth-domain-builder
   (fn [{:keys [state props]}]
     (let [{:keys [all-groups selected-groups]} @state
           locked-groups (vec (:auth-domain props))]
       (if-not all-groups
         [comps/Spinner {:text "Loading Groups..." :style {:margin 0}}]
         [:div {}
          (if (empty? all-groups)
            [:div {} "You are not a member of any groups. You must be a member of a group to set an Authorization Domain."]
            (map-indexed
             (fn [i opt]
               [:div {}
                [:div {:style {:float "left" :width "90%"}}
                 (style/create-identity-select-name
                  {:value opt
                   :data-test-id "selected-auth-domain-group"
                   :disabled (utils/seq-contains? locked-groups opt)
                   :onChange #(swap! state update :selected-groups assoc i (-> % .-target .-value))}
                  (set/difference all-groups (set (utils/delete selected-groups i))))]
                [:div {:style {:float "right"}}
                 (if (utils/seq-contains? locked-groups opt)
                   (icons/icon {:style {:color (:text-lightest style/colors)
                                        :verticalAlign "middle" :fontSize 22
                                        :padding "0.25rem 0.5rem"}}
                               :lock)
                   (icons/icon {:style {:color (:text-lightest style/colors)
                                        :verticalAlign "middle" :fontSize 22
                                        :cursor "pointer" :padding "0.25rem 0.5rem"}
                                :onClick #(swap! state update :selected-groups utils/delete i)}
                               :remove))]])
             selected-groups))
          (when (not-empty (set/difference all-groups selected-groups))
            [:div {:style {:float "left" :width "90%"}}
             (style/create-identity-select-name
              {:defaultValue -1
               :data-test-id "workspace-auth-domain-select"
               :onChange #(swap! state update :selected-groups conj (-> % .-target .-value))}
              (set/difference all-groups (set selected-groups))
              (str "Select " (if (empty? selected-groups) "a" "another") " Group..."))])
          (common/clear-both)])))})


(react/defc Button
  {:render
   (fn [{:keys [props state]}]
     [:div {:style {:display "inline"}}
      (when (:modal? @state)
        [CreateDialog (merge (select-keys props [:billing-projects])
                             {:dismiss #(swap! state dissoc :modal?)})])
      [buttons/Button
       {:data-test-id "open-create-workspace-modal-button"
        :text (case (:disabled-reason props)
                :not-loaded [comps/Spinner {:text "Getting billing info..." :style {:margin 0}}]
                "Create New Workspace...")
        :icon :add-new
        :disabled? (case (:disabled-reason props)
                     nil false
                     :not-loaded "Project billing data has not yet been loaded."
                     :no-billing (comps/no-billing-projects-message)
                     "Project billing data failed to load.")
        :onClick #(swap! state assoc :modal? true)}]])})
