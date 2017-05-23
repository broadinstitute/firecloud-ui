(ns broadfcui.net
  (:require
  [dmohs.react :as react]
  [broadfcui.utils :as utils]
  [broadfcui.common.style :as style]
  [broadfcui.common.components :as comps]))

(defn handle-ajax-state [[success? xhr state error-keyword]]
  (let [r (as-> xhr x
                (aget x "responseText")
                (js-invoke js/JSON "parse" x)
                )]
    (if r
      (if success?
        (swap! state assoc :parsed-response r)
        (swap! state assoc error-keyword (aget r "message")))
      (swap! state assoc error-keyword "Parsing Error"))
    r))

(defn render-ajax-state [[state error-keyword passed-keyword loading-message function-to-render]]
  (cond
    (error-keyword @state) (style/create-server-error-message (error-keyword @state))
    (passed-keyword @state) (swap! state assoc passed-keyword (function-to-render))
    :else [comps/Spinner {:text loading-message}])
  )
