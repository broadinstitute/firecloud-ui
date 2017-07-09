(ns broadfcui.components.sticky
  (:require
    [dmohs.react :as react]
    [broadfcui.utils :as utils]
    ))

(react/defc Sticky
  {:render
   (fn [{:keys [props]}]
     (let [{:keys [outer-style anchor inner-style sticky-props contents]} props]
       [:div {:data-sticky-container "" :style outer-style}
        [:div (merge {:data-sticky "" :className "sticky" :data-anchor anchor
                      :style inner-style}
                     sticky-props)
         contents]]))
   :component-did-mount
   (fn [{:keys [this]}]
     (.foundation (js/$ (react/find-dom-node this)))
     (js* "$(window).trigger('load');"))})
