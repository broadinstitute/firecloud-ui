(ns broadfcui.page.method-repo.method.summary
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.markdown :refer [MarkdownView]]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.collapse :refer [Collapse]]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.sticky :refer [Sticky]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.page.method-repo.create-method :as create]
   [broadfcui.page.method-repo.methods-configs-acl :as mca]
   [broadfcui.page.method-repo.redactor :refer [Redactor]]
   [broadfcui.utils :as utils]
   ))

(react/defc Summary
  {:component-will-mount
   (fn [{:keys [locals]}]
     (swap! locals assoc :body-id (gensym "summary")))
   :render
   (fn [{:keys [this]}]
     [:div {:style {:margin "2.5rem 1.5rem" :display "flex"}}
      (this :-render-sidebar)
      (this :-render-main)])
   :-render-sidebar
   (fn [{:keys [props state locals]}]
     (let [{:keys [selected-snapshot]} props
           {:keys [managers]} selected-snapshot
           owner? (contains? (set managers) (utils/get-user-email))
           {:keys [body-id]} @locals
           on-method-created (fn [_ method-id]
                               (nav/go-to-path :method-summary method-id)
                               (common/scroll-to-top))]
       [:div {:style {:flex "0 0 270px" :paddingRight 30}}
        (modals/show-modals
         state
         {:deleting?
          [Redactor {:entity selected-snapshot :config? false :on-delete #(nav/go-to-path :method-repo2)}]
          :sharing?
          [mca/AgoraPermsEditor
           {:save-endpoint (endpoints/persist-agora-entity-acl false selected-snapshot)
            :load-endpoint (endpoints/get-agora-entity-acl false selected-snapshot)
            :entityType (:entityType selected-snapshot)
            :entityName (mca/get-ordered-name selected-snapshot)
            :title (str (:entityType selected-snapshot) " " (mca/get-ordered-name selected-snapshot))}]
          :editing?
          [create/CreateMethodDialog
           {:snapshot selected-snapshot
            :on-created on-method-created}]
          :cloning?
          [create/CreateMethodDialog
           {:duplicate selected-snapshot
            :on-created on-method-created}]})
        [Sticky
         {:sticky-props {:data-check-every 1
                         :data-anchor body-id}
          :contents
          [:div {:style {:width 270}}
           (when owner?
             [buttons/SidebarButton
              {:style :light :color :button-primary
               :text "Permissions..." :icon :settings :margin :bottom
               :onClick #(swap! state assoc :sharing? true)}])
           (when owner?
             [buttons/SidebarButton
              {:style :light :color :button-primary
               :text "Edit..." :icon :edit :margin :bottom
               :onClick #(swap! state assoc :editing? true)}])
           [buttons/SidebarButton
            {:style :light :color :button-primary
             :text "Clone..." :icon :clone :margin :bottom
             :onClick #(swap! state assoc :cloning? true)}]
           (when owner?
             [buttons/SidebarButton
              {:data-test-id "redact-button"
               :style :light :color :exception-state
               :text "Redact" :icon :delete :margin :bottom
               :onClick #(swap! state assoc :deleting? true)}])]}]]))
   :-render-main
   (fn [{:keys [props locals]}]
     (let [{:keys [synopsis managers createDate documentation]} (:selected-snapshot props)
           {:keys [body-id]} @locals]
       [:div {:style {:flex "1 1 auto" :overflow "hidden" :paddingLeft icons/fw-icon-width}
              :id body-id}
        (style/create-summary-block "Synopsis" synopsis)
        [:div {:style {:display "flex"}}
         (style/create-summary-block (str "Method Owner" (when (> (count managers) 1) "s"))
                                     (string/join ", " managers))
         (style/create-summary-block "Created" (common/format-date createDate))]

        [Collapse
         {:style {:marginBottom "2rem"}
          :title (style/create-subsection-header "Documentation")
          :contents
          [:div {:style {:marginTop "1rem" :fontSize "90%" :lineHeight 1.5}}
           (if (string/blank? documentation)
             [:em {} "No documentation provided"]
             [MarkdownView {:text documentation}])]}]]))
   :refresh
   (constantly nil)})
