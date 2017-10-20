(ns broadfcui.components.split-pane
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))


(react/defc SplitPane
  (->>
   {:get-default-props
    (fn []
      {:overflow-left "auto"})
    :get-initial-state
    (fn [{:keys [props]}]
      {:slider-position (or (:initial-slider-position props) 100)})
    :render
    (fn [{:keys [props state]}]
      (let [{:keys [left right top bottom slider-padding]} props]
        (assert (or (and left right) (and top bottom)) "Either specify left/right or top/bottom for SplitPane")
        [:div {:style {:display "flex" :flexDirection (if left "row" "column")
                       :maxHeight "-webkit-fill-available"}}
         [:div {:style {:flexGrow 0 :flexShrink 0 :flexBasis (:slider-position @state) :overflow (:overflow-left props)}}
          (or left top)]
         [:div {:style {:flex "0 0 10px"
                        :display "flex" :flexDirection (if left "column" "row") :justifyContent "center"
                        :backgroundColor (:background-light style/colors)
                        :margin (if top (str slider-padding " 0px") (str "0px " slider-padding))
                        :cursor (if left "ew-resize" "ns-resize")}
                :onMouseDown (fn [e]
                               (swap! state assoc
                                      :dragging? true
                                      :mouse-pos (if left (.-clientX e) (.-clientY e))
                                      :text-selection (common/disable-text-selection)))}
          [:div {:style {:display "flex" :flexDirection (if left "row" "column") :justifyContent "space-between"}}
           (icons/render-icon {:className (when left "fa-rotate-90")
                               :style {:padding slider-padding
                                       :color (:text-lightest style/colors)}} :resize)]]
         [:div {:style {:flex "1 0 0" :overflow "auto"}}
          (or right bottom)]]))}
   (utils/with-window-listeners
    {"mouseup"
     (fn [{:keys [state]}]
       (when (:dragging? @state)
         (common/restore-text-selection (:text-selection @state))
         (swap! state dissoc :dragging? :text-selection)))
     "mousemove"
     (fn [{:keys [props state]} e]
       (when (:dragging? @state)
         (let [start-pos (:mouse-pos @state)
               pos (if (:left props) (.-clientX e) (.-clientY e))]
           (when-not (= start-pos pos)
             (swap! state assoc
                    :slider-position (+ (:slider-position @state) (- pos start-pos))
                    :mouse-pos pos)))))})))
