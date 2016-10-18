(ns org.broadinstitute.firecloud-ui.persistence
  (:require
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- generate-persistence-key [key]
  (keyword (str "state:" @utils/current-user ":" key)))

(defn save [{:keys [key state except only]}]
  (assert (not (and except only)) "Specify EITHER except OR only")
  (utils/local-storage-write (generate-persistence-key key)
    (cond except (apply dissoc @state except)
          only (select-keys @state only)
          :else @state)))

(defn try-restore [{:keys [key initial]}]
  (if-let [saved-state (some-> key generate-persistence-key utils/local-storage-read cljs.reader/read-string)]
    saved-state
    initial))
