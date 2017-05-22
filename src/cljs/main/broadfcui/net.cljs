(ns broadfcui.net
  (:require
  [dmohs.react :as react]
  [broadfcui.utils :as utils]
  [broadfcui.common.components :as comps]))

(defn handle-ajax-state [[success? xhr state]]
  (let [r (as-> xhr x
                (aget x "responseText")
                (js-invoke js/JSON "parse" x)
                )]
    (if r
      (if success?
        (swap! state assoc :parsed-response r)
        (swap! state assoc :error (aget r "message")))
      (swap! state assoc :parse-error "Parsing Error"))
    r))

(defn render-ajax-state [[state passed-keyword function-to-render]]
  (cond
    (:error @state) (swap! state assoc passed-keyword (:error @state))
    (:parsed-response @state) (swap! state assoc passed-keyword (function-to-render))
    :else [comps/Spinner {:text "Loading..."}])
  (utils/log state))
