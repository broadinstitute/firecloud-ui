(ns broadfcui.net
  (:require
  [dmohs.react :as react]
  [broadfcui.utils :as utils]
  [broadfcui.common.style :as style]
  [broadfcui.common.components :as comps]))

(defn handle-ajax-state [[success? xhr state error-keyword parsed-keyword]]
  (let [r (as-> xhr x
                (aget x "responseText")
                (js-invoke js/JSON "parse" x)
                )]
    (if r
      (if success?
        (swap! state assoc (if parsed-keyword parsed-keyword :parsed-response) r)
        (swap! state assoc error-keyword (aget r "message")))
      (swap! state assoc error-keyword "Parsing Error"))
    r))

(defn render-ajax-state [[state error-keyword passed-keyword loading-message function-to-render & args]]
  (cond
    (error-keyword @state) (style/create-server-error-message (error-keyword @state))
    (passed-keyword @state) (swap! state assoc passed-keyword (function-to-render args))
    :else [comps/Spinner {:text loading-message}])
  )

(defn create-handle-ajax-response [state state-key]
  (fn [{:keys [xhr success?]}]
    (swap! state assoc state-key)
    (let [parsed-response (as-> xhr x
                  (aget x "responseText")
                  (js-invoke js/JSON "parse" x))]
     (cond
       (complement parsed-response) (swap! state assoc-in [state-key :parse-error] "Error in parsing response");;("assgn parse error to error")
       ((not success?)) (swap! state assoc-in [state-key :error] (aget parsed-response "message"));;("assign error to error")
       :else (swap! state assoc-in [state-key :parsed-response] parsed-response)))

    ) ;;writing to passed key map of "response" for

(defn render-ajax [state state-key render-success]
  (utils/log state-key)
  (cond
    (state-key state) [comps/Spinner {:text "Loading..."}]
    (get-in state [state-key :error]) (style/create-server-error-message ((get-in state [state-key :error])))
    :else (render-success)))) ;;reading the passed key map to properly render state
