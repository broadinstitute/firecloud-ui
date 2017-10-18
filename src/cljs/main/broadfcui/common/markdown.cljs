(ns broadfcui.common.markdown
  (:require
   [dmohs.react :as react]
   [broadfcui.common.style :as style]
   [broadfcui.components.script-loader :refer [ScriptLoader]]
   [broadfcui.components.split-pane :refer [SplitPane]]
   [broadfcui.utils :as utils]))

(def ^:private MarkdownIt-js (aget js/window "webpackDeps" "MarkdownIt"))
(def ^:private markdown-it (MarkdownIt-js. #js{:linkify true}))

(react/defc- MarkdownViewElement
  {:render
   (fn [{:keys [props]}]
     [:div {:className "markdown-body firecloud-markdown"
            :dangerouslySetInnerHTML #js{"__html" (.render markdown-it (or (:text props) ""))}}])})

(react/defc MarkdownView
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [loaded? error?]} @state]
       (cond
         error? [:p {} (:text props)]
         loaded? [MarkdownViewElement props]
         :else
         [ScriptLoader
          {:on-error #(swap! state assoc :error? true)
           :on-load #(swap! state assoc :loaded? true)
           :path "markdown-deps.bundle.js"}])))})

(react/defc MarkdownEditor
  {:get-text
   (fn [{:keys [state]}]
     (:text @state))
   :get-initial-state
   (fn [{:keys [props]}]
     {:mode :edit
      :text (or (:initial-text props) "")})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [mode text]} @state
           tab (fn [mode-key label]
                 (let [selected? (= mode-key mode)]
                   [:span {:style {:border style/standard-line :padding "3px 8px" :cursor "pointer"
                                   :color (when selected? "white")
                                   :backgroundColor ((if selected? :button-primary :background-light) style/colors)}
                           :onClick #(swap! state assoc :mode mode-key)}
                    label]))
           markdown-view [MarkdownView {:text text}]
           text-area (style/create-text-area {:value (:text @state)
                                              :onChange #(swap! state assoc :text (-> % .-target .-value))
                                              :style {:width "100%" :height "100%" :minHeight 200
                                                      :resize (if (= mode :edit) "vertical" "none")}})]
       [:div {}
        [:div {}
         (tab :edit "Edit")
         (tab :preview "Preview")
         (tab :side-by-side "Side-by-side")]
        (case mode
          :edit text-area
          :preview [:div {:style {:paddingTop "0.5rem"}} markdown-view]
          :side-by-side [SplitPane
                         {:left text-area :overflow-left "initial"
                          :right [:div {:style {:marginLeft 2}} markdown-view]
                          :initial-slider-position (:or (:initial-slider-position props) 500)}])]))})
