(ns broadfcui.page.method-repo.method.wdl
  (:require
   [dmohs.react :as react]
   [broadfcui.common.codemirror :refer [CodeMirror]]
   [broadfcui.components.pipeline-builder :refer [PipelineBuilder]]
   [broadfcui.common.style :as style]
   [broadfcui.components.split-pane :refer [SplitPane]]
   [broadfcui.utils :as utils]
   ))

(react/defc WDLViewer
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [mode]} @state
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
                          :initial-slider-position 550}])]))
   :refresh
   (fn [{:keys [state]}]
     (swap! state dissoc :mode))})
