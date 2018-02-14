(ns broadfcui.page.workspace.create
  (:require
   [dmohs.react :as react]
   [clojure.set :as set]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.input :as input]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.foundation-dropdown :as dropdown]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   [broadfcui.utils.user :as user]
   ))


(react/defc- CreateDialog
  {:get-initial-state
   (fn [{:keys [props]}]
     {:selected-groups (or (vec (:auth-domain props)) [])
      :protected-option :not-loaded})
   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [creating-ws selected-project billing-loaded? server-error validation-errors]} @state
           {:keys [workspace-id]} props
           billing-projects @user/saved-ready-billing-project-names]
       [modals/OKCancelForm
        {:header (if workspace-id "Clone Workspace" "Create New Workspace")
         :ok-button {:text (if workspace-id "Clone Workspace" "Create Workspace")
                     :onClick (if workspace-id #(this :-do-clone) #(this :-create-workspace))}
         :dismiss (:dismiss props)
         :content
         (react/create-element
          [:div {:style {:marginBottom -20}}
           (when creating-ws
             (blocker (if workspace-id "Cloning Workspace..." "Creating Workspace...")))
           (style/create-form-label "Name")
           [input/TextField {:ref "wsName" :autoFocus true :style {:width "100%"}
                             :defaultValue (when workspace-id (str (:name workspace-id) "_copy"))
                             :data-test-id "workspace-name-input"
                             :predicates [(input/nonempty "Workspace name")
                                          (input/alphanumeric_- "Workspace name")]}]
           (style/create-textfield-hint input/hint-alphanumeric_-)
           (style/create-form-label "Billing Project")
           (if billing-loaded?
             (style/create-identity-select-name
               {:ref "project" :value selected-project
                :data-test-id "billing-project-select"
                :onChange #(swap! state assoc :selected-project (-> % .-target .-value))}
               billing-projects)
             (spinner {:style {:margin 0}} "Loading Billing..."))
           (style/create-form-label "Description (optional)")
           (style/create-text-area {:style {:width "100%"} :rows 5 :ref "wsDescription"
                                    :data-test-id "workspace-description-text-field"
                                    :defaultValue (:description props)})
           [:div {:style {:display "flex"}}
            (style/create-form-label "Authorization Domain (optional)")
            (dropdown/render-info-box
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
     (user/reload-billing-projects
      (fn []
        (swap! state assoc :selected-project (first @user/saved-ready-billing-project-names)
               :billing-loaded? true)))
     (endpoints/get-groups
      (fn [_ parsed-response]
        (swap! state assoc :all-groups
               (apply sorted-set (map :groupName parsed-response))))))
   :-create-workspace
   (fn [{:keys [state refs]}]
     (swap! state dissoc :server-error :validation-errors)
     (if-let [fails (input/validate refs "wsName")]
       (swap! state assoc :validation-errors fails)
       (let [project (:selected-project @state)
             name (input/get-text refs "wsName")
             desc (common/get-trimmed-text refs "wsDescription")
             attributes (if (clojure.string/blank? desc) {} {:description desc})
             auth-domain {:authorizationDomain (map
                                                (fn [group-name]
                                                  {:membersGroupName group-name})
                                                (:selected-groups @state))}]
         (swap! state assoc :creating-ws true)
         (endpoints/call-ajax-orch
          {:endpoint (endpoints/create-workspace project name)
           :payload (conj {:namespace project :name name :attributes attributes} auth-domain)
           :headers ajax/content-type=json
           :on-done (fn [{:keys [success? get-parsed-response]}]
                      (swap! state dissoc :creating-ws)
                      (if success?
                        (nav/go-to-path :workspace-summary {:namespace project :name name})
                        (swap! state assoc :server-error (get-parsed-response false))))}))))
   :-do-clone
   (fn [{:keys [props refs state]}]
     (swap! state dissoc :server-error :validation-errors)
     (if-let [fails (input/validate refs "wsName")]
       (swap! state assoc :validation-errors fails)
       (let [name (input/get-text refs "wsName")
             project (:selected-project @state)
             desc (common/get-trimmed-text refs "wsDescription")
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
           :headers ajax/content-type=json
           :on-done (fn [{:keys [success? get-parsed-response]}]
                      (swap! state dissoc :creating-ws)
                      (if success?
                        (nav/go-to-path :workspace-summary {:namespace project :name name})
                        (swap! state assoc :server-error (get-parsed-response false))))}))))
   :-auth-domain-builder
   (fn [{:keys [state props]}]
     (let [{:keys [all-groups selected-groups]} @state
           locked-groups (vec (:auth-domain props))]
       (if-not all-groups
         (spinner {:style {:margin 0}} "Loading Groups...")
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
                   (icons/render-icon {:style {:color (:text-lightest style/colors)
                                               :verticalAlign "middle" :fontSize 22
                                               :padding "0.25rem 0.5rem"}}
                                      :lock)
                   (icons/render-icon {:style {:color (:text-lightest style/colors)
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
  {:component-will-mount
   (fn [{:keys [state]}]
     (add-watch user/saved-ready-billing-project-names :ws-create-button #(swap! state assoc :loaded? true))
     (user/reload-billing-projects
      (fn [err-text]
        (when err-text
          (swap! state assoc :error-message err-text)))))
   :render
   (fn [{:keys [state]}]
     (let [{:keys [loaded? error-message]} @state]
       [:div {:style {:display "inline"}}
        (when (:modal? @state)
          [CreateDialog {:dismiss #(swap! state dissoc :modal?)}])
        [buttons/Button
         {:data-test-id "open-create-workspace-modal-button"
          :text (if loaded?
                  "Create New Workspace..."
                  (spinner {:style {:margin 0}} "Getting billing info..."))
          :icon :add-new
          :disabled? (cond
                       (not loaded?) "Project billing data has not yet been loaded."
                       (empty? @user/saved-ready-billing-project-names) (comps/no-billing-projects-message)
                       error-message "Project billing data failed to load.")
          :onClick #(swap! state assoc :modal? true)}]]))
   :component-will-unmount
   (fn []
     (remove-watch user/saved-ready-billing-project-names :ws-create-button))})
