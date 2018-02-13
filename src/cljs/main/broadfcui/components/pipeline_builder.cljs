(ns broadfcui.components.pipeline-builder
  (:require
   [dmohs.react :as react]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.components.script-loader :refer [ScriptLoader]]
   [broadfcui.utils :as utils]
   ))

(defonce ^:private pipeline-constructor (atom false))

(react/defc PipelineBuilder
  {:component-will-receive-props
   (fn [{:keys [this next-props]}]
     (this :-render-pipeline (:wdl next-props)))
   :render
   (fn [{:keys [props state locals this]}]
     (let [{:keys [loaded? error?]} @state
           {:keys [diagram]} @locals]
       [:div {:style {:height "100%"}
              :ref #(swap! locals assoc :wrapper %)}
        (cond
          error? [:h2 {} "Couldn't load visualizer."]
          (not loaded?) [ScriptLoader
                         {:on-error #(swap! state assoc :error? true)
                          :on-load (fn []
                                     (when-not @pipeline-constructor
                                       (reset! pipeline-constructor (aget js/window "webpackDeps" "PipelineBuilder")))
                                     (this :-render-pipeline (:wdl props))
                                     (swap! state assoc :loaded? true))
                          :path "pipeline-deps.bundle.js"}])
        [:div {}
         [:div {:data-test-id (:data-test-id props)
                :ref #(swap! locals assoc :container %)
                :style (when loaded? {:border style/standard-line :minHeight 300})}]
         (let [make-zoom-button (fn [on-click icon]
                                  [:button {:className "button-reset"
                                            :style {:backgroundColor (:tag-background style/colors)
                                                    :padding "0.25rem" :margin "0.25rem 0"
                                                    :width "2.5rem" :height "2.5rem"
                                                    :borderRadius "50%"
                                                    :textAlign "center"
                                                    :cursor "pointer"}
                                            :onClick on-click}
                                   (icons/render-icon {:className "fa-lg"
                                                       :style {:color (:text-light style/colors)}}
                                                      icon)])]
           [:div {:style {:position "absolute"
                          :top "1rem" :left "1rem"
                          :display "flex" :flexDirection "column"}}
            (make-zoom-button #(.zoom.zoomIn diagram) :zoom-in)
            (make-zoom-button #(.zoom.zoomOut diagram) :zoom-out)
            (make-zoom-button #(.zoom.fitToPage diagram) :zoom-fit)])]]))
   :-render-pipeline
   (fn [{:keys [locals props]} wdl]
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
                           (aget (.-model res) 0))))
       (.addEventListener wrapper "onresize" recalculate-size)
       (recalculate-size)
       (when read-only?
         ;(.togglePanningMode diagram)
         (.disableSelection diagram))))})
