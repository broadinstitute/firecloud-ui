(ns broadfcui.page.external-importer
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.blocker :refer [blocker]]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.page.method-repo.method.export-destination-form :refer [ExportDestinationForm]]
   [broadfcui.page.method-repo.method.wdl :refer [WDLViewer]]
   [broadfcui.page.workspace.workspace-common :as ws-common]
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
  {:render
   (fn [{:keys [state this]}]
     (let [{:keys [wdl load-error]} @state]
       (cond load-error (this :-render-error)
             wdl (this :-render-export)
             :else (spinner "Loading WDL..."))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (let [{:keys [id version]} props]
       (endpoints/dockstore-get-wdl
        id version
        (fn [{:keys [success? get-parsed-response]}]
          (if success?
            (swap! state assoc :wdl (:descriptor (get-parsed-response)))
            (swap! state assoc :load-error (get-parsed-response false)))))))
   :-render-error
   (fn [{:keys [state]}]
     [:div {}
      [:div {:style {:margin "0.5rem 0"}}
       "Error loading WDL. Please verify the workflow path and version and ensure this workflow supports WDL."]
      [comps/ErrorViewer {:error (:load-error @state)}]])
   :-render-export
   (fn [{:keys [props state locals this]}]
     (let [{:keys [id]} props
           {:keys [banner server-error wdl]} @state]
       [:div {}
        (blocker banner)
        [:div {:style {:marginBottom "1rem"}}
         (icons/render-icon {:style {:marginRight "0.5rem" :color (:state-warning style/colors)}}
           :warning)
         "Please note: Dockstore cannot guarantee that the WDL and Docker image referenced by this Workflow will not change. We advise you to review the WDL before future runs."]
        [:div {:style {:display "flex"}}
         [:div {:style {:marginRight "1rem"}}
          [ExportDestinationForm {:ref #(swap! locals assoc :form %)
                                  :initial-name (-> id (string/split #"/") last)}]
          [buttons/Button {:text "Export" :onClick #(this :-export)}]
          [:div {:style {:marginTop "1rem"}}
           [comps/ErrorViewer {:error server-error}]]]
         [:div {:style {:flex "1 1 auto"}}
          [WDLViewer {:wdl wdl}]]]]))
   :-export
   (fn [{:keys [locals this]}]
     (let [{:keys [form]} @locals]
       (when (form :valid?)
         (this :-resolve-workspace (form :get-field-values)))))
   :-resolve-workspace
   (fn [{:keys [state this]} {:keys [workspace] :as form-data}]
     (swap! state assoc :banner "Resolving workspace...")
     (let [{:keys [existing-workspace new-workspace]} workspace
           {:keys [project name description auth-domain]} new-workspace]
       (if existing-workspace
         (this :-export-method (ws-common/workspace->id existing-workspace) form-data)
         (endpoints/call-ajax-orch
          {:endpoint endpoints/create-workspace
           :payload {:namespace project
                     :name name
                     :attributes (if (string/blank? description) {} {:description description})
                     :authorizationDomain auth-domain}
           :headers ajax/content-type=json
           :on-done (fn [{:keys [success? get-parsed-response]}]
                      (if success?
                        (this :-export-method {:namespace project :name name} form-data)
                        (utils/multi-swap! state (assoc :server-error (get-parsed-response false))
                                                 (dissoc :banner))))}))))
   :-export-method
   (fn [{:keys [props state]} {:keys [namespace] :as workspace-id} {:keys [name]}]
     (swap! state assoc :banner "Exporting method...")
     (let [config-id (utils/restructure namespace name)]
       (endpoints/call-ajax-orch
        {:endpoint (endpoints/post-workspace-method-config workspace-id)
         :payload {:namespace namespace
                   :name name
                   :rootEntityType (first common/root-entity-types)
                   :inputs {} :outputs {} :prerequisites {}
                   :methodRepoMethod {:sourceRepo "dockstore"
                                      :methodPath (:id props)
                                      :methodVersion (:version props)}
                   :methodConfigVersion 1
                   :deleted false}
         :headers ajax/content-type=json
         :on-done (fn [{:keys [success? get-parsed-response]}]
                    (if success?
                      (nav/go-to-path :workspace-method-config workspace-id config-id)
                      (utils/multi-swap! state (assoc :server-error (get-parsed-response false))
                                               (dissoc :banner))))})))})


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
  {:get-initial-state
   (fn []
     {:load-status :workspace-check})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [source item]} props
           {:keys [load-status error error-response]} @state]
       [:div {:style {:margin "1.5rem 2rem 0"}}
        [:div {:style {:fontWeight 500 :fontSize "125%" :marginBottom "2rem"}}
         (str "Importing " item " from " (string/capitalize source))]
        (case load-status
          :workspace-check (spinner "Checking workspace access...")
          :error (list
                  [:div {:style {:marginBottom "1rem" :fontSize "125%"}}
                   error]
                  [comps/ErrorViewer {:error error-response}])
          :no-destination (nowhere-to-save-message)
          :done (case source
                  "dockstore" (let [[id version] (string/split item #":" 2)]
                                [DockstoreImporter (utils/restructure id version)])))]))
   :component-will-mount
   (fn [{:keys [state]}]
     (endpoints/call-ajax-orch
      {:endpoint endpoints/import-status
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if-not success?
                    (swap! state assoc
                           :load-status :error
                           :error "Error checking import status"
                           :error-response (get-parsed-response false))
                    (let [{:keys [billingProject writableWorkspace]} (get-parsed-response)]
                      (swap! state assoc :load-status (if (or billingProject writableWorkspace) :done :no-destination)))))}))})

(defn add-nav-paths []
  (nav/defpath
   :import
   {:component Importer
    :regex #"import/([^/]+)/(.+)"
    :make-props (fn [source item]
                  (utils/restructure source item))
    :make-path (fn [source item]
                 (str source "/" item))}))
