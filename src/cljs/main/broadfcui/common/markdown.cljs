(ns broadfcui.common.markdown
  (:require
    [dmohs.react :as react]
    [broadfcui.common.components :as comps]
    [broadfcui.common.style :as style]
    [broadfcui.utils :as utils]
    ))

;; Documentation:
;; https://github.com/chjj/marked

(def marked (aget js/window "webpack-deps" "marked"))

(def ^:private renderer-js (aget marked "Renderer"))
(defonce ^:private renderer (renderer-js.))

(set! (.-link renderer)
  (fn [href title text]
    ;; whitelist http/https to guard agaisnt XSS
    (if-not (re-matches #"^https?://.*" href)
      text
      (str "<a href='" (js/encodeURI href) "' title='" title "' target='_blank'>"
           text
           "</a>"))))

(js-invoke marked "setOptions"
           #js{:sanitize true :renderer renderer})

(react/defc MarkdownView
  {:render
   (fn []
     [:div {:ref "ref" :className "markdown-body firecloud-markdown"}])
   :component-did-mount
   (fn [{:keys [props this]}]
     (react/call :refresh this (:text props)))
   :component-will-receive-props
   (fn [{:keys [next-props this]}]
     (react/call :refresh this (:text next-props)))
   :refresh
   (fn [{:keys [refs]} text]
     (when text ; marked doesn't like trying to render null text
       (set! (.-innerHTML (@refs "ref"))
             (marked text))))})

(react/defc MarkdownEditor
  {:get-text
   (fn [{:keys [state]}]
     (:text @state))
   :get-initial-state
   (fn [{:keys [props]}]
     {:mode :edit
      :text (:initial-text props)})
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
          :preview markdown-view
          :side-by-side [comps/SplitPane
                         {:left text-area :overflow-left "initial"
                          :right [:div {:style {:marginLeft 2}} markdown-view]
                          :initial-slider-position (:or (:initial-slider-position props) 500)}])]))})
