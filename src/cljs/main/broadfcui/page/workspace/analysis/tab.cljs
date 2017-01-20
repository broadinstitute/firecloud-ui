(ns broadfcui.page.workspace.analysis.tab
  (:require
    [dmohs.react :as react]
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
   :get-tracks-button
   (fn [{:keys [props state]}]
     [comps/Button {:text "Select Tracks..."
                    :onClick
                    (fn [_]
                      (modal/push-modal
                        [TrackSelectionDialog
                         (assoc props
                           :tracks (:tracks @state)
                           :on-ok #(swap! state assoc :tracks %))]))}])
   :get-initial-state
   (fn [{:keys [props]}]
     {:tracks (get @tracks-cache (:workspace-id props) [])})
   :render
   (fn [{:keys [state]}]
     [IGVContainer {:tracks (:tracks @state)}])
   :component-will-unmount
   (fn [{:keys [props state]}]
     (swap! tracks-cache assoc (:workspace-id props) (:tracks @state)))})
