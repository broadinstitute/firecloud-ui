(ns org.broadinstitute.firecloud-ui.page.workspace.analysis.track-selector
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.entity-table :refer [EntityTable]]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.input :as input]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.page.workspace.analysis.igv-utils :as igv-utils]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


; TODO: support .vcf and .bed
;(def ^:private supported-file-types [".bam" ".vcf" ".bed"])
(def ^:private supported-file-types [".bam"])

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
                               (not-any? #(= data (:track-url %)) (:tracks props))
                               (let [lc-data (clojure.string/lower-case data)]
                                 (some #(.endsWith lc-data %) supported-file-types)))
                        (style/left-ellipses {:style {:marginRight "0.5em"}}
                                             (style/create-link {:text data
                                                                 :onClick #((:on-select props) data)}))
                        data))}]])})


(react/defc SpecifyIndexDialog
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [track]} props]
       [modal/OKCancelForm
        {:header "Specify index file"
         :ok-button #(react/call :ok this)
         :content
         (react/create-element
           [:div {}
            (style/create-form-label ".bam file:")
            [:div {:style {:maxWidth 800 :marginBottom "0.667em"}}
             (:track-url track)]
            (style/create-form-label ".bai file:")
            [input/TextField {:ref "input"
                              :style {:width 800 :marginTop "0.5em"}
                              :predicates [(input/nonempty "Index file")
                                           {:test #(.endsWith (clojure.string/lower-case %) ".bai")
                                            :message "Index must be a .bai file"}]}]
            (style/create-validation-error-message (:validation-errors @state))])}]))
   :ok
   (fn [{:keys [props state refs]}]
     (let [[index-file & fails] (input/get-and-validate refs "input")]
       (swap! state assoc :validation-errors fails)
       (when-not fails
         (reset! (:index-url (:track props)) index-file)
         (modal/pop-modal))))})


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
          [:div {:style {:margin "1em"}}
           "None--select a file on the left"])
        [:div {:style {:marginTop "1em" :overflowY "auto"}}
         (map-indexed
           (fn [index track]
             (if (= track :dummy)
               [:div {:ref (str "track" index)
                      :style {:display "flex" :alignItems "center"
                              :height 27 :marginRight 3 :border style/standard-line :borderRadius 4 :boxSizing "border-box"}}
                (style/left-ellipses {:style {:margin "0 4px"}} (:drag-url @state))]
               [:div {:ref (str "track" index)
                      :style {:display "flex"
                              :alignItems "center" :padding 4}}
                (when (> (count tracks) 1)
                  [:img {:src "assets/drag_temp.png"
                         :style {:flex "0 0 auto" :height 16 :cursor "ns-resize"}
                         :draggable false
                         :onMouseDown #(swap! state assoc :drag-index index :drop-index index :drag-url (:track-url track)
                                              :text-selection (common/disable-text-selection))}])
                (case @(:index-url track)
                  :pending [comps/Spinner {:height "1em" :text "Searching for index file..."}]
                  :error (style/right-ellipses {:style {:flex "1 1 auto" :margin "0 8px"
                                                        :color (:exception-red style/colors) :cursor "pointer"}
                                                :onClick #(modal/push-modal [SpecifyIndexDialog {:track track}])}
                                               "Could not find index file.  Click to specify.")
                  (style/left-ellipses {:style {:flex "1 1 auto" :margin "0 8px"}}
                                       (:track-url track)))
                (icons/font-icon {:style {:flex "0 0 auto" :color (:exception-red style/colors) :cursor "pointer"}
                                  :onClick #((:on-remove props) index)}
                                 :x)]))
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
                   #(if (->> (:tracks @state)
                             (map :index-url)
                             (map deref)
                             (some keyword?))
                     (swap! state assoc :index-error true)
                     (do ((:on-ok props) (:tracks @state))
                         (modal/pop-modal)))}
       :content
       (react/create-element
         [:div {:style {:width "80vw"}}
          [:div {:style {:background "white" :border style/standard-line}}
           [comps/SplitPane
            {:left [Left {:workspace-id (:workspace-id props)
                          :tracks (:tracks @state)
                          :on-select (fn [track-url]
                                       (swap! state assoc :loading? true)
                                       (let [index-atom (atom :pending)]
                                         (add-watch index-atom :loader #(swap! state dissoc :loading?))
                                         (igv-utils/find-index {:track-url track-url
                                                                :on-success #(reset! index-atom %)
                                                                :on-error #(reset! index-atom :error)})
                                         (swap! state update-in [:tracks] conj {:track-url track-url :index-url index-atom})))}]
             :right [Right {:tracks (:tracks @state)
                            :reorder (fn [start-index end-index]
                                       (swap! state update-in [:tracks] utils/move start-index end-index))
                            :on-remove #(swap! state update-in [:tracks] utils/delete %)}]
             :initial-slider-position 700}]]
          (when (:index-error @state)
            [:div {:style {:textAlign "center" :color (:exception-red style/colors) :marginTop "1em"}}
             "All selected tracks must have associated index files.  Either manually specify them or remove them from the list."])])}])})
