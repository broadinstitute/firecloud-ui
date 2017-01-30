(ns broadfcui.nih-link-warning
  (:require
   [dmohs.react :as react]
   [broadfcui.common.duration :as duration]
   [broadfcui.page.profile :as profile]
   [broadfcui.utils :as utils]
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
       (let [expire-time (-> (get status "linkExpireTime") (* 1000))]
         (when (and (get status "isDbgapAuthorized")
                    (< expire-time (utils/_24-hours-from-now-ms)))
           [:div {:style {:border "1px solid #c00" :backgroundColor "#fcc"
                          :color "#800" :fontSize "small" :padding "6px 10px" :textAlign "center"}}
            "Your access to TCGA Controlled Access workspaces and data will expire "
            (duration/fuzzy-time-from-now-ms expire-time true) " and your access to TCGA Controlled Access workspaces will be revoked "
            "within 24 hours of that time. "
            [:br]
            [:a {:href (profile/get-nih-link-href)} "Re-link"]
            " your FireCloud and eRA Commons / NIH accounts (" (get status "linkedNihUsername")
            ") before then to retain access to these workspaces and data."]))))
   :component-did-mount
   (fn [{:keys [props state]}]
     (get-profile
      (:ajax-args props)
      (fn [{:keys [success? get-parsed-response]}]
        ;; Silently fail on errors?
        (when success?
          (swap! state assoc :status (get-parsed-response false))))))})
