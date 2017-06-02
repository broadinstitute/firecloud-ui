(ns broadfcui.page.workspace.data.utils
  (:require [broadfcui.endpoints :as endpoints]
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
                   (if (is-entity-set? entity-type)
                     (let [attrs (:attributes (get-parsed-response true))
                           items (case entity-type
                                   "sample_set" (:items (:samples attrs))
                                   "pair_set" (:items (:pairs attrs))
                                   "participant_set" (:items (:participants attrs)))]
                       (update-parent-state :selected-attr-list items :loading-attributes false))
                     (update-parent-state :selected-attr-list (:attributes (get-parsed-response true)) :loading-attributes false))
                   (update-parent-state :server-error (get-parsed-response false) :loading-attributes false)))})))
