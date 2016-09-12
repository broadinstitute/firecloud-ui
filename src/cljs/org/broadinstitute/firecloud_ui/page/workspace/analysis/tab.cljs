(ns org.broadinstitute.firecloud-ui.page.workspace.analysis.tab
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.page.workspace.analysis.igv :refer [IGVContainer]]
    [org.broadinstitute.firecloud-ui.page.workspace.analysis.track-selector :refer [TrackSelectionDialog]]
    [org.broadinstitute.firecloud-ui.utils :as utils]
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
