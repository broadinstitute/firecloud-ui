(ns broadfcui.common.entity-table
  (:require
   [dmohs.react :as react]
   [inflections.core :as inflections]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.common.table.utils :as table-utils]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   [broadfcui.persistence :as persistence]))


;; for attributes referring to a single other entity
;; e.g. samples referring to participants
(defn is-single-ref? [attr-value]
  (and (map? attr-value)
       (= (set (keys attr-value)) #{:entityType :entityName})))

(defn- render-list-item [item]
  (if (is-single-ref? item)
    (:entityName item)
    item))

(react/defc EntityTable
  {:update-data
   (fn [{:keys [refs after-update]} reinitialize?]
     ((@refs "table") :refresh-rows)
     (when reinitialize?
       (after-update #((@refs "table") :reinitialize))))
   :refresh
   (fn [{:keys [props state this after-update]} & [entity-type reinitialize?]]
     (swap! state dissoc :entity-metadata)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-entity-types (:workspace-id props))
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (let [metadata (get-parsed-response)
                          entity-types-from-response (map name (keys metadata))
                          entity-types-with-given (if entity-type
                                                    (distinct (conj entity-types-from-response entity-type))
                                                    entity-types-from-response)
                          entity-types-sorted (utils/sort-match common/root-entity-types entity-types-with-given)
                          selected-entity-type (or entity-type (first entity-types-sorted))]
                      (swap! state assoc
                             :entity-metadata metadata
                             :entity-types entity-types-sorted
                             :selected-entity-type selected-entity-type)
                      (when-let [f (:on-entity-type-selected props)]
                        (f selected-entity-type))
                      (after-update #(this :update-data reinitialize?)))
                    (swap! state assoc :server-error (get-parsed-response false))))}))
   :get-default-props
   (fn []
     {:empty-message "There are no entities to display."
      :attribute-renderer table-utils/default-render})
   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [server-error entity-metadata entity-types selected-entity-type]} @state]
       [:div {}
        (cond
          server-error (style/create-server-error-message server-error)
          (nil? entity-metadata) [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Retrieving entity types..."}]]
          :else
          (let [attributes (map keyword (get-in entity-metadata [(keyword selected-entity-type) :attributeNames]))
                attr-col-width (->> attributes count (/ 1000) int (min 400) (max 100))
                process-local-state (and (:process-local-state props) (persistence/check-saved-state {:key (str (common/workspace-id->string (:workspace-id props)) ":data:" (:selected-entity-type @state))}))]
            [Table
             {:data-test-id "entity-table"
              :process-local-state process-local-state
              :ref "table" :key selected-entity-type
              :persistence-key (when selected-entity-type
                                 (str (common/workspace-id->string (:workspace-id props)) ":data:" selected-entity-type))
              :v 2
              :fetch-data (this :-pagination)
              :blocker-delay-time-ms 0
              :tabs {:items (->> entity-types
                                 (map (fn [entity-type]
                                        {:label entity-type
                                         :entity-type entity-type
                                         :size (get-in entity-metadata [(keyword entity-type) :count])}))
                                 vec)
                     :initial-selection #(= (:entity-type %) selected-entity-type)
                     :on-tab-selected (fn [{:keys [entity-type]}]
                                        (swap! state assoc :selected-entity-type entity-type)
                                        (when-let [f (:on-entity-type-selected props)]
                                          (f entity-type)))
                     :render (fn [label count] (str label " (" count " total)"))}
              :body
              {:style (merge table-style/table-heavy (:style props))
               ;; this :behavior and the 'or' guard on the first column are to make things
               ;; behave when there is no data (and thus no entity-types)
               :behavior {:reorderable-columns? (seq entity-types) :filterable? (seq entity-types)}
               :columns
               (into [{:header (or (get-in entity-metadata [(keyword selected-entity-type) :idName]) "No data")
                       :initial-width 200
                       :as-text :name :sort-by :text
                       :render (or (:entity-name-renderer props) :name)}]
                     (map (fn [k]
                            {:header (name k) :initial-width attr-col-width
                             :column-data (comp k :attributes)
                             :as-text (fn [attr-value]
                                        (cond (is-single-ref? attr-value)
                                              (:entityName attr-value)
                                              (common/attribute-list? attr-value)
                                              (map render-list-item (common/attribute-values attr-value))
                                              :else
                                              (str attr-value)))
                             :sort-by :text
                             :render (fn [attr-value]
                                       (cond
                                         (is-single-ref? attr-value)
                                         (if-let [renderer (:linked-entity-renderer props)]
                                           (renderer attr-value)
                                           (:entityName attr-value))
                                         (common/attribute-list? attr-value)
                                         (let [items (map render-list-item (common/attribute-values attr-value))
                                               c (count items)
                                               entity-type (or (some-> selected-entity-type
                                                                       common/set-type->membership-attribute
                                                                       inflections/singular)
                                                               "item")]
                                           (str (inflections/pluralize c entity-type)
                                                (when (and (pos? c) (= entity-type "item"))
                                                  (str ": " (string/join ", " items)))))
                                         :else ((:attribute-renderer props) attr-value)))})
                          attributes))
               :column-defaults (if (utils/log (some? process-local-state))
                                  (let [local-state (persistence/check-saved-state {:key (str (common/workspace-id->string (:workspace-id props)) ":data:" (:selected-entity-type @state))})]
                                    (let [shown (mapv #(get % :id) (get (group-by :visible? (:column-display local-state)) true))
                                          hidden (mapv #(get % :id) (get (group-by :visible? (:column-display local-state)) false))]
                                      (hash-map "shown" shown "hidden" hidden)))
                                  (get (:column-defaults props) selected-entity-type))
               :on-row-click (:on-row-click props)
               :on-column-change (:on-column-change props)}
              :toolbar
              {:get-items
               (fn [table-props]
                 (when-let [get-toolbar-items (:get-toolbar-items props)]
                   (get-toolbar-items table-props)))
               :style {:flexWrap "wrap"}}}]))]))
   :component-did-mount
   (fn [{:keys [props this]}]
     (this :refresh (:initial-entity-type props)))
   :-pagination
   (fn [{:keys [props state]}]
     (let [{:keys [entity-types]} @state]
       (fn [{:keys [query-params tab on-done]}]
         (if (empty? entity-types)
           (on-done {:total-count 0 :tab-count 0 :results []})
           (let [{:keys [page-number rows-per-page filter-text sort-column sort-order]} query-params
                 entity-type (:entity-type tab)]
             (endpoints/call-ajax-orch
              {:endpoint (endpoints/get-entities-paginated
                          (:workspace-id props)
                          entity-type
                          {"page" page-number
                           "pageSize" rows-per-page
                           "filterTerms" (js/encodeURIComponent filter-text)
                           "sortField" sort-column
                           "sortDirection" (name sort-order)})
               :on-done (fn [{:keys [success? get-parsed-response status-text status-code]}]
                          (if success?
                            (let [{:keys [results]
                                   {:keys [unfilteredCount filteredCount]} :resultMetadata} (get-parsed-response)]
                              (on-done {:total-count unfilteredCount
                                        :tab-count filteredCount
                                        :results results}))
                            (on-done {:error (str status-text " (" status-code ")")})))}))))))})
