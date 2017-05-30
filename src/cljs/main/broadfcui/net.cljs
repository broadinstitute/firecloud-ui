(ns broadfcui.net
  (:require
  [dmohs.react :as react]
  [broadfcui.utils :as utils]
  [broadfcui.common.style :as style]
  [broadfcui.common.components :as comps]
  ))

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
    (swap! state assoc state-key nil)
    (let [parsed-response (as-> xhr x
                                (aget x "responseText")
                                (try (js-invoke js/JSON "parse" x) (catch js/Error e e))
                                (when-not (instance? js/Error x)
                                  (js->clj x :keywordize-keys true)))]
     (cond
       (not parsed-response) (swap! state assoc-in [state-key :error] "Error parsing server response");;("assgn parse error to error")
       (not success?) (swap! state assoc-in [state-key :error] (:message parsed-response));;("assign error to error")
       :else (swap! state assoc-in [state-key :parsed] parsed-response))))

    ) ;;writing to passed key map of "response" for

(defn render-ajax [state state-key loading-text render-success error-overwrite]
  (cond
    (nil? (get-in state [state-key])) [comps/Spinner {:text loading-text}]
    (get-in state [state-key :error]) (style/create-server-error-message (if error-overwrite (error-overwrite state state-key) (get-in state [state-key :error])))
    :else (render-success))) ;;reading the passed key map to properly render state

(defn error-overwrite
  ([message] message)
  ([message code state state-key]
   (let [current-error (as-> (get-in state [state-key :error]) e
                             (js-invoke js/JSON "parse" e)
                             (js->clj e :keywordize-keys true))]
     (if (= code (:code current-error)) message
                                        (:message current-error)))))
