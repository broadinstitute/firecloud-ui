(ns broadfcui.page.method-repo.method.summary
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.markdown :refer [MarkdownView]]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.components.collapse :refer [Collapse]]
   [broadfcui.components.sticky :refer [Sticky]]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
   [broadfcui.page.method-repo.create-method :as create]
   [broadfcui.components.modals :as modals]
   [broadfcui.page.method-repo.methods-configs-acl :as mca]
   [broadfcui.page.method-repo.redactor :refer [Redactor]]
   ))


(react/defc- RedactDialog
  {:render
   (fn [{:keys [state this]}]
     [comps/OKCancelForm
      {:header "Confirm Redact"
       :content
       [:div {}
        (when (:deleting? @state)
          [comps/Blocker {:banner "Deleting..."}])
        [:p {:style {:margin 0}} "Are you sure you want to redact this method snapshot?"]
        [comps/ErrorViewer {:error (:server-error @state)}]]
       :ok-button {:text "Redact" :onClick #(this :redact)
                   :data-test-id (config/when-debug "confirm-redact-method-button")}}])
   :redact
   (fn [{:keys [props state]}]
     (swap! state assoc :deleting? true :server-error nil)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/delete-workspace (:workspace-id props))
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (swap! state dissoc :deleting?)
                  (if success?
                    (do (modal/pop-modal) (nav/go-to-path :workspaces))
                    (swap! state assoc :server-error (get-parsed-response false))))}))})

(react/defc Summary
  {:component-will-mount
   (fn [{:keys [locals]}]
     (swap! locals assoc :body-id (gensym "summary")))
   :render
   (fn [{:keys [state props this refs]}]
     (let [{:keys [selected-snapshot request-refresh]} props]
       [:div {}
        [:div {:style {:margin "2.5rem 1.5rem" :display "flex"}}
         (this :-render-sidebar)
         (this :-render-main)]]))
   :component-will-receive-props
   (fn [{:keys [props next-props state this]}]
     (swap! state dissoc :editing? :cloning? :sharing?)
     (when-not (= (:selected-snapshot props) (:selected-snapshot next-props))
       (this :refresh)))
   :-render-sidebar
   (fn [{:keys [props state locals refs this]}]
     (let [{:keys [selected-snapshot request-refresh]} props
           {:keys [managers documentation]} selected-snapshot
           owner? (contains? (set managers) (utils/get-user-email))
           method-id (select-keys selected-snapshot [:name :namespace])
           {:keys [can-share?]} @state
           {:keys [body-id]} @locals
           on-method-created (fn [_ id]
                               (nav/go-to-path :method id)
                               (common/scroll-to-top))
           on-delete #(nav/go-to-path :method-repo)]
       [:div {:style {:flex "0 0 270px" :paddingRight 30}}
        (modals/show-modals
         state
         {:deleting?
          [Redactor (utils/restructure selected-snapshot false on-delete)]
          :sharing?
          [mca/AgoraPermsEditor
           {:save-endpoint (endpoints/persist-agora-entity-acl false selected-snapshot)
            :load-endpoint (endpoints/get-agora-entity-acl false selected-snapshot)
            :entityType (:entityType selected-snapshot)
            :entityName (mca/get-ordered-name selected-snapshot)
            :title (str (:entityType selected-snapshot) " " (mca/get-ordered-name selected-snapshot))
            :on-users-added #((@refs "sync-container") :check-synchronization %)}]
          :cloning?
          [create/CreateMethodDialog
           {:duplicate selected-snapshot
            :on-created on-method-created}]
          :editing-method?
          [create/CreateMethodDialog
           {:snapshot selected-snapshot
            :on-created on-method-created}]})
        [Sticky
         {:sticky-props {:data-check-every 1
                         :data-anchor body-id}
          :contents
          [:div {:style {:width 270}}
           (when-not (some? can-share?)
             (comps/render-blocker "Loading..."))
           (when can-share?
             [comps/SidebarButton
              {:style :light :margin :top :color :button-primary
               :text "Share..." :icon :share
               :data-test-id (config/when-debug "share-method-button")
               :onClick #(swap! state assoc :sharing? true)}])

           [comps/SidebarButton
            {:style :light :color :button-primary :margin :top
             :text "Edit..." :icon :edit
             :onClick #(swap! state assoc :editing? true)}]
           [comps/SidebarButton
            {:style :light :margin :top :color :button-primary
             :text "Clone..." :icon :clone
             :data-test-id (config/when-debug "open-clone-method-modal-button")
             :onClick #(swap! state assoc :cloning? true)}]
           (when owner?
             [comps/SidebarButton
              {:style :light :margin :top :color :exception-state
               :text "Redact" :icon :delete
               :data-test-id (config/when-debug "redact-method-button")
               :onClick #(modal/push-modal
                          [RedactDialog (utils/restructure method-id)])}])]}]]))
   :-render-main
   (fn [{:keys [props locals]}]
     (let [{:keys [synopsis managers createDate documentation]} (:selected-snapshot props)
           {:keys [body-id]} @locals
           make-block (fn [title body]
                        [:div {:style {:flexBasis "50%" :paddingRight "2rem" :marginBottom "2rem"}}
                         [:div {:style {:paddingBottom "0.5rem"}}
                          (style/create-subsection-header title)]
                         (style/create-subsection-contents body)])]
       [:div {:style {:flex "1 1 auto" :overflow "hidden"} :id body-id}

        [:div {:style {:flexBasis "50%" :paddingRight "2rem" :marginBottom "2rem"}}
         [:div {:style {:paddingBottom "0.5rem"}}
          (style/create-subsection-header "Synopsis")]
         (style/create-subsection-contents synopsis)]


        [:div {:style {:display "flex"}}
         (make-block
          (str "Method Owner" (when (> (count managers) 1) "s"))
          (string/join ", " managers))

         (make-block
          "Created"
          (common/format-date createDate))]

        [Collapse
         {:style {:marginBottom "2rem"}
          :title (style/create-subsection-header "Documentation")
          :contents
          [:div {:style {:marginTop "1rem" :fontSize "90%" :lineHeight 1.5}}
           (if (not-empty documentation)
             [MarkdownView {:text documentation}]
             [:em {} "No documentation provided"])]}]]))
   :refresh
   (fn [{:keys [state]}]
     (swap! state dissoc :server-response)
     (endpoints/get-billing-projects
      (fn [err-text projects]
        (if err-text
          (swap! state update :server-response assoc :server-error err-text)
          (swap! state update :server-response
                 assoc :billing-projects (map :projectName projects)))))
     (endpoints/call-ajax-orch
      {:endpoint endpoints/get-library-curator-status
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (swap! state update :server-response assoc :curator? (:curator (get-parsed-response)))
                    (swap! state update :server-response assoc :server-error "Unable to determine curator status")))}))})
