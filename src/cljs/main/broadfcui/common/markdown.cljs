(ns broadfcui.common.markdown
  (:require
   [dmohs.react :as react]
   [broadfcui.common.style :as style]
   [broadfcui.components.script-loader :refer [ScriptLoader]]
   [broadfcui.components.split-pane :refer [SplitPane]]
   [broadfcui.utils :as utils]
   [broadfcui.utils.test :as test-utils]
   ))

(defonce ^:private markdown-instance (atom false))

(react/defc MarkdownView
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [text style]} props
           {:keys [loaded? error?]} @state]
       (cond
         error? [:p {} text]
         loaded? [:div {:className "markdown-body"
                        :style style
                        :dangerouslySetInnerHTML #js{"__html"
                                                     (if text
                                                       (.render @markdown-instance text)
                                                       "")}}]
         :else
         [ScriptLoader
          {:on-error #(swap! state assoc :error? true)
           :on-load (fn []
                      (when-not @markdown-instance
                        (let [markdown-it (aget js/window "webpackDeps" "MarkdownIt")]
                          (reset! markdown-instance (markdown-it. #js{:linkify true}))))
                      (swap! state assoc :loaded? true))
           :path "markdown-deps.bundle.js"}])))})

(react/defc MarkdownEditor
  {:get-trimmed-text
   (fn [{:keys [state this]}]
     (assert (not (this :-controlled?)) "Trying to read text from a controlled component")
     (:text @state))
   :set-text
   (fn [{:keys [state this]} text]
     (assert (not (this :-controlled?)) "Trying to set text on a controlled component")
     (swap! state assoc :text text))
   :get-initial-state
   (fn [{:keys [props this]}]
     (merge
      {:mode :edit}
      (when-not (this :-controlled?)
        {:text (or (:initial-text props) "")})))
   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [data-test-id value on-change toolbar-style toolbar-items initial-slider-position]} props
           {:keys [text mode]} @state
           tab (fn [mode-key label]
                 (let [selected? (= mode-key mode)]
                   [:div {:data-test-id (test-utils/text->test-id label "tab")
                          :style {:border style/standard-line :padding "3px 8px" :cursor "pointer"
                                  :color (when selected? "white")
                                  :backgroundColor ((if selected? :button-primary :background-light) style/colors)}
                          :onClick #(swap! state assoc :mode mode-key)}
                    label]))
           controlled? (this :-controlled?)
           text (if controlled? value text)
           markdown-view [MarkdownView {:text text
                                        :style {:display "table"
                                                :backgroundColor "white"
                                                :padding "5px 8px"
                                                :border style/standard-line :borderRadius 2
                                                :height "100%" :width "100%" :boxSizing "border-box"}}]
           text-area (style/create-text-area {:data-test-id "markdown-editor-text-area"
                                              :value text
                                              :onChange #(let [new-value (-> % .-target .-value)]
                                                           (if controlled?
                                                             (on-change new-value)
                                                             (swap! state assoc :text new-value)))
                                              :style {:width "100%" :height "100%" :minHeight 200
                                                      :resize (if (= mode :edit) "vertical" "none")}})]
       [:div {:data-test-id data-test-id}
        [:div {:style (merge {:display "flex" :alignItems "baseline" :marginTop "0.5rem"}
                             toolbar-style)}
         (tab :edit "Edit")
         (tab :preview "Preview")
         (tab :side-by-side "Side-by-side")
         (list* toolbar-items)]
        (case mode
          :edit text-area
          :preview markdown-view
          :side-by-side [SplitPane
                         {:left text-area :overflow-left "initial"
                          :right markdown-view
                          :initial-slider-position (or initial-slider-position 500)}])]))
   :-controlled?
   (fn [{:keys [props]}]
     (contains? props :value))})
