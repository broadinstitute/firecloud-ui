(ns broadfcui.user-info
  (:require
   [broadfcui.common :as common]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))

(defonce saved-user-profile (atom false))

(defn reload-user-profile [& [on-done]]
  (endpoints/profile-get
   (fn [{:keys [success? get-parsed-response] :as response}]
     (when success?
       (reset! saved-user-profile (common/parse-profile (get-parsed-response))))
     (when on-done (on-done response)))))

(defonce saved-ready-billing-project-names (atom []))

(defn reload-billing-projects [& [on-done]]
  (endpoints/get-billing-projects
   (fn [err-text projects]
     (when-not err-text
       (reset! saved-ready-billing-project-names (map :projectName projects)))
     (when on-done (on-done err-text)))))
