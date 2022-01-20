(ns broadfcui.config.loader
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.style :as style]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.config :as config]
   [broadfcui.utils :as utils]
   [broadfcui.utils.ajax :as ajax]
   ))

(react/defc Component
  {:render
   (fn [{:keys [state]}]
     (when-let [errors (:errors @state)]
       [:div {:style {:padding "40px 0" :color (:state-exception style/colors)}}
        "Error loading configuration:"
        [:ul {}
         (common/mapwrap :li errors)]]))
   :component-did-mount
   (fn [{:keys [this]}]
     ;; Use basic ajax call here to bypass authentication.
     (ajax/call {:url "/config.json" :on-done #(this :-handle-response %)}))
   :-handle-response
   (fn [{:keys [props state]} {:keys [success? raw-response status-code]}]
     (if success?
       (let [[parsed error] (utils/parse-json-string raw-response false false)]
         (if error
           (swap! state assoc :errors ["Failed to parse server response"])
           (let [[valid? errors] (config/check-config parsed)]
             (if valid?
               (do
                 (reset! config/config parsed)
                 ((:on-success props)))
               (swap! state assoc :errors errors)))))
       (swap! state assoc :errors [(str "Server responded with status code " status-code)])))})
