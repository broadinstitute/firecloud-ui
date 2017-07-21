(ns broadfcui.common.entity-table
  (:require
   [dmohs.react :as react]
   [inflections.core :as inflections]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.common.table.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.common.table.utils :as table-utils]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))


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
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-entity-types (:workspace-id props))
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (let [metadata (get-parsed-response)
                          entity-types (utils/sort-match (map keyword common/root-entity-types) (keys metadata))
                          selected-entity-type (or (some-> entity-type keyword) (first entity-types))]
                      (swap! state assoc
                             :entity-metadata metadata
                             :entity-types entity-types
                             :selected-entity-type selected-entity-type
                             :selected-filter-index (utils/index-of entity-types selected-entity-type))
                      (when-let [f (:on-entity-type-selected props)]
                        (f selected-entity-type))
                      (after-update #(this :update-data reinitialize?)))
                    (swap! state assoc :server-error (get-parsed-response false))))}))
   :get-ordered-columns
   (fn [{:keys [refs]}]
     ((@refs "table") :get-ordered-columns))
   :get-default-props
   (fn []
     {:empty-message "There are no entities to display."
      :attribute-renderer table-utils/default-render})
   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [server-error entity-metadata entity-types selected-entity-type]} @state]
       [:div {}
        (when (:loading-entities? @state)
          [comps/Blocker {:banner "Loading entities..."}])
        (cond
          server-error (style/create-server-error-message server-error)
          (nil? entity-metadata) [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Retrieving entity types..."}]]
          :else
          (let [attributes (map keyword (get-in entity-metadata [selected-entity-type :attributeNames]))
                attr-col-width (->> attributes count (/ 1000) int (min 400) (max 100))]
            [Table
             {:ref "table" :key selected-entity-type
              :persistence-key (when selected-entity-type
                                 (str (common/workspace-id->string (:workspace-id props)) ":data" selected-entity-type))
              :v 1
              :fetch-data (this :-pagination)
              :load-on-mount false
              :body
              {:style (merge table-style/table-heavy (:style props))
               ;; this :behavior and the 'or' guard on the first column are to make things
               ;; behave when there is no data (and thus no entity-types)
               :behavior {:reorderable-columns? (seq entity-types) :filterable? (seq entity-types)}
               :columns
               (into [{:header (or (get-in entity-metadata [selected-entity-type :idName]) "No data")
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
                                                                       name
                                                                       common/set-type->membership-attribute
                                                                       inflections/singular)
                                                               "item")]
                                           (str (inflections/pluralize c entity-type)
                                                (when (and (pos? c) (= entity-type "item"))
                                                  (str ": " (string/join ", " items)))))
                                         :else ((:attribute-renderer props) attr-value)))})
                          attributes))
               :column-defaults (get (:column-defaults props) (some-> selected-entity-type name))
               :on-row-click (:on-row-click props)
               :on-column-change (:on-column-change props)}
              :toolbar
              {:items
               (fn [table-props]
                (cons [comps/FilterGroupBar
                       {:filter-groups (map (fn [entity-type]
                                              {:text (name entity-type)
                                               :count-override (get-in entity-metadata [entity-type :count])})
                                            entity-types)
                        :selected-index (:selected-filter-index @state)
                        :on-change (fn [index _]
                                     (let [selected-entity-type (nth entity-types index)]
                                       (swap! state assoc
                                              :selected-filter-index index
                                              :selected-entity-type selected-entity-type)
                                       (when-let [f (:on-entity-type-selected props)]
                                         (f selected-entity-type))))}]
                      ((:toolbar-items props) table-props)))
               :style {:flexWrap "wrap"}}}]))]))
   :component-did-mount
   (fn [{:keys [props this]}]
     (this :refresh (:initial-entity-type props)))
   :component-did-update
   (fn [{:keys [state prev-state this]}]
     (let [prev-index (:selected-filter-index prev-state)
           this-index (:selected-filter-index @state)]
       (when (and prev-index
                  (not= prev-index this-index))
         (this :update-data))))
   :-pagination
   (fn [{:keys [props state]}]
     (let [{:keys [entity-types selected-filter-index]} @state]
       (fn [{:keys [query-params on-done]}]
         (if (empty? entity-types)
           (on-done {:total-count 0 :filtered-count 0 :results []})
           (let [{:keys [page-number rows-per-page filter-text sort-column sort-order]} query-params
                 entity-type (nth entity-types selected-filter-index)]
             (endpoints/call-ajax-orch
              {:endpoint (endpoints/get-entities-paginated
                          (:workspace-id props)
                          (name entity-type)
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
                                        :filtered-count filteredCount
                                        :results results}))
                            (on-done {:error (str status-text " (" status-code ")")})))}))))))})
