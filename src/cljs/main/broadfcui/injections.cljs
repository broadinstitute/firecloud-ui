(ns broadfcui.injections
  (:require
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.modals :as modals]
   ))


(defn- setup-button-modals []
  (reset! buttons/modal-constructor
          (fn [type props]
            ((case type
               :message modals/render-message
               :error modals/render-error)
             props))))


(defn setup []
  (setup-button-modals))
