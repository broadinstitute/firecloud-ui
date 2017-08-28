(ns broadfcui.common.markdown
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))

(def ^:private MarkdownIt-js (aget js/window "webpack-deps" "MarkdownIt"))
(def ^:private markdown-it (MarkdownIt-js. #js{:linkify true}))

(react/defc MarkdownView
  {:render
   (fn [{:keys [state]}]
     [:div {:className "markdown-body firecloud-markdown"
            :dangerouslySetInnerHTML #js{"__html" (:rendered-text @state)}}])
   :component-did-mount
   (fn [{:keys [props this]}]
     (this :refresh (:text props)))
   :component-will-receive-props
   (fn [{:keys [next-props this]}]
     (this :refresh (:text next-props)))
   :refresh
   (fn [{:keys [state]} text]
     (when text ; markdown-it doesn't like trying to render null text
       (swap! state assoc :rendered-text (.render markdown-it text))))})

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
          :side-by-side [comps/SplitPane
                         {:left text-area :overflow-left "initial"
                          :right [:div {:style {:marginLeft 2}} markdown-view]
                          :initial-slider-position (:or (:initial-slider-position props) 500)}])]))})
