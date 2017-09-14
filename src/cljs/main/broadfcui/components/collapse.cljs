(ns broadfcui.components.collapse
  (:require
   [dmohs.react :as react]
   [broadfcui.common.icons :as icons]
   [broadfcui.utils :as utils]
   ))

(react/defc Collapse
  {:get-initial-state
   (fn [{:keys [props]}]
     {:visible? (not (:default-hidden? props))})
   :get-default-props
   (fn [] {:show-title-when-expanded? true})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [title title-expand show-title-when-expanded? contents]} props
           {:keys [visible?]} @state]
       [:div (dissoc props :title :title-expand :show-title-when-expanded? :contents :default-hidden?)
        [:div {:style {:display "flex"}}
         [:div {:style {:display "flex" :cursor "pointer" :alignItems "baseline"}
                :onClick #(swap! state update :visible? not)}
          (icons/icon {:className "fa-fw" :style {:flexShrink 0}}
                      (if visible? :disclosure-opened :disclosure-closed))
          (when (or show-title-when-expanded? (not visible?)) title)]
         (when visible? title-expand)]
        [:div {:style {:display (when-not visible? "none")
                       :marginLeft "1.25rem"}} ; to match the disclosure triangle
         contents]]))})
