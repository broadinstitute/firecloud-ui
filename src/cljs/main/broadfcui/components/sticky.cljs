(ns broadfcui.components.sticky
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.utils :as utils]
   ))

(react/defc Sticky
  {:render
   (fn [{:keys [this props]}]
     (let [{:keys [outer-style anchor inner-style sticky-props contents]} props]
       [:div {:data-sticky-container "" :style outer-style}
        [:div (merge {:data-sticky "" :className "sticky" :data-anchor anchor
                      :style inner-style}
                     sticky-props
                     {:ref (react/method this :-sticky-ref-handler)})
         contents]]))
   :-sticky-ref-handler
   (common/create-element-ref-handler-method
    {:did-mount
     (fn [_ el]
       (.foundation (js/$ (react/find-dom-node el)))
       (js* "$(window).trigger('load');"))
     :will-unmount
     (fn [_ el]
       (.foundation (js/$ (react/find-dom-node el)) "destroy"))})})
