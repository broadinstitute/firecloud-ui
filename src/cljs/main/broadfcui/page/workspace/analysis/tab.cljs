(ns broadfcui.page.workspace.analysis.tab
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.page.workspace.analysis.igv :refer [IGVContainer]]
   [broadfcui.page.workspace.analysis.track-selector :refer [TrackSelectionDialog]]
   [broadfcui.utils :as utils]
   ))


(def ^:private tracks-cache (atom {}))

(react/defc Page
  {:refresh
   (fn [])
   :get-initial-state
   (fn [{:keys [props]}]
     {:tracks (get @tracks-cache (:workspace-id props) [])})
   :render
   (fn [{:keys [props state]}]
     [:div {}
      (when (:show-track-selection-dialog? @state)
        [TrackSelectionDialog
         (assoc props
           :dismiss #(swap! state dissoc :show-track-selection-dialog?)
           :tracks (:tracks @state)
           :on-ok #(swap! state assoc :tracks %))])
      [IGVContainer {:tracks (:tracks @state)
                     :workspace-id (:workspace-id props)}]
      [buttons/Button {:text "Select Tracks..."
                       :style {:float "right" :marginTop "1rem"}
                       :onClick #(swap! state assoc :show-track-selection-dialog? true)}]
      (common/clear-both)])
   :component-will-unmount
   (fn [{:keys [props state]}]
     (swap! tracks-cache assoc (:workspace-id props) (:tracks @state)))})
