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
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [title title-expand contents]} props
           {:keys [visible?]} @state]
       [:div (dissoc props :title :title-expand :contents :default-hidden?)
        [:div {:style {:display "flex"}}
         [:div {:style {:display "flex" :cursor "pointer" :alignItems "baseline"}
                :onClick #(swap! state update :visible? not)}
          title
          (icons/icon {:className "fa-fw" :style {:flexShrink 0 :marginLeft "0.25rem"}}
                      (if visible? :collapse :expand))]
         (when visible? title-expand)]
        [:div {:style {:display (when-not visible? "none")}}
         contents]]))})
