(ns broadfcui.common.table.table
  (:require
    [dmohs.react :as react]
    [clojure.set :as set]
    [broadfcui.common.components :as comps]
    [broadfcui.common.style :as style]
    [broadfcui.common.table.body :as body]
    [broadfcui.common.table.column-editor :refer [ColumnEditButton]]
    [broadfcui.common.table.paginator :refer [Paginator]]
    [broadfcui.common.table.utils :as table-utils]
    [broadfcui.persistence :as persistence]
    [broadfcui.utils :as utils]
    ))


;; Documentation:
;; https://broadinstitute.atlassian.net/wiki/display/GAWB/The+Table+UI+component


;; Define nested default props this way because we need to do a deep-merge,
;; instead of React's regular merge.
(def ^:private default-props
  {:body {:empty-message "There are no rows to display."
          :external-query-params #{}
          :behavior {:reorderable-columns? true
                     :fixed-column-count 0
                     :sortable-columns? true
                     :allow-no-sort? false
                     :resizable-columns? true
                     :filterable? true}}
   :toolbar {:style {:display "flex" :alignItems "baseline" :marginBottom "1rem"}
             :column-edit-button {:style {:marginRight "1rem"}
                                  :button {:text "Columns" :icon :settings
                                           :style {:padding "0.4rem 0.8rem 0.4rem 0.4rem"}}
                                  :anchor :left}
             :filter-bar {:style {:marginRight "1rem"}}}
   :paginator {:style {:marginTop "1.5rem"}
               :width-threshold 700
               :per-page-options [10 20 100 500]}})

(def ^:private all-query-params #{:page-number :rows-per-page :filter-text :sort-column :sort-order})


(react/defc Table
  {:reinitialize
   (fn [{:keys [state this]}]
     (swap! state merge (this :-fetch-initial-state)))
   :update-query-params
   (fn [{:keys [state]} new-params]
     (assert (set/subset? (set (keys new-params)) all-query-params) "Unknown key passed to :update-query-params")
     (let [old-state (:query-params @state)
           new-state (merge old-state new-params)
           different? (not= old-state new-state)]
       (when different?
         (swap! state assoc :query-params new-state))
       different?))
   :refresh-rows
   (fn [{:keys [props state refs]} & [reset-page-number?]]
     ((@refs "blocker") :show)
     (let [{:keys [data fetch-data]} props
           data-source (if data (table-utils/local data) fetch-data)
           query-params (merge (:query-params @state)
                               (when reset-page-number? {:page-number 1}))]
       (data-source {:columns (-> props :body :columns)
                     :query-params query-params
                     :on-done (fn [{:keys [total-count filtered-count results]}]
                                ((@refs "blocker") :hide)
                                (swap! state assoc
                                       :total-count total-count
                                       :filtered-count filtered-count
                                       :rows results
                                       :query-params query-params))})))
   :get-default-props
   (fn []
     {:load-on-mount true})
   :get-initial-state
   (fn [{:keys [this]}]
     (assoc (this :-fetch-initial-state) :rows []))
   :render
   (fn [{:keys [props state]}]
     (let [props (utils/deep-merge default-props props)
           {:keys [rows column-display filtered-count query-params]} @state
           {:keys [body toolbar paginator]} props
           {:keys [empty-message columns behavior external-query-params]} body
           {:keys [fixed-column-count allow-no-sort?]} behavior
           total-count (some :total-count [props @state])
           query-params (merge query-params (select-keys props external-query-params))
           update-column-display #(swap! state assoc :column-display %)]
       [:div {:style {:position "relative"}}
        [comps/DelayedBlocker {:ref "blocker" :banner "Loading..."}]
        [:div {:style (:style toolbar)}
         (when (:reorderable-columns? behavior)
           (let [button-props (:column-edit-button toolbar)]
             [:div {:style (:style button-props)}
              [ColumnEditButton
               (assoc (utils/restructure columns column-display update-column-display fixed-column-count)
                 :reorder-anchor (:anchor button-props)
                 :button (:button button-props))]]))
         (when (and (:filterable? behavior) (not (contains? external-query-params :filter-text)))
           (let [filter-bar-props (:filter-bar toolbar)]
             [:div {:style (:style filter-bar-props)}
              [comps/TextFilter (merge
                                 {:initial-text (:filter-text query-params)
                                  :on-filter #(swap! state update :query-params assoc :filter-text % :page-number 1)}
                                 (:inner filter-bar-props))]]))
         (list* (:items toolbar))]
        [:div {:style {:overflowX "auto"}}
         (if (empty? rows)
           (style/create-message-well empty-message)
           [body/TableBody
            (merge
             body
             (select-keys query-params [:sort-column :sort-order])
             (utils/restructure rows column-display update-column-display fixed-column-count allow-no-sort?)
             {:set-sort (fn [col order] (swap! state update :query-params
                                               merge {:sort-column col :sort-order order}))})])]
        [Paginator
         (merge paginator
                (select-keys query-params [:rows-per-page :page-number])
                (utils/restructure total-count filtered-count)
                {:page-selected #(swap! state assoc-in [:query-params :page-number] %)
                 :per-page-selected #(swap! state update :query-params
                                            merge {:rows-per-page % :page-number 1})})]]))
   :component-did-mount
   (fn [{:keys [props this]}]
     (when (:load-on-mount props)
       (this :refresh-rows)))
   :component-did-update
   (fn [{:keys [props state prev-props prev-state this]}]
     (let [data-change? (not= (:data props) (:data prev-props))]
       (when (or (not= (:query-params @state) (:query-params prev-state))
                 data-change?)
         (this :refresh-rows data-change?)))
     (when (and (:persistence-key props)
                (or (not= (:query-params @state) (:query-params prev-state))
                    (not= (:column-display @state) (:column-display prev-state))))
       (persistence/save {:key (:persistence-key props)
                          :state state
                          :only [:query-params :column-display :v]})))
   :-fetch-initial-state
   (fn [{:keys [props]}]
     (persistence/try-restore
      {:key (:persistence-key props)
       :validator (fn [stored-value]
                    (or (not (:v props))
                        (= (:v props) (:v stored-value))))
       :initial
       (fn []
         (let [columns (-> props :body :columns)
               processed-columns (if-let [defaults (-> props :body :column-defaults)]
                                   (let [by-header (utils/index-by (some-fn :id :header) columns)
                                         default-showing (->> (defaults "shown")
                                                              (replace by-header)
                                                              (map #(assoc % :show-initial? true)))
                                         default-hiding (->> (defaults "hidden")
                                                             (replace by-header)
                                                             (map #(assoc % :show-initial? false)))
                                         mentioned (set/union (set (defaults "shown"))
                                                              (set (defaults "hidden")))]
                                     (concat default-showing default-hiding
                                             (remove (fn [{:keys [id header]}]
                                                       (contains? mentioned (or id header)))
                                                     columns)))
                                   columns)
               initial-sort-column (or (first (filter :sort-initial processed-columns))
                                       (when-not (-> props :body :behavior :allow-no-sort?)
                                         (first processed-columns)))
               initial-sort-order (when initial-sort-column
                                    (get initial-sort-column :sort-initial :asc))]
           (merge
            {:query-params (select-keys
                            {:page-number 1
                             :rows-per-page 20
                             :filter-text ""
                             :sort-column (table-utils/resolve-id initial-sort-column)
                             :sort-order initial-sort-order}
                            (set/difference all-query-params (-> props :body :external-query-params)))
             :column-display (table-utils/build-column-display processed-columns)}
            (when-let [v (:v props)] {:v v}))))}))})
