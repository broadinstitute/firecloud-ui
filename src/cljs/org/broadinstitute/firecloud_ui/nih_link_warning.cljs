(ns org.broadinstitute.firecloud-ui.nih-link-warning
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.page.profile :as profile]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))


(defn- get-profile [ajax-args on-done]
  (let [on-done (if (or (nil? (:canned-response ajax-args)) (:use-canned-response? ajax-args))
                  on-done
                  (fn [{:keys [xhr] :as m}]
                    (utils/cljslog {:status (.-status xhr)
                                    :statusText (.-statusText xhr)
                                    :responseText (.-responseText xhr)})
                    (on-done m)))]
    (utils/ajax-orch
     "/nih/status"
     (merge ajax-args {:on-done on-done}))))


(react/defc NihLinkWarning
  {:render
   (fn [{:keys [props state]}]
     (when-let [status (:status @state)]
       (let [expire-time (-> (get status "linkExpireTime") (* 1000) (js/moment.))
             _24-hours-from-now (.add (js/moment.) 24 "hours")]
         (when (and (get status "isDbgapAuthorized")
                    (.isBefore expire-time _24-hours-from-now))
           [:div {:style {:border "1px solid #c00" :backgroundColor "#fcc"
                          :color "#800" :fontSize "small" :padding "6px 10px"}}
            "Your NIH account link (" (get status "linkedNihUsername") ") will expire "
            (.calendar expire-time) ". "
            [:a {:href (profile/get-nih-link-href)} "Re-link"]
            " your account before then to retain dbGAP authorization."]))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (get-profile
      (:ajax-args props)
      (fn [{:keys [success? get-parsed-response]}]
        ;; Silently fail on errors?
        (when success?
          (swap! state assoc :status (get-parsed-response))))))})
