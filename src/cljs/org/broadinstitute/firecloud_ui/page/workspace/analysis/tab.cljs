(ns org.broadinstitute.firecloud-ui.page.workspace.analysis.tab
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.entity-table :refer [EntityTable]]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.page.workspace.analysis.igv :as igv]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(def ^:private supported-file-types [".bam" ".vcf" ".bed"])

(react/defc TrackSelectionDialog
  {:get-initial-state
   (fn [{:keys [props]}]
     {:tracks-set (set (:tracks props))
      :tracks-vec (vec (:tracks props))})
   :render
   (fn [{:keys [props state]}]
     [modal/OKCancelForm
      {:header "Select IGV Tracks"
       :ok-button {:text "Load" :onClick #(do ((:on-ok props) (:tracks-vec @state)) (modal/pop-modal))}
       :content
       (react/create-element
         [:div {:style {:width "80vw" :background "white" :border style/standard-line}}
          [comps/SplitPane
           {:left
            [:div {:style {:padding "1em"}}
             [:div {:style {:paddingBottom "1em"}}
              "Available data"
              [:span {:style {:fontStyle "italic" :paddingLeft "2em"}}
               (str "Supported file types: " (clojure.string/join ", " supported-file-types))]]
             [EntityTable {:workspace-id (:workspace-id props) :width :narrow
                           :attribute-renderer
                           (fn [data]
                             (if (and (string? data)
                                      (not (contains? (:tracks-set @state) data))
                                      (let [lc-data (clojure.string/lower-case data)]
                                        (some #(.endsWith lc-data %) supported-file-types)))
                               [:div {:style {:overflow "hidden" :textOverflow "ellipsis" :direction "rtl" :marginRight "0.5em"}}
                                (style/create-link {:text data
                                                    :onClick #(swap! state
                                                                     (fn [s] (-> s
                                                                                 (update-in [:tracks-set] conj data)
                                                                                 (update-in [:tracks-vec] conj data))))})]
                               data))}]]
            :right
            [:div {:style {:width "100%"}}
             [:div {:style {:margin "1em 0 0 1em"}}
              "Selected tracks"]
             (when (empty? (:tracks-set @state))
               [:div {:style {:margin "1em"}}
                "None--select a file on the left"])
             [:div {:style {:marginTop "1em" :overflowY "auto"}}
              (map-indexed
                (fn [index track]
                  [:div {:style {:display "flex" :alignItems "center" :padding 4}}
                   [:img {:src "assets/drag_temp.png"
                          :style {:flex "0 0 auto" :height 16 :cursor "ns-resize"}
                          :draggable false}]
                   [:div {:style {:flex "1 1 auto" :margin "0 8px"
                                  :whiteSpace "nowrap" :overflow "hidden" :textOverflow "ellipsis" :direction "rtl"}}
                    track]
                   (icons/font-icon {:style {:flex "0 0 auto" :color (:exception-red style/colors) :cursor "pointer"}
                                     :onClick #(swap! state
                                                      (fn [s] (-> s
                                                                  (update-in [:tracks-set] disj track)
                                                                  (update-in [:tracks-vec] utils/delete index))))}
                                    :x)])
                (:tracks-vec @state))]]
            :initial-slider-position 700}]])}])})


(react/defc Page
  {:refresh
   (fn [])
   :get-initial-state
   (fn []
     {:tracks []})
   :render
   (fn [{:keys [props state]}]
     [:div {}
      [:div {:style {:margin "1em"}}
       [comps/Button {:text "Select Tracks..."
                      :onClick
                      (fn [_]
                        (modal/push-modal
                          [TrackSelectionDialog
                           (assoc props
                             :tracks (:tracks @state)
                             :on-ok #(swap! state assoc :tracks (vec %)))]))}]]
      [igv/IGVContainer {:tracks (:tracks @state)}]])})
