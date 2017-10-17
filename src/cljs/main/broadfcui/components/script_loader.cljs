(ns broadfcui.components.script-loader
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.utils :as utils]
   ))

(def ^:private ReactLoadScript (aget js/window "webpackDeps" "ReactLoadScript"))

(react/defc ScriptLoader
  "Loads an external js asset.

  :on-create (optional) - spinner shown by default
  :on-error
  :on-load
  :path
  :attributes (optional) - additional html attributes to be placed on the script tag"
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [on-create on-error on-load path attributes]} props
           {:keys [loaded?]} @state]
       [:div {}
        [ReactLoadScript
         (clj->js
          (merge
           (when on-create
             {:onCreate on-create})
           {:onError on-error
            :onLoad (fn []
                      (when-not on-create
                        (swap! state assoc :loaded? true))
                      (on-load))
            :url path
            :attributes attributes}))]
        (when (and (not on-create) (not loaded?))
          [comps/Spinner])]))})
