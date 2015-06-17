(ns org.broadinstitute.firecloud-ui.main
  (:require
   [dmohs.react :as react]
   ))


(def App
  (react/create-class
   {:render
    (fn []
      [:div {}
        "Hello World!"])}))


(defn ^:export render [element]
  (react/render (react/create-element App) element))
