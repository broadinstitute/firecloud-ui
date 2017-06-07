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
        (update-fn :error (select-keys parsed-response [:message :statusCode])))
      (update-fn :error "Error parsing server response"))))

(defn render-with-ajax [ajax-response render-success & {:keys [loading-text rephrase-error error-handler]}]
  (assert (not (and rephrase-error error-handler)) "Provide EITHER error-handler OR rephrase-error")
  (let [error (:error ajax-response)]
    (cond
      (nil? ajax-response)
      [comps/Spinner {:text (or loading-text "Loading...")}]
      error
      (if error-handler
        (error-handler error)
        (style/create-server-error-message
         (if rephrase-error
           (rephrase-error error)
           (:message error))))
      :else (render-success))))

(defn create-error-message-for-code
  ([message code error]
   (if (= code (:statusCode error))
     message
     (:message error))))
