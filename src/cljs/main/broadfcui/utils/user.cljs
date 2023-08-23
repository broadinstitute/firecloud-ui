(ns broadfcui.utils.user
  (:require
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))


(defonce ^:private user-listeners (atom {}))
(defn add-user-listener [k on-change]
  (swap! user-listeners assoc k on-change))
(defn remove-user-listener [k]
  (swap! user-listeners dissoc k))


(defonce auth2-atom (atom nil))
(defn set-google-auth2-instance! [instance]
  (reset! auth2-atom instance)
  (-> instance
      (aget "currentUser")
      (js-invoke
       "listen" (fn [u]
                  (doseq [[_ on-change] @user-listeners]
                    (on-change u))))))

(defn get-user []
  (-> @auth2-atom (aget "currentUser") (js-invoke "get")))

(defn get-access-token []
  (-> (get-user) (js-invoke "getAuthResponse") (aget "access_token")))

(defn get-bearer-token-header []
  {"Authorization" (str "Bearer " (get-access-token))})

(defn get-email []
  (-> (get-user) (js-invoke "getBasicProfile") (js-invoke "getEmail")))

(defn get-id []
  (-> (get-user) (js-invoke "getBasicProfile") (js-invoke "getId")))


(defn get-cookie-domain []
  (if (= "local.broadinstitute.org" js/window.location.hostname)
    "local.broadinstitute.org"
    (string/join "." (rest (string/split js/window.location.hostname ".")))))

(defn delete-access-token-cookie []
  (.remove goog.net.cookies "FCtoken" "/" (get-cookie-domain)))

(defn set-access-token-cookie [token]
  (if token
    (.set goog.net.cookies "FCtoken" token -1 "/" (get-cookie-domain) true) ; secure cookie
    (delete-access-token-cookie)))

(defn refresh-access-token [] (set-access-token-cookie (get-access-token)))


(defonce profile (atom false))
(defonce terra-preference (atom nil)) ;; initially false to disable redirects until profile is loaded

(defn reload-profile [& [on-done]]
  (endpoints/profile-get
   (fn [{:keys [success? get-parsed-response] :as response}]
     (when success?
       (let [parsed-profile (common/parse-profile (get-parsed-response))
             profile-pref (if-some [pp (:preferTerra parsed-profile)]
                            (utils/parse-boolean pp)
                            true)]
         (reset! profile parsed-profile)
         (reset! terra-preference profile-pref)))
     (when on-done (on-done response)))))

(defonce saved-ready-billing-project-names (atom []))

