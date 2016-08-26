(ns org.broadinstitute.firecloud-ui.page.workspace.analysis.tab
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.page.workspace.analysis.igv :refer [IGVContainer]]
    [org.broadinstitute.firecloud-ui.page.workspace.analysis.track-selector :refer [TrackSelectionDialog]]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(react/defc Page
  {:refresh
   (fn [])
   :get-initial-state
   (fn []
     {:tracks []})
   :render
   (fn [{:keys [props state]}]
     [:div {:style {:position "relative"}}
      [:div {:style {:position "absolute"
                     :right "2em" :height "2.5em" :top "-2.5em"}}
       [comps/Button {:text "Select Tracks..."
                      :onClick
                      (fn [_]
                        (modal/push-modal
                          [TrackSelectionDialog
                           (assoc props
                             :tracks (:tracks @state)
                             :on-ok #(swap! state assoc :tracks %))]))}]]
      [IGVContainer {:tracks (:tracks @state)}]])})
