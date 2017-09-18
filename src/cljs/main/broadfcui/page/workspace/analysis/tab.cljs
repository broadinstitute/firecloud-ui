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
    (fn [{:keys [state]}]
      [:div {}
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
                                           (input/alphanumeric_- "Cluster name")]}]])}]])
    :-create-cluster
    (fn [{:keys [state]}]
      (endpoints/call-ajax-leo
       {:endpoint (endpoints/create-cluster "broad-dsde-dev" "cluster219-anu")
        :payload {:bucketPath "" :serviceAccount "" :labels {}}
        :headers utils/content-type=json
        :on-done (fn [{:keys [success? get-parsed-response]}]
                   (swap! state dissoc :creating?)
                   (if success?
                     (do (modal/pop-modal) ((:on-success props) ((get-parsed-response false) "submissionId")))
                     (swap! state assoc :launch-server-error (get-parsed-response false))))}))})


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


  ;{:refresh
  ; (fn [])
  ; :show-track-selection-dialog
  ; (fn [{:keys [props state]}]
  ;   (modal/push-modal
  ;    [TrackSelectionDialog
  ;     (assoc props
  ;       :tracks (:tracks @state)
  ;       :on-ok #(swap! state assoc :tracks %))]))
  ; :get-initial-state
  ; (fn [{:keys [props]}]
  ;   {:tracks (get @tracks-cache (:workspace-id props) [])})
  ; :render
  ; (fn [{:keys [state this]}]
  ;   [:div {}
  ;    [IGVContainer {:tracks (:tracks @state)}]
  ;    [comps/Button {:text "Select Tracks..."
  ;                   :style {:float "right" :marginTop "1rem"}
  ;                   :onClick #(this :show-track-selection-dialog)}]
  ;    (common/clear-both)])
  ; :component-will-unmount
  ; (fn [{:keys [props state]}]
  ;   (swap! tracks-cache assoc (:workspace-id props) (:tracks @state)))})
