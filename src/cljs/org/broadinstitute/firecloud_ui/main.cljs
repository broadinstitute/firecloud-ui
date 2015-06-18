(ns org.broadinstitute.firecloud-ui.main
  (:require
   [dmohs.react :as react]
   ))


(def App
  (react/create-class
   {:render
    (fn []
      [:div {:style {:padding "1em"}}
       [:div {} [:img {:src "broad_logo.png" :style {:height 36}}]]
        [:div {:style {:padding-top "1em"}} "Hello World!"]])}))


(defn ^:export render [element]
  (react/render (react/create-element App) element))
