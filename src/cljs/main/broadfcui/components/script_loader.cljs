(ns broadfcui.components.script-loader
  (:require
   [dmohs.react :as react]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.utils :as utils]
   ))

(def ^:private ReactLoadScript (aget js/window "webpackDeps" "ReactLoadScript"))

(react/defc ScriptLoader
  "Loads an external js asset.

  :on-create (optional) - spinner shown by default
  :on-error
  :on-load
  :path
  :attributes (optional) - additional html attributes to be placed on the script tag
  :allow-cache? (optional) - pass true to avoid reloading on every new build"
  {:render
   (fn [{:keys [props state after-update]}]
     (let [{:keys [on-create on-error on-load path attributes allow-cache?]} props
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
                      (after-update on-load)) ; after-update ensures that parent's children are rendered
            :url (str path (when-not allow-cache? (str "?t=" utils/build-timestamp)))
            :attributes attributes}))]
        (when (and (not on-create) (not loaded?))
          (spinner))]))})
