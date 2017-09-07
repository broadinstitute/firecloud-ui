(ns broadfcui.page.workspace.data.utils
  (:require
   [broadfcui.common :as common]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))

(defn is-entity-set? [entity-type]
  (re-find #"_set" entity-type))

(defn get-entity-attrs [{:keys [entity-name entity-type workspace-id update-parent-state]}]
  (when (and (some? entity-name) (some? entity-type))
    (endpoints/call-ajax-orch
     {:endpoint (endpoints/get-entity workspace-id entity-type entity-name)
      :on-done (fn [{:keys [success? get-parsed-response]}]
                 (if success?
                   (let [attrs (:attributes (get-parsed-response))]
                     (if (is-entity-set? entity-type)
                       ;; for set entity types we display _only_ the set elements, expanded into separate rows.
                       (let [entities (case entity-type
                                        "sample_set" (:samples attrs)
                                        "pair_set" (:pairs attrs)
                                        "participant_set" (:participants attrs))]
                         (update-parent-state :selected-attr-list (:items entities) :loading-attributes? false))
                       ;; otherwise display all attribute values, expanded into separate rows.
                       ;; generate a user-friendly string for list-valued attributes.
                       (let [attr-value-mapper
                             (fn [v]
                               (if-let [items (common/attribute-values v)]
                                 (if (empty? items)
                                   "0 items"
                                   (str (count items) " items: " (clojure.string/join ", " items)))
                                 v))]
                         (update-parent-state :selected-attr-list (utils/map-values attr-value-mapper attrs) :loading-attributes? false))))
                   (update-parent-state :server-error (get-parsed-response false) :loading-attributes? false)))})))

(defn get-column-defaults [json-column-defaults]
  (let [parsed
        (try
          (utils/parse-json-string json-column-defaults)
          (catch js/Object e
            (utils/jslog e) nil))
        validate-schema
        (fn [[k v]]
          (if (some #(= k %) common/root-entity-types)
            (cond
              (map? v) {k (select-keys v ["shown" "hidden"])}
              ;; Default to shown if the shown/hidden key is not present
              (vector? v) {k {"shown" v}}
              :else {})
            {}))]
    (into {} (mapcat validate-schema parsed))))
