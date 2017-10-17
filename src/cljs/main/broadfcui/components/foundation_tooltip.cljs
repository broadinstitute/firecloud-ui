(ns broadfcui.components.foundation-tooltip
  (:require
   [dmohs.react :as react]
   ))


(react/defc FoundationTooltip
  {:component-did-mount
   (fn [{:keys [this]}]
     (.foundation (js/$ (react/find-dom-node this))))
   :render
   (fn [{:keys [props]}]
     (let [{:keys [position text tooltip]} props]
       ;; empty string makes react attach a property with no value
       [:span (merge
               {:data-tooltip "" :className (str "has-tip " position) :title tooltip}
               (dissoc props :position :text :tooltip))
        text]))})
