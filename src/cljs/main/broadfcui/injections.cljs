(ns broadfcui.injections
  (:require
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.modals :as modals]
   [broadfcui.utils.ajax :as ajax]
   [broadfcui.utils.user :as user]
   ))


(defn- setup-button-modals []
  (reset! buttons/modal-constructor
          (fn [type props]
            ((case type
               :message modals/render-message
               :error modals/render-error)
             props))))


(defn- setup-ajax-headers []
  (reset! ajax/get-bearer-token-header user/get-bearer-token-header))


(defn setup []
  (setup-button-modals)
  (setup-ajax-headers))
