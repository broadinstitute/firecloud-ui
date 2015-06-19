(ns org.broadinstitute.firecloud-ui.session
  (:require [clojure.string]
            [dmohs.react :as react]
            [org.broadinstitute.firecloud-ui.utils :as utils]))


(defonce current-user (atom nil))


(defonce on-log-out-atom (atom nil))


(defn get-current-user []
  @current-user)


(defn set-current-user [user]
  (reset! current-user user))


(defn log-out []
  (-> js/gapi
    (aget "auth2")
    (utils/call-external-object-method :getAuthInstance)
    (utils/call-external-object-method :signOut)
    (utils/call-external-object-method
      :then (fn [] (reset! current-user nil) (@on-log-out-atom) (.reload (.-location js/window))))))


(defn on-log-out [callback]
  (reset! on-log-out-atom callback)
  )
