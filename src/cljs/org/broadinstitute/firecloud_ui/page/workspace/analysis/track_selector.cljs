(ns org.broadinstitute.firecloud-ui.page.workspace.analysis.track-selector
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.entity-table :refer [EntityTable]]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.page.workspace.analysis.igv-utils :as igv-utils]
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
                               (.startsWith data "gs://")
                               (not-any? #(= data (:track-url %)) (:tracks props))
                               (let [lc-data (clojure.string/lower-case data)]
                                 (some #(.endsWith lc-data %) supported-file-types)))
                        (style/left-ellipses {:style {:marginRight "0.5em"}}
                                             (style/create-link {:text data
                                                                 :onClick #((:on-select props) data)}))
                        data))}]])})


(react/defc Right
  {:render
   (fn [{:keys [props state refs]}]
     (let [{:keys [tracks]} props]
       [:div {:style {:width "100%"}
              :onMouseMove (fn [e]
                             (when (:drag-index @state)
                               (let [y (.-clientY e)
                                     div-locs (map (fn [i]
                                                     {:index i
                                                      :midpoint
                                                      (let [rect (.getBoundingClientRect (@refs (str "track" i)))]
                                                        (/ (+ (.-top rect) (.-bottom rect)) 2))})
                                                   (range (count tracks)))
                                     closest-div-index (:index (apply min-key #(js/Math.abs (- y (:midpoint %))) div-locs))]
                                 (when-not (= (:drop-index @state) closest-div-index)
                                   (swap! state assoc :drop-index closest-div-index)))))}
        [:div {:style {:margin "1em 0 0 1em"}}
         "Selected tracks"]
        (when (empty? tracks)
          [:div {:style {:margin "1em" :textAlign "center"
                         :border style/standard-line :borderRadius 4 :padding "1em"}}
           "No tracks selected"])
        [:div {:style {:marginTop "1em" :overflowY "auto"}}
         (map-indexed
           (fn [index {:keys [track-url index-url requires-index?] :as track}]
             (if (= track :dummy)
               [:div {:ref (str "track" index)
                      :style {:display "flex" :alignItems "center" :justifyContent "center"
                              :height 46 :padding "0 4px" :margin "2px 3px"
                              :border (str "1px dotted " (:line-gray style/colors)) :borderRadius 4}}
                (style/left-ellipses {} (:drag-url @state))]
               [:div {:ref (str "track" index)
                      :style {:display "flex" :alignItems "center" :padding 4 :margin "2px 3px"
                              :border style/standard-line :borderRadius 4}}
                (when (> (count tracks) 1)
                  [:img {:src "assets/drag_temp.png"
                         :style {:flex "0 0 auto" :height 16 :cursor "ns-resize"}
                         :draggable false
                         :onMouseDown #(swap! state assoc :drag-index index :drop-index index :drag-url track-url
                                              :text-selection (common/disable-text-selection))}])
                (case @index-url
                  :pending [:div {} [comps/Spinner {:height "1em" :text "Searching for index file..."}]]
                  :error (if requires-index?
                           [:div {:style {:flex "1 1 auto" :marginLeft 8 :color (:exception-red style/colors) :overflow "hidden"}}
                            (style/right-ellipses {} (str "Unable to find index file for '" track-url "'"))
                            (style/right-ellipses {} "Please ensure you have the associated .bai or .bam.bai at the same path")]
                           [:div {:style {:flex "1 1 auto" :marginLeft 8 :overflow "hidden"}}
                            (style/left-ellipses {} track-url)
                            (style/right-ellipses {} "Optional index file not found.")])
                  [:div {:style {:flex "1 1 auto" :marginLeft 8 :overflow "hidden"}}
                   (style/left-ellipses {} track-url)
                   (style/left-ellipses {} @index-url)])
                [:div {:style {:flex "0 0 auto" :alignSelf "flex-start" :cursor "pointer"
                               :margin "-5px -5px 0 0" :padding "0 4px 1px 4px"}
                       :onClick #((:on-remove props) index)}
                 "×"]]))
           (if (:drag-index @state)
             (-> tracks
                 (utils/delete (:drag-index @state))
                 (utils/insert (:drop-index @state) :dummy))
             tracks))]]))
   :component-did-mount
   (fn [{:keys [props state locals]}]
     (let [mouse-up #(when (:drag-index @state)
                      ((:reorder props) (:drag-index @state) (:drop-index @state))
                      (common/restore-text-selection (:text-selection @state))
                      (swap! state dissoc :drag-index :drop-index :drag-url :text-selection))]
       (swap! locals assoc :mouse-up mouse-up)
       (.addEventListener js/window "mouseup" mouse-up)))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (.removeEventListener js/window "mouseup" (:mouse-up @locals)))})


(react/defc TrackSelectionDialog
  {:get-initial-state
   (fn [{:keys [props]}]
     {:tracks (vec (:tracks props))})
   :render
   (fn [{:keys [props state]}]
     [modal/OKCancelForm
      {:header "Select IGV Tracks"
       :ok-button {:text "Load"
                   :onClick
                   (fn [_]
                     (if (->> (:tracks @state)
                              (filter :requires-index?)
                              (keep :index-url)
                              (map deref)
                              (some keyword?))
                       (swap! state assoc :index-error true)
                       (do ((:on-ok props) (:tracks @state))
                         (modal/pop-modal))))}
       :content
       (react/create-element
         [:div {:style {:width "80vw"}}
          [:div {:style {:background "white" :border style/standard-line}}
           [comps/SplitPane
            {:left [Left {:workspace-id (:workspace-id props)
                          :tracks (:tracks @state)
                          :on-select (fn [track-url]
                                       (let [index-atom (atom :pending)]
                                         (swap! state assoc :loading? true)
                                         (add-watch index-atom :loader #(swap! state dissoc :loading?))
                                         (igv-utils/find-index-file {:track-url track-url
                                                                     :on-success #(reset! index-atom %)
                                                                     :on-error #(reset! index-atom :error)})
                                         (swap! state update-in [:tracks] conj
                                                {:track-url track-url :index-url index-atom
                                                 :requires-index? (.endsWith track-url ".bam")})))}]
             :right [Right {:tracks (:tracks @state)
                            :reorder (fn [start-index end-index]
                                       (swap! state update-in [:tracks] utils/move start-index end-index))
                            :on-remove #(swap! state update-in [:tracks] utils/delete %)}]
             :initial-slider-position 700}]]
          (when (:index-error @state)
            [:div {:style {:textAlign "center" :color (:exception-red style/colors) :marginTop "1em"}}
             "All .bam tracks must have associated index (.bai) files."])])}])})
