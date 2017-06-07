(ns broadfcui.net
  (:require
  [broadfcui.common.components :as comps]
  [broadfcui.common.style :as style]
  [broadfcui.utils :as utils]
  ))

(defn handle-ajax-response [update-fn]
  (fn [{:keys [xhr status-code success?]}]
    (if-let [parsed-response (as-> xhr x
                                   (aget x "responseText")
                                   (try (js-invoke js/JSON "parse" x) (catch js/Error e e))
                                   (when-not (instance? js/Error x)
                                     (js->clj x :keywordize-keys true)))]
      (update-fn (utils/restructure success? status-code parsed-response))
      (update-fn {:success? false
                  :status-code status-code
                  :parsed-response {:message "Error parsing server response"}
                  :original-request xhr}))))

(defn render-with-ajax [{:keys [success? parsed-response] :as ajax-response} render-success
                        & {:keys [loading-text rephrase-error error-handler]}]
  (assert (not (and rephrase-error error-handler)) "Provide EITHER error-handler OR rephrase-error")
  (cond
    (nil? ajax-response)
    [comps/Spinner {:text (or loading-text "Loading...")}]
    (not success?)
    (if error-handler
      (error-handler parsed-response)
      (style/create-server-error-message
       (if rephrase-error
         (rephrase-error parsed-response)
         (:message parsed-response))))
    :else (render-success)))

(defn create-error-message-for-code
  ([message code error]
   (if (= code (:statusCode error))
     message
     (:message error))))
