(ns org.broadinstitute.firecloud-ui.common.overlay
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

(react/defc Overlay
  {:get-default-props
   (fn []
     {:cycle-focus? false
      :anchor-side :left})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [content width dismiss-self get-anchor-dom-node]} props
           anchored? (not (nil? get-anchor-dom-node))]
       (assert (react/valid-element? content)
         (subs (str "Not a react element: " content) 0 200))
       (when (or (not anchored?) (:position @state))
         [:div {:style {:backgroundColor "rgba(210, 210, 210, 0.4)"
                        :position "absolute" :zIndex 8888
                        :top 0 :left 0 :right 0 :height (.. js/document -body -offsetHeight)}
                :onKeyDown (common/create-key-handler [:esc] dismiss-self)
                :onClick dismiss-self}
          [:div {:style (if anchored?
                          {:position "absolute" :backgroundColor "#fff"
                           :top (get-in @state [:position :top])
                           :left (when (= (:anchor-side props) :left) (get-in @state [:position :left]))
                           :right (when (= (:anchor-side props) :right) (get-in @state [:position :right]))}
                          {:transform "translate(-50%, 0px)" :backgroundColor "#fff"
                           :position "relative" :marginBottom 60
                           :top 60 :left "50%" :width width})
                 :onClick #(.stopPropagation %)}
           content]])))
   :component-did-mount
   (fn [{:keys [props state locals]}]
     (when-let [get-dom-node (:get-anchor-dom-node props)]
       (let [rect (.getBoundingClientRect (get-dom-node))]
         (swap! state assoc :position {:top (+ (.-top rect) (.. js/document -body -scrollTop))
                                       :left (+ (.-left rect) (.. js/document -body -scrollLeft))
                                       :right (- (.. js/document -body -clientWidth) (.-right rect))})))
     (when-let [get-first (:get-first-element-dom-node props)]
       (common/focus-and-select (get-first))
       (when-let [get-last (:get-last-element-dom-node props)]
         (.addEventListener (get-first) "keydown" (common/create-key-handler [:tab] #(.-shiftKey %)
                                                    (fn [e] (.preventDefault e)
                                                      (when (:cycle-focus? props)
                                                        (.focus (get-last))))))
         (.addEventListener (get-last) "keydown" (common/create-key-handler [:tab] #(not (.-shiftKey %))
                                                   (fn [e] (.preventDefault e)
                                                     (when (:cycle-focus? props)
                                                       (.focus (get-first))))))))
     (swap! locals assoc :onKeyDownHandler
       (common/create-key-handler [:esc] (:dismiss-self props)))
     (.addEventListener js/window "keydown" (:onKeyDownHandler @locals)))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (.removeEventListener js/window "keydown" (:onKeyDownHandler @locals)))})
