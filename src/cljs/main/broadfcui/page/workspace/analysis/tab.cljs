(ns broadfcui.page.workspace.analysis.tab
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.input :as input]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.components.modals :as modals]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.workspace.analysis.igv :refer [IGVContainer]]
   [broadfcui.page.workspace.analysis.track-selector :refer [TrackSelectionDialog]]
   [broadfcui.utils :as utils]
   ))


(def ^:private tracks-cache (atom {}))

(react/defc- ConfigImporter
 {
  :render
  (fn [{:keys [state this]}]
    [:div {:style {:paddingBottom "0.5rem"}} (style/create-section-header "Notebooks")]
    [modals/OKCancelForm
      {:header "Create Cluster"
       :ok-button {:text "Create"
                   :onClick #(this :-create-cluster)}
       :content
       (react/create-element
         [:div {:style {:marginBottom -20}}
          (style/create-form-label "Name")
          [input/TextField {:ref "clName" :autoFocus true :style {:width "100%"}
                            :predicates [(input/nonempty "Cluster name")
                                         (input/alphanumeric_- "Cluster name")]}]])}])
  :-create-cluster
  (fn [{:keys [this state]}]
    (endpoints/call-ajax-leo
     {:endpoint (endpoints/create-cluster "broad-dsde-dev" "cluster219-anu")
      :payload {:bucketPath "" :serviceAccount "" :labels {}}
      :headers utils/content-type=json
      :on-done (fn [{:keys [success? get-parsed-response]}]
                 ;(swap! state dissoc :creating?)
                 ;(if success?
                 ;  (do (modal/pop-modal) ((:on-success props) ((get-parsed-response false) "submissionId")))
                 ;  (swap! state assoc :launch-server-error (get-parsed-response false)))
                 )}))})


(react/defc Page
  {
   :render
   (fn [{:keys [state]}]
     [:div {}
      [comps/Button {:text "Create Cluster..."
                     :onClick #(modal/push-modal
                                [ConfigImporter {} ]
                                 ;{:workspace-id (:workspace-id props)
                                 ; :data-test-id "import-method-configuration-modal"
                                 ; :after-import (fn [{:keys [config-id]}]
                                 ;                 (modal/pop-modal))}]
                                )}]])})