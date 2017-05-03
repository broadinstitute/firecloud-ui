(ns broadfcui.page.workspace.analysis.tab
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.modal :as modal]
    [broadfcui.page.workspace.analysis.igv :refer [IGVContainer]]
    [broadfcui.page.workspace.analysis.track-selector :refer [TrackSelectionDialog]]
    [broadfcui.utils :as utils]
    ))


(def ^:private tracks-cache (atom {}))

(react/defc Page
  {:refresh
   (fn [])
   :show-track-selection-dialog
   (fn [{:keys [props state]}]
     (modal/push-modal
      [TrackSelectionDialog
       (assoc props
         :tracks (:tracks @state)
         :on-ok #(swap! state assoc :tracks %))]))
   :get-initial-state
   (fn [{:keys [props]}]
     {:tracks (get @tracks-cache (:workspace-id props) [])})
   :render
   (fn [{:keys [state this]}]
     [:div {}
      [IGVContainer {:tracks (:tracks @state)}]
      [comps/Button {:text "Select Tracks..."
                     :style {:float "right" :marginTop "1rem"}
                     :onClick #(this :show-track-selection-dialog)}]
      (common/clear-both)])
   :component-will-unmount
   (fn [{:keys [props state]}]
     (swap! tracks-cache assoc (:workspace-id props) (:tracks @state)))})
