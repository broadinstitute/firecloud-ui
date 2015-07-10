;; Create a namespace for these session-related codes
(ns org.broadinstitute.firecloud-ui.session
  (:require [clojure.string]
            [dmohs.react :as react]
            [org.broadinstitute.firecloud-ui.utils :as utils]))


;; current-user variable/token/holder
(defonce current-user (atom nil))



;; logout *callback* atom variable/token/holder
(defonce on-log-out-atom (atom nil))



;; utility for getting the current user via the token/holder
(defn get-current-user []
  @current-user)



;; utility for setting the current user via the token/holder
(defn set-current-user [user]
  (reset! current-user user))



;; utility for logging a user out via the tokens/holders and external methods
(defn log-out []
  (-> js/gapi
    (aget "auth2")
    (utils/call-external-object-method :getAuthInstance)
    (utils/call-external-object-method :signOut)
    (utils/call-external-object-method
      ;; set the user as nil and reload
      :then (fn [] (reset! current-user nil) (@on-log-out-atom) (.reload (.-location js/window))))))



;; reset the on-log-out atom to the given callback
(defn on-log-out [callback]
  (reset! on-log-out-atom callback)
  )

