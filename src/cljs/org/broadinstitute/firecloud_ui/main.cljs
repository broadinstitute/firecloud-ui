(ns org.broadinstitute.firecloud-ui.main
  (:require
   [dmohs.react :as react]
   ))

(defn footer []
  (let [thisyear (.getFullYear (js/Date.))
        startyear 2015
        yeartext (if (= startyear thisyear) (str startyear) (str startyear "-" thisyear))]
    [:div {:style {:padding-top "1em" :font-size 12}} (str "\u00A9 " yeartext " Broad Institute")]))

(def App
  (react/create-class
   {:render
    (fn []
      [:div {:style {:padding "1em"}}
        [:div {} [:img {:src "broad_logo.png" :style {:height 36}}]]
        [:div {:style {:padding-top "1em"}} "Hello World!"]
        (footer)])}))

(defn ^:export render [element]
  (react/render (react/create-element App) element))
