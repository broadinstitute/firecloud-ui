(ns org.broadinstitute.firecloud-ui.persistence
  (:require
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- generate-persistence-key [key]
  (let [id (-> @utils/google-auth2-instance (.-currentUser) (.get) (.getBasicProfile) (.getId))]
    (keyword (str "state:" id ":" key))))

(defn save [{:keys [key state except only]}]
  (assert (not (and except only)) "Specify EITHER except OR only")
  (utils/local-storage-write
    (generate-persistence-key key)
    (cond except (apply dissoc @state except)
          only (select-keys @state only)
          :else @state)))

(defn try-restore [{:keys [key initial validator]}]
  (initial)
  ;; FIXME: actual logic is disabled pending fix for new columns not showing up
  #_(let [saved-state (some-> key generate-persistence-key utils/local-storage-read cljs.reader/read-string)]
    (if (and saved-state
             (or (not validator) (some-> saved-state validator)))
      saved-state
      (initial))))
