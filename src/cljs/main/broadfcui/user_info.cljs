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

(defonce saved-ready-billing-project-names (atom []))

(defn reload-billing-projects [& [on-done]]
  (endpoints/get-billing-projects
   (fn [err-text projects]
     (when-not err-text
       (reset! saved-ready-billing-project-names (map :projectName projects)))
     (when on-done (on-done err-text)))))
