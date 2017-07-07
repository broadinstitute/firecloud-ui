(ns broadfcui.nih-link-warning
  (:require
    [dmohs.react :as react]
    [broadfcui.common.duration :as duration]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.page.profile :as profile]
    [broadfcui.utils :as utils]
    ))

(react/defc NihLinkWarning
  {:render
   (fn [{:keys [props state]}]
     (when-let [status (:nih-status @state)]
       (let [linked-username (:linkedNihUsername status)
             expire-time (* (:linkExpireTime status) 1000)
             expired? (< expire-time (.now js/Date))
             expiring-soon? (and (not expired?) (< expire-time (utils/_24-hours-from-now-ms)))]
         (when (and linked-username (or expired? expiring-soon?))
           [:div {:style {:border "1px solid #c00" :backgroundColor "#fcc"
                          :color "#800" :fontSize "small" :padding "6px 10px" :textAlign "center"}}
            "Your access to NIH Controlled Access workspaces and data "
            (if expiring-soon?
              (str "will expire " (duration/fuzzy-time-from-now-ms expire-time true))
              "has expired")
            " and your access to NIH Controlled Access workspaces will be revoked within 24 hours."
            [:div {} [:a {:href (profile/get-nih-link-href)} "Re-link"]
             " your FireCloud and eRA Commons / NIH accounts (" linked-username
             ") to retain access to these workspaces and data."]]))))
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/profile-get-nih-status
      (fn [{:keys [success? status-code status-text get-parsed-response]}]
        (cond
          success? (swap! state assoc :nih-status (get-parsed-response))
          (= status-code 404) (swap! state assoc :nih-status :none)
          :else
          (swap! state assoc :error-message status-text)))))})
