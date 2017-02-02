(ns broadfcui.persistence
  (:require
    [broadfcui.utils :as utils]
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

(defn is-valid [validator saved-state]
  )

(defn try-restore [{:keys [key initial validator]}]
  (let [saved-state (some-> key generate-persistence-key utils/local-storage-read cljs.reader/read-string)]
    (if (and saved-state
             (or (not validator) (some-> saved-state validator)))
      saved-state
      (initial))))

(defn delete [key]
  (utils/local-storage-remove
   (generate-persistence-key key)))
