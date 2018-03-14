(ns broadfcui.page.external-importer
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.utils :as utils]
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
     (let [{:keys [source id]} props
           {:keys [load-status error error-response]} @state]
       [:div {:style style/thin-page-style}
        [:div {:style {:fontWeight 500 :fontSize "125%" :marginBottom "2rem"}}
         (str "Importing " id " from " source)]
        (case load-status
          :workspace-check (spinner "Checking workspace access...")
          :version-check (spinner "Loading available method versions...")
          :no-destination (nowhere-to-save-message)
          :done nil)
        (when error
          (list
           [:div {:style {:marginBottom "1rem" :fontSize "125%"}}
            error]
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
   (fn [{:keys [state]}]
     (swap! state assoc :load-status :version-check)
     ;; TODO: do it
     )})

(defn add-nav-paths []
  (nav/defpath
   :import
   {:component Importer
    :regex #"import/([^/]+)/(.+)"
    :make-props (fn [source id]
                  (utils/restructure source id))
    :make-path (fn [source id]
                 (str source "/" id))}))
