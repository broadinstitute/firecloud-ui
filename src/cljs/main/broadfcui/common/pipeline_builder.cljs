(ns broadfcui.common.pipeline-builder
  (:require
   [dmohs.react :as react]
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
     (let [{:keys [loaded? error?]} @state]
       [:div {:ref #(swap! locals assoc :wrapper %)}
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
        [:div {:data-test-id (:data-test-id props)
               :ref #(swap! locals assoc :container %)
               :style (when loaded? {:border style/standard-line :min-height 500})}]]))
   :-render-pipeline
   (fn [{:keys [locals props]} wdl]
     (let [{:keys [read-only?]} props
           {:keys [container wrapper]} @locals
           Visualizer (.-Visualizer @pipeline-constructor)
           diagram (Visualizer. container read-only?)
           recalculate-size #(.setDimensions (.-paper diagram)
                                             (- (.-clientWidth wrapper) 2) (- (.-clientHeight wrapper) 2))]
       (.then (.parse @pipeline-constructor wdl)
              (fn [res]
                (.attachTo diagram
                           (aget (.-model res) 0))))
       (.addEventListener wrapper "onresize" recalculate-size)
       (recalculate-size)
       (when read-only?
         (.togglePanningMode diagram)
         (.disableSelection diagram))))})
