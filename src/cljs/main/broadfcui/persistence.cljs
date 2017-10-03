(ns broadfcui.persistence
  (:require
   [broadfcui.utils :as utils]
   ))

(defn- generate-persistence-key [key]
  (let [id (-> @utils/auth2-atom (.-currentUser) (.get) (.getBasicProfile) (.getId))]
    (keyword (str "state:" id ":" key))))

(defn save [{:keys [key state except only]}]
  (assert (not (and except only)) "Specify EITHER except OR only")
  (utils/local-storage-write
   (generate-persistence-key key)
   (cond except (apply dissoc @state except)
         only (select-keys @state only)
         :else @state)))

(defn try-restore [{:keys [key initial validator]}]
  (let [saved-state (some-> key generate-persistence-key utils/local-storage-read cljs.reader/read-string)]
    (if (and saved-state
             (or (not validator) (validator saved-state)))
      saved-state
      (initial))))

(defn check-saved-state [{:keys [key initial validator]}]
  (let [saved-state (some-> key generate-persistence-key utils/local-storage-read cljs.reader/read-string)]
    (if (and saved-state
             (or (not validator) (validator saved-state)))
      saved-state
      nil)))

(defn delete [key]
  (utils/local-storage-remove
   (generate-persistence-key key)))


(defn with-state-persistence [{:keys [key version validator initial except only]} defined-methods]
  (if-not key
    defined-methods
    (let [get-initial-state
          (fn [data]
            (merge
             (try-restore
              {:key key
               :initial (fn [] (merge initial (when version {:v version})))
               :validator (or validator
                              (when version (comp (partial = version) :v)))})
             (when-let [defined (:get-initial-state defined-methods)]
               (defined data))))
          component-did-update
          (fn [{:keys [state] :as data}]
            (save (utils/restructure key state except only))
            (when-let [defined (:component-did-update defined-methods)]
              (defined data)))]
      (merge defined-methods (utils/restructure get-initial-state component-did-update)))))
