(ns broadfcui.page.method-repo.method.summary
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.markdown :refer [MarkdownView MarkdownEditor]]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.components.collapse :refer [Collapse]]
   [broadfcui.components.sticky :refer [Sticky]]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.page.workspace.create :as create]
   [broadfcui.page.workspace.monitor.common :as moncommon]
   [broadfcui.page.workspace.summary.acl-editor :refer [AclEditor]]
   [broadfcui.page.workspace.summary.attribute-editor :as attributes]
   [broadfcui.page.workspace.summary.catalog.wizard :refer [CatalogWizard]]
   [broadfcui.page.workspace.summary.library-utils :as library-utils]
   [broadfcui.page.workspace.summary.library-view :refer [LibraryView]]
   [broadfcui.page.workspace.summary.publish :as publish]
   [broadfcui.page.workspace.summary.synchronize :as ws-sync]
   [broadfcui.utils :as utils]
   ))


(react/defc- DeleteDialog
  {:render
   (fn [{:keys [state this]}]
     [comps/OKCancelForm
      {:header "Confirm Delete"
       :content
       [:div {}
        (when (:deleting? @state)
          [comps/Blocker {:banner "Deleting..."}])
        [:p {:style {:margin 0}} "Are you sure you want to delete this workspace?"]
        [:p {} "Bucket data will be deleted too."]
        [comps/ErrorViewer {:error (:server-error @state)}]]
       :ok-button {:text "Delete" :onClick #(this :delete)
                   :data-test-id (config/when-debug "confirm-delete-workspace-button")}}])
   :delete
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
     (swap! locals assoc :label-id (gensym "status") :body-id (gensym "summary")))
   :render
   (fn [{:keys [state props this refs]}]
     (let [{:keys [selected-snapshot request-refresh]} props]
       [:div {}
        [:div {:style {:margin "2.5rem 1.5rem" :display "flex"}}
         (when (:sharing? @state)
           [AclEditor
            (merge (utils/restructure selected-snapshot request-refresh)
                   {:dismiss #(swap! state dissoc :sharing?)
                    :on-users-added (constantly nil)})])
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
           {:keys [synopsis managers createDate documentation]} selected-snapshot
           method-id (select-keys selected-snapshot [:name :namespace])
           {:keys [can-share? owner?]} @state
           {:keys [body-id]} @locals]
       [:div {:style {:flex "0 0 270px" :paddingRight 30}}
        (when (:cloning? @state)
            [create/CreateDialog
             {:dismiss #(swap! state dissoc :cloning?)
              :method-id method-id
              :documentation documentation}])
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
             [comps/SidebarButton {:style :light :margin :top :color :exception-state
                                   :text "Delete" :icon :delete
                                   :data-test-id (config/when-debug "delete-workspace-button")
                                   :onClick #(modal/push-modal
                                              [DeleteDialog (utils/restructure method-id)])}])]}]]))
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
   (fn [{:keys [state refs]}]
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
