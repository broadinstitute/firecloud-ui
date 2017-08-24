(ns broadfcui.net
  (:require
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))

(defn handle-ajax-response [update-fn]
  (fn [{:keys [xhr status-code status-text success?]}]
    (if-let [parsed-response (as-> xhr x
                                   (aget x "responseText")
                                   (try (js-invoke js/JSON "parse" x) (catch js/Error e e))
                                   (when-not (instance? js/Error x)
                                     (js->clj x :keywordize-keys true)))]
      (update-fn (utils/restructure success? status-code status-text parsed-response))
      (update-fn (merge
                  (utils/restructure success? status-code status-text xhr)
                  {:parsed-response {:message "Error parsing server response"}})))))

(defn render-with-ajax
  ([ajax-response render-success] (render-with-ajax ajax-response render-success nil))
  ([{:keys [success? parsed-response] :as ajax-response}
    render-success
    {:keys [loading-text rephrase-error handle-error blocking?]}]
   (assert (not (and rephrase-error handle-error)) "Provide EITHER handle-error OR rephrase-error")
   (cond
     (nil? ajax-response)
     (if blocking?
       [comps/Blocker {:banner (or loading-text "Loading...")}]
       [comps/Spinner {:text (or loading-text "Loading...")
                       :style {:display "inline-block"}}])
     (not success?)
     (if handle-error
       (handle-error parsed-response)
       (style/create-server-error-message
        (if rephrase-error
          (rephrase-error ajax-response)
          (:message parsed-response))))
     :else (render-success))))
