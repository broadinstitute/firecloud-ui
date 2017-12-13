(ns broadfcui.user-info
  (:require
   [broadfcui.common :as common]
   [broadfcui.endpoints :as endpoints]
   ))

(defonce saved-user-profile (atom false))

(defn save-user-profile [user-profile]
  (reset! saved-user-profile user-profile))

(defn reload-user-profile [& [on-done]]
  (endpoints/profile-get
   (fn [{:keys [success? get-parsed-response] :as response}]
     (when success?
       (save-user-profile (common/parse-profile (get-parsed-response false))))
     (when on-done (on-done response)))))

