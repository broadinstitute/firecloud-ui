(ns broadfcui.page.external-importer
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.page.method-repo.method.wdl :refer [WDLViewer]]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   ))

(def import-title "Import Workflow to FireCloud")
(def import-subtitle "In order to import a workflow into FireCloud, you'll need two things:")

;; TODO: move these to configs
(def ^:private billing-project-guide-url "https://software.broadinstitute.org/firecloud/documentation/article?id=9765")
(def ^:private firecloud-help-url "https://software.broadinstitute.org/firecloud/documentation/article?id=9846")

(defn render-import-tutorial []
  (let [create-number-label
        (fn [number]
          [:div {:style {:flexShrink 0 :height "2rem" :width "2rem" :borderRadius "50%"
                         :background-color (:button-primary style/colors) :color "white"
                         :fontSize "1.25rem" :textAlign "center" :lineHeight "2rem"}}
           number])]
    [:div {:style {:marginTop "1.5rem"}}
     [:div {:style {:display "flex"}}
      (create-number-label 1)
      [:div {:style {:margin "0 0.5rem"}}
       [:h2 {:style {:margin "0 0 0.3rem"}} "Google Billing Project"]
       "To pay any storage or compute costs"
       [:p {:style {:fontSize "80%"}}
        "Don’t know if you have one? If you own or have 'write & compute' access to an existing workspace,
         that means you have access to a billing project and you’re all set. If you don't, click "
        (links/create-external {:href billing-project-guide-url} "here")
        " for instructions on how to get one."]]]
     [:div {:style {:display "flex"}}
      (create-number-label 2)
      [:div {:style {:marginLeft "0.5rem"}}
       [:h2 {:style {:margin "0 0 0.3rem"}} "FireCloud Account"]
       "To use FireCloud services"
       [:p {:style {:fontSize "80%"}}
        "Need to create a FireCloud account? FireCloud requires a Google account. Click "
        (links/create-external {:href firecloud-help-url} "here")
        " to learn how you can create a Google identity and link any email address to that Google account. Once
         you have signed in and completed the user profile registration step you can start using FireCloud."]]]]))


(react/defc DockstoreImporter
  {:get-initial-state
   (fn [{:keys [props]}]
     {:highlighted-version (:defaultVersion props)})
   :component-will-mount
   (fn [{:keys [props locals]}]
     (let [valid-versions (filter :valid (:workflowVersions props))]
       (swap! locals assoc
              :workflow-versions valid-versions
              :wdls-by-reference (->> valid-versions
                                      (map (fn [{:keys [reference sourceFiles]}]
                                             [reference
                                              (->> sourceFiles
                                                   (keep (fn [{:keys [type content]}]
                                                           (when (= type "DOCKSTORE_WDL")
                                                             content)))
                                                   first)]))
                                      (into {})))))
   :render
   (fn [{:keys [state this]}]
     (let [{:keys [selected-version]} @state]
       (if selected-version
         (this :-render-select-workspace)
         (this :-render-select-version))))
   :-render-select-version
   (fn [{:keys [state locals]}]
     (let [{:keys [highlighted-version]} @state
           {:keys [workflow-versions wdls-by-reference]} @locals]
       [:div {}
        [:div {:style {:fontSize "125%" :margin "0 0 0.5rem 5px"}}
         "Select Workflow Version"]
        (flex/box {:style {:maxHeight 500}}
          [:div {:style {:flex "0 0 340px" :marginRight 5 :overflowY "auto"}}
           (map-indexed (fn [index {:keys [reference]}]
                          [:div {:style {:borderTop (when (pos? index) style/standard-line)
                                         :padding "0.75rem"
                                         :backgroundColor (when (= reference highlighted-version)
                                                            (:tag-background style/colors))
                                         :cursor "pointer"}
                                 :onClick #(swap! state assoc :highlighted-version reference)}
                           reference])
                        workflow-versions)]
          [:div {:style {:flex "1 1 auto"}}
           [WDLViewer {:wdl (wdls-by-reference highlighted-version)}]])
        (flex/box {:style {:marginTop "1rem"}}
          flex/spring
          [buttons/Button
           {:text "Use Selected Version"
            :onClick #(swap! state assoc :selected-version highlighted-version)}])]))
   :-render-select-workspace
   (fn [{:keys [state this]}]
     [:div {:style {:width 500 :margin "0 auto"}}
      (flex/box {:style {:alignItems "center"}}
        (links/create-internal {:onClick #(swap! state dissoc :selected-version)}
          (flex/box {:style {:alignItems "center"}}
            (icons/render-icon {:style {:fontSize "150%" :margin "-3px 0.5rem 0 0"}} :angle-left)
            "Choose Another Version"))
        flex/spring
        [buttons/Button {:text "Export to Workspace"
                         :onClick #(this :-export)}])])
   :-export
   (fn [])})


(defn nowhere-to-save-message []
  (list
   [:div {:style {:fontSize "150%"}}
    (icons/render-icon {:style {:color (:state-exception style/colors) :marginRight "0.5rem"}}
      :error)
    "Billing Account Required"]
   [:p {} "In order to import this workflow, you must be able to save it to a workspace."]
   [:p {}
    "You must have a billing project associated with your account to create a new workspace,
     or else you must be granted write access to an existing workspace."]
   [:p {}
    (links/create-external {:href billing-project-guide-url}
      "Learn how to create a billing project.")]))

(react/defc Importer
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [source item]} props
           {:keys [load-status error error-response error-message docker-payload]} @state]
       [:div {:style {:margin "1.5rem 2rem 0"}}
        [:div {:style {:fontWeight 500 :fontSize "125%" :marginBottom "2rem"}}
         (str "Importing " item " from " source)]
        (case load-status
          :workspace-check (spinner "Checking workspace access...")
          :version-check (spinner "Loading available method versions...")
          :no-destination (nowhere-to-save-message)
          :done nil)
        (when docker-payload
          [DockstoreImporter docker-payload])
        (when error
          (list
           [:div {:style {:marginBottom "1rem" :fontSize "125%"}}
            error]
           (when error-message
             [:div {} error-message])
           [comps/ErrorViewer {:error error-response}]))]))
   :component-will-mount
   (fn [{:keys [this]}]
     (this :-check-workspace-access))
   :-check-workspace-access
   (fn [{:keys [state this]}]
     (swap! state assoc :load-status :workspace-check)
     (endpoints/call-ajax-orch
      {:endpoint endpoints/import-status
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if-not success?
                    (swap! state assoc
                           :load-status :done
                           :error "Error checking import status"
                           :error-response (get-parsed-response false))
                    (let [{:keys [billingProject writableWorkspace]} (get-parsed-response)]
                      (if (or billingProject writableWorkspace)
                        (this :-load-method-versions)
                        (swap! state assoc :load-status :no-destination)))))}))
   :-load-method-versions
   (fn [{:keys [props state]}]
     (let [{:keys [id]} props]
       (swap! state assoc :load-status :version-check)
       (ajax/call
        {:url (str "https://dockstore.org:8443/workflows/path/workflow/" (js/encodeURIComponent id) "/published")
         :on-done (fn [{:keys [success? get-parsed-response raw-response]}]
                    (if-not success?
                      (swap! state assoc
                             :load-status :done
                             :error "Error loading method versions"
                             :error-message raw-response)
                      (swap! state assoc
                             :load-status :done
                             :docker-payload (get-parsed-response))))})))})

(defn add-nav-paths []
  (nav/defpath
   :import
   {:component Importer
    :regex #"import/([^/]+)/(.+)"
    :make-props (fn [source item]
                  (utils/restructure source item))
    :make-path (fn [source item]
                 (str source "/" item))}))
