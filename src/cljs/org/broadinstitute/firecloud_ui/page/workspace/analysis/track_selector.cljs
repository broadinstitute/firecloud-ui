(ns org.broadinstitute.firecloud-ui.page.workspace.analysis.track-selector
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.entity-table :refer [EntityTable]]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(def ^:private supported-file-types [".bam" ".vcf" ".bed"])


(react/defc Left
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:padding "1em"}}
      [:div {:style {:paddingBottom "1em"}}
       "Available data"
       [:span {:style {:fontStyle "italic" :paddingLeft "2em"}}
        (str "Supported file types: " (clojure.string/join ", " supported-file-types))]]
      [EntityTable {:workspace-id (:workspace-id props) :width :narrow
                    :attribute-renderer
                    (fn [data]
                      (if (and (string? data)
                               (not (contains? (:tracks-set props) data))
                               (let [lc-data (clojure.string/lower-case data)]
                                 (some #(.endsWith lc-data %) supported-file-types)))
                        [:div {:style {:overflow "hidden" :textOverflow "ellipsis" :direction "rtl" :marginRight "0.5em"}}
                         (style/create-link {:text data
                                             :onClick #((:on-select props) data)})]
                        data))}]])})

(react/defc Right
  {:render
   (fn [{:keys [props state refs]}]
     (let [{:keys [tracks-vec]} props]
       [:div {:style {:width "100%"}
              :onMouseMove (fn [e]
                             (when (:dragging? @state)
                               (let [y (.-clientY e)
                                     div-locs (map (fn [i]
                                                     {:index i
                                                      :midpoint
                                                      (let [rect (.getBoundingClientRect (@refs (str "track" i)))]
                                                        (/ (+ (.-top rect) (.-bottom rect)) 2))})
                                                   (range (count tracks-vec)))
                                     closest-div (apply min-key #(js/Math.abs (- y (:midpoint %))) div-locs)]
                                 (when-not (= (:drop-index @state) (:index closest-div))
                                   (swap! state assoc :drop-index (:index closest-div))))))
              :onMouseUp #(when (:dragging? @state)
                           ((:reorder props) (:drag-index @state) (:drop-index @state))
                           (common/restore-text-selection (:text-selection @state))
                           (swap! state dissoc :dragging? :drag-index :drop-index :text-selection))}
        [:div {:style {:margin "1em 0 0 1em"}}
         "Selected tracks"]
        (when (empty? tracks-vec)
          [:div {:style {:margin "1em"}}
           "None--select a file on the left"])
        [:div {:style {:marginTop "1em" :overflowY "auto"}}
         (map-indexed
           (fn [index track]
             (if (= track :dummy)
               [:div {:ref (str "track" index)
                      :style {:height 27 :marginRight 3 :border style/standard-line :borderRadius 4 :boxSizing "border-box"}}]
               [:div {:ref (str "track" index)
                      :style {:display "flex"
                              :alignItems "center" :padding 4}}
                (when (> (count tracks-vec) 1)
                  [:img {:src "assets/drag_temp.png"
                         :style {:flex "0 0 auto" :height 16 :cursor "ns-resize"}
                         :draggable false
                         :onMouseDown #(swap! state assoc :dragging? true :drag-index index :drop-index index
                                              :text-selection (common/disable-text-selection))}])
                [:div {:style {:flex "1 1 auto" :margin "0 8px"
                               :whiteSpace "nowrap" :overflow "hidden" :textOverflow "ellipsis" :direction "rtl"}}
                 track]
                (icons/font-icon {:style {:flex "0 0 auto" :color (:exception-red style/colors) :cursor "pointer"}
                                  :onClick #((:on-remove props) index track)}
                                 :x)]))
           (if (:dragging? @state)
             (-> tracks-vec
                 (utils/delete (:drag-index @state))
                 (utils/insert (:drop-index @state) :dummy))
             tracks-vec))]]))})


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
           {:left [Left {:workspace-id (:workspace-id props)
                         :tracks-set (:tracks-set @state)
                         :on-select (fn [track]
                                      (swap! state
                                             (fn [s] (-> s
                                                         (update-in [:tracks-set] conj track)
                                                         (update-in [:tracks-vec] conj track)))))}]
            :right [Right {:tracks-vec (:tracks-vec @state)
                           :reorder (fn [start-index end-index]
                                      (swap! state update-in [:tracks-vec] utils/move start-index end-index))
                           :on-remove (fn [index track]
                                        (swap! state
                                               (fn [s] (-> s
                                                           (update-in [:tracks-set] disj track)
                                                           (update-in [:tracks-vec] utils/delete index)))))}]
            :initial-slider-position 700}]])}])})
