(ns broadfcui.page.workspace.create
  (:require
   [dmohs.react :as react]
   [clojure.set :as set]
   [clojure.string :as string]
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


(react/defc WorkspaceCreationForm
  {:validate
   (fn [{:keys [refs]}]
     (input/validate refs "wsName"))
   :get-field-values
   (fn [{:keys [state refs]}]
     (swap! state dissoc :validation-errors)
     (if-let [fails (input/validate refs "wsName")]
       (do (swap! state assoc :validation-errors fails) nil)
       {:project (:selected-project @state)
        :name (input/get-text refs "wsName")
        :description (not-empty (common/get-trimmed-text refs "wsDescription"))
        :auth-domain (map (fn [group] {:membersGroupName group}) (:selected-groups @state))}))
   :get-initial-state
   (fn [{:keys [props]}]
     {:selected-groups (or (vec (:auth-domain props)) [])})
   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [workspace-id description auth-domain]} props
           {:keys [billing-loaded? selected-project validation-errors]} @state]
       [:div {}
        (style/create-form-label "Workspace Name")
        [input/TextField {:data-test-id "workspace-name-input"
                          :ref "wsName" :autoFocus true :style {:width "100%"}
                          :defaultValue (when workspace-id (str (:name workspace-id) "_copy"))
                          :predicates [(input/nonempty "Workspace name")
                                       (input/alphanumeric_-space "Workspace name")]}]
        (style/create-textfield-hint input/hint-alphanumeric_-space)
        (style/create-form-label "Billing Project")
        (if billing-loaded?
          (style/create-identity-select-name
            {:data-test-id "billing-project-select"
             :value selected-project
             :onChange #(swap! state assoc :selected-project (-> % .-target .-value))}
            @user/saved-ready-billing-project-names)
          (spinner {:style {:margin 0}} "Loading Billing..."))
        (style/create-form-label "Description (optional)")
        (style/create-text-area {:data-test-id "workspace-description-text-field"
                                 :style {:width "100%"} :rows 5 :ref "wsDescription"
                                 :defaultValue description})
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
        (when auth-domain
          [:div {:style {:fontStyle "italic" :fontSize "80%" :paddingBottom "0.25rem"}}
           "The cloned Workspace will automatically inherit the Authorization Domain from this Workspace."
           [:div {} "You may add Groups to the Authorization Domain, but you may not remove existing ones."]])
        (this :-auth-domain-builder)
        (style/create-validation-error-message validation-errors)]))
   :component-did-mount
   (fn [{:keys [state]}]
     (user/reload-billing-projects
      (fn []
        (swap! state assoc
               :selected-project (first @user/saved-ready-billing-project-names)
               :billing-loaded? true)))
     (endpoints/get-groups
      (fn [_ parsed-response]
        (swap! state assoc :all-groups (apply sorted-set (map :groupName parsed-response))))))
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
                   {:data-test-id "selected-auth-domain-group" :value opt
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
               {:data-test-id "workspace-auth-domain-select" :defaultValue -1
                :onChange #(swap! state update :selected-groups conj (-> % .-target .-value))}
               (set/difference all-groups (set selected-groups))
               (str "Select " (if (empty? selected-groups) "a" "another") " Group..."))])
          (common/clear-both)])))})


(react/defc CreateDialog
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [creating-ws server-error]} @state
           {:keys [workspace-id]} props]
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
           [WorkspaceCreationForm (merge (select-keys props [:workspace-id :description :auth-domain])
                                         {:ref "form"})]
           [comps/ErrorViewer {:error server-error}]])}]))
   :-create-workspace
   (fn [{:keys [state refs]}]
     (swap! state dissoc :server-error)
     (when-let [{:keys [project name description auth-domain]} ((@refs "form") :get-field-values)]
       (swap! state assoc :creating-ws true)
       (endpoints/call-ajax-orch
        {:endpoint endpoints/create-workspace
         :payload {:namespace project
                   :name name
                   :attributes (if (string/blank? description) {} {:description description})
                   :authorizationDomain auth-domain}
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :creating-ws)
                    (if success?
                      (nav/go-to-path :workspace-summary {:namespace project :name name})
                      (swap! state assoc :server-error (get-parsed-response false))))})))
   :-do-clone
   (fn [{:keys [props refs state]}]
     (swap! state dissoc :server-error)
     (when-let [{:keys [project name description auth-domain]} ((@refs "form") :get-field-values)]
       (swap! state assoc :creating-ws true)
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/clone-workspace (:workspace-id props))
         :payload {:namespace project
                   :name name
                   :attributes (if (or (:description props) (not (string/blank? description)))
                                 {:description description}
                                 {})
                   :authorizationDomain auth-domain}
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (swap! state dissoc :creating-ws)
                    (if success?
                      (nav/go-to-path :workspace-summary {:namespace project :name name})
                      (swap! state assoc :server-error (get-parsed-response false))))})))})


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
