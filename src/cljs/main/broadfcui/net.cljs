(ns broadfcui.net
  (:require
  [broadfcui.common.components :as comps]
  [broadfcui.common.style :as style]
  [broadfcui.utils :as utils]
  ))

(defn handle-ajax-response [update-fn]
  (fn [{:keys [xhr success?]}]
    (if-let [parsed-response (as-> xhr x
                                (aget x "responseText")
                                (try (js-invoke js/JSON "parse" x) (catch js/Error e e))
                                (when-not (instance? js/Error x)
                                  (js->clj x :keywordize-keys true)))]
     (if success?
       (update-fn :parsed parsed-response)
       (update-fn :error (:message parsed-response)))
     (update-fn :error "Error parsing server response"))))

(defn render-with-ajax [ajax-response render-success & {:keys [loading-text error-override]}]
  (cond
    (nil? ajax-response)
    [comps/Spinner {:text (or loading-text "Loading...")}]
    (:error ajax-response)
    (style/create-server-error-message
     (if error-override
       (error-override ajax-response)
       (:error ajax-response)))
    :else (render-success))) ;;reading the passed key map to properly render state

(defn overwrite-error
  ([message] message)
  ([message code state state-key]
   (let [current-error (as-> (get-in state [state-key :error]) e
                             (js-invoke js/JSON "parse" e)
                             (js->clj e :keywordize-keys true))]
     (if (= code (:code current-error)) message
                                        (:message current-error)))))
