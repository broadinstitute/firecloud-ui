(ns broadfcui.components.pipeline-builder
  (:require
   [dmohs.react :as react]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.components.script-loader :refer [ScriptLoader]]
   [broadfcui.utils :as utils]
   ))

(defonce ^:private pipeline-constructor (atom false))

; TODO: address transitive vulnerability before re-enabling
; epam/pipeline-builder 0.3.10-dev.264 depends on lodash 3.10.1
(react/defc PipelineBuilder
  {:component-will-receive-props
   (fn [{:keys [this next-props]}]
     (this :-render-pipeline (:wdl next-props)))
   :render
   (fn [{:keys [props state locals this]}]
     (let [{:keys [loaded? error connections-shown?]} @state
           {:keys [diagram]} @locals]
       [:div {:style {:height "100%" :position "relative"}
              :ref #(swap! locals assoc :wrapper %)}
        (when-not loaded?
          [ScriptLoader
           {:on-error #(swap! state assoc :error "Couldn't load WDL Pipeline Visualizer.")
            :on-load (fn []
                       (when-not @pipeline-constructor
                         (reset! pipeline-constructor (aget js/window "webpackDeps" "PipelineBuilder")))
                       (this :-render-pipeline (:wdl props))
                       (swap! state assoc :loaded? true))
            :path "pipeline-deps.bundle.js"}])
        (if error
          [:h3 {:style {:textAlign "center"}} error]
          [:div {:className (when (:read-only? props) "read-only")
                 :style {:display "flex" :border (when loaded? style/standard-line)}}
           (when loaded?
             (let [make-pipeline-button
                   (fn [on-click icon title & [active?]]
                     (style/add-hover-style
                      [:button {:title title
                                :className "button-reset"
                                :hover-style {:filter "brightness(0.9)"}
                                :style {:backgroundColor (:tag-background style/colors)
                                        :color (:text-light style/colors)
                                        ;; shadow from material design raised buttons
                                        :boxShadow "0 0 2px rgba(0,0,0,.12), 0 2px 2px rgba(0,0,0,.2)"
                                        :padding "0.25rem" :margin "0.25rem 0"
                                        :width "2.5rem" :height "2.5rem"
                                        :border (when active? (str "2px solid" (:text-light style/colors)))
                                        :borderRadius "50%"
                                        :textAlign "center"
                                        :cursor "pointer"}
                                :onClick on-click}
                       (icons/render-icon {:className "fa-lg"} icon)]))]
               [:div {:style {:display "flex" :flexDirection "column"
                              :padding "1rem 0.5rem" :borderRight style/standard-line
                              :backgroundColor (:background-light style/colors)}}
                (make-pipeline-button #(.zoom.zoomIn diagram) :zoom-in "Zoom In")
                (make-pipeline-button #(.zoom.zoomOut diagram) :zoom-out "Zoom Out")
                (make-pipeline-button #(do (.zoom.fitToPage diagram) (.zoom.zoomOut diagram)) :zoom-fit "Zoom to Fit")
                [:div {:style {:height "2rem"}}]
                (make-pipeline-button #(do (.togglePorts diagram true)
                                           (swap! state update :connections-shown? not))
                                      :show (str (if connections-shown? "Hide" "Show") " All Connections")
                                      connections-shown?)]))
           [:div {:data-test-id (:data-test-id props)
                  :ref #(swap! locals assoc :container %)
                  :style (when loaded? {:minHeight 300})}]])]))
   :-render-pipeline
   (fn [{:keys [locals props state]} wdl]
     (let [{:keys [read-only?]} props
           {:keys [container wrapper]} @locals
           Visualizer (.-Visualizer @pipeline-constructor)
           diagram (Visualizer. container read-only?)
           recalculate-size #(.setDimensions (.-paper diagram)
                                             (- (.-clientWidth wrapper) 2) (- (.-clientHeight wrapper) 2))]
       (swap! locals assoc :diagram diagram)
       (.then (.parse @pipeline-constructor wdl)
              (fn [res]
                (.attachTo diagram
                           (aget (.-model res) 0)))
              (fn [reason]
                (swap! state assoc :error (str "Couldn't visualize WDL: " reason))
                (.clear diagram)))
       (.addEventListener wrapper "onresize" recalculate-size)
       (recalculate-size)
       (when read-only?
         (.togglePanningMode diagram)
         (.disableSelection diagram))))})
