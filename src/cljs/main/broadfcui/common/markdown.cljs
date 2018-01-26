(ns broadfcui.common.markdown
  (:require
   [dmohs.react :as react]
   [broadfcui.common.style :as style]
   [broadfcui.components.script-loader :refer [ScriptLoader]]
   [broadfcui.components.split-pane :refer [SplitPane]]
   [broadfcui.test-utils :as test-utils]
   [broadfcui.utils :as utils]
   ))

(defonce ^:private markdown-instance (atom false))

(react/defc MarkdownView
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [loaded? error?]} @state]
       (cond
         error? [:p {} (:text props)]
         loaded? [:div {:className "markdown-body"
                        :dangerouslySetInnerHTML #js{"__html"
                                                     (if-let [text (:text props)]
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
   :get-initial-state
   (fn [{:keys [props this]}]
     (merge
      {:mode :edit}
      (when-not (this :-controlled?)
        {:text (or (:initial-text props) "")})))
   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [mode]} @state
           tab (fn [mode-key label]
                 (let [selected? (= mode-key mode)]
                   [:div {:data-test-id (test-utils/text->test-id label "tab")
                          :style {:display "inline-block"
                                  :border style/standard-line :padding "3px 8px" :cursor "pointer"
                                  :color (when selected? "white")
                                  :backgroundColor ((if selected? :button-primary :background-light) style/colors)}
                          :onClick #(swap! state assoc :mode mode-key)}
                    label]))
           controlled? (this :-controlled?)
           text (if controlled? (:value props) (:text @state))
           markdown-view [MarkdownView {:text text}]
           text-area (style/create-text-area {:data-test-id "markdown-editor-text-area"
                                              :value text
                                              :onChange #(let [new-value (-> % .-target .-value)]
                                                           (if controlled?
                                                             ((:on-change props) new-value)
                                                             (swap! state assoc :text new-value)))
                                              :style {:width "100%" :height "100%" :minHeight 200
                                                      :resize (if (= mode :edit) "vertical" "none")}})]
       [:div {:data-test-id (:data-test-id props)}
        [:div {:style {:marginTop "0.5rem"}}
         (tab :edit "Edit")
         (tab :preview "Preview")
         (tab :side-by-side "Side-by-side")]
        (case mode
          :edit text-area
          :preview [:div {:style {:paddingTop "0.5rem"}} markdown-view]
          :side-by-side [SplitPane
                         {:left text-area :overflow-left "initial"
                          :right [:div {:style {:marginLeft 2}} markdown-view]
                          :initial-slider-position (or (:initial-slider-position props) 500)}])]))
   :-controlled?
   (fn [{:keys [props]}]
     (contains? props :value))})
