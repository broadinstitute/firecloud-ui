(ns broadfcui.components.blocker
  (:require
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.utils :as utils]
   ))


(defn blocker [text]
  (when text
    [:div {:style {:backgroundColor "rgba(210, 210, 210, 0.4)"
                   :position "absolute" :top 0 :bottom 0 :right 0 :left 0 :zIndex 9999
                   :display "flex" :justifyContent "center" :alignItems "center"}}
     [:div {:style {:backgroundColor "#fff" :padding "2em"}}
      (spinner text)]]))
