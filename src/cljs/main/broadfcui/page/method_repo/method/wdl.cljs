(ns broadfcui.page.method-repo.method.wdl
  (:require
   [dmohs.react :as react]
   [broadfcui.common.codemirror :refer [CodeMirror]]
   [broadfcui.common.style :as style]
   ; TODO: address transitive vulnerability before re-enabling
   ; epam/pipeline-builder 0.3.10-dev.264 depends on lodash 3.10.1
   ;[broadfcui.components.pipeline-builder :refer [PipelineBuilder]]
   [broadfcui.components.split-pane :refer [SplitPane]]
   [broadfcui.utils :as utils]
   ))

(react/defc WDLViewer
  {:render
   (fn [{:keys [props #_state]}] ; TODO: uncomment state when re-enabling
     [CodeMirror {:text (:wdl props) :read-only? true}] ; TODO: remove this line when re-enabling
     #_(let [{:keys [mode]} @state ; TODO: un-comment this when re-enabling
           {:keys [wdl]} props
           mode (or mode :code)
           tab (fn [mode-key label]
                 (let [selected? (= mode-key mode)]
                   [:div {:style {:display "inline-block"
                                  :border style/standard-line :padding "3px 8px" :cursor "pointer"
                                  :color (when selected? "white")
                                  :backgroundColor ((if selected? :button-primary :background-light) style/colors)}
                          :onClick #(swap! state assoc :mode mode-key)}
                    label]))
           pipeline-view [PipelineBuilder {:wdl wdl :read-only? true?}]
           code-mirror [CodeMirror {:text wdl :read-only? true}]]
       [:div {:style {:margin "2.5rem 1.5rem 1rem"}}
        [:div {}
         (tab :code "Code")
         (tab :preview "Preview")
         (tab :side-by-side "Side-by-side")]
        (case mode
          :code code-mirror
          :preview [:div {:style {:height 500}} pipeline-view]
          :side-by-side [SplitPane
                         {:left code-mirror :right pipeline-view
                          :height 500 :initial-slider-position 550}])]))
   :refresh
   (fn [{:keys [state]}]
     (swap! state dissoc :mode))})
