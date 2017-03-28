(ns broadfcui.common.table.table
  (:require
    [clojure.set :refer [difference]]
    [dmohs.react :as react]
    [broadfcui.common.components :as comps]
    [broadfcui.common.table.body :as body]
    [broadfcui.common.table.column-editor :refer [ColumnEditButton]]
    [broadfcui.common.table.paginator :as paginator]
    [broadfcui.common.table.utils :as table-utils]
    [broadfcui.common.style :as style]
    [broadfcui.persistence :as persistence]
    [broadfcui.utils :as utils]
    ))


;; Define default props this way because we need to do a deep-merge,
;; instead of React's regular merge.
(def ^:private default-props
  {:table {:empty-message "There are no rows to display."
           :fixed-column-count 0
           :external-query-params #{}
           :behavior {:reorderable-columns? true
                      :sortable-columns? true
                      :resizable-columns? true
                      :filterable? true}}
   :toolbar {:style {:display "flex" :alignItems "baseline" :marginBottom "1rem"}
             :column-edit-button {:style {:marginRight "1rem"}
                                  :button {:icon :settings}
                                  :anchor :left}
             :filter-bar {:style {:marginRight "1rem"}}}
   :paginator {:style {:marginTop "1rem"}
               :per-page-options [10 20 100 500]}})

(def ^:private all-query-params #{:page-number :rows-per-page :filter-text :sort-column :sort-order})


(react/defc Table
  {:update-query-params
   (fn [{:keys [state]} new-params]
     (let [old-state (:query-params @state)
           new-state (merge old-state new-params)
           different? (not= old-state new-state)]
       (when different?
         (swap! state assoc :query-params new-state))
       different?))
   :refresh-rows
   (fn [{:keys [props state]}]
     (swap! state assoc :loading? true)
     ((-> props :table :data-source) {:columns (-> props :table :columns)
                                      :query-params (:query-params @state)
                                      :on-done (fn [{:keys [total-count filtered-count results]}]
                                                 (swap! state assoc
                                                        :total-count total-count
                                                        :filtered-count filtered-count
                                                        :rows results
                                                        :loading? false))}))
   :get-initial-state
   (fn [{:keys [props]}]
     (assoc
      (persistence/try-restore
       {:key (:state-key props)
        :validator (fn [stored-value]
                     (or (not (:v props))
                         (= (:v props) (:v stored-value))))
        :initial
        #(let [columns (-> props :table :columns)
               initial-sort-column (or (first (filter :sort-initial columns))
                                       (first columns))
               initial-sort-order (get initial-sort-column :sort-initial :asc)]
           (merge
            {:query-params (select-keys
                            {:page-number 1
                             :rows-per-page 20
                             :filter-text ""
                             :sort-column (table-utils/resolve-id initial-sort-column)
                             :sort-order initial-sort-order}
                            (difference all-query-params (-> props :table :external-query-params)))
             :column-display (table-utils/build-column-display columns)}
            (when-let [v (:v props)] {:v v})))})
       :rows []))
   :render
   (fn [{:keys [props state]}]
     (let [props (utils/deep-merge default-props props)
           {:keys [rows column-display total-count filtered-count query-params]} @state
           {:keys [table toolbar paginator]} props
           {:keys [empty-message columns behavior fixed-column-count external-query-params]} table
           query-params (merge query-params (select-keys props external-query-params))
           update-column-display #(swap! state assoc :column-display %)]
       [:div {}
        (when (:loading? @state)
          [comps/DelayedBlocker {:banner "Loading..."}])
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
              [comps/TextFilter {:on-filter #(swap! state update :query-params assoc :filter-text %)}]]))
         (list* (:items toolbar))]
        [:div {:style {:overflowX "auto"}}
         (if (empty? rows)
           (style/create-message-well empty-message)
           [body/TableBody
            (merge
             table
             (select-keys query-params [:sort-column :sort-order])
             (utils/restructure rows column-display update-column-display)
             {:set-sort (fn [col order] (swap! state update :query-params
                                               merge {:sort-column col :sort-order order}))})])]
        (paginator/paginator
         (merge
          paginator
          (select-keys query-params [:rows-per-page :page-number])
          (utils/restructure total-count filtered-count)
          {:page-selected #(swap! state assoc-in [:query-params :page-number] %)
           :per-page-selected #(swap! state update :query-params
                                      merge {:rows-per-page % :page-number 1})}))]))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :refresh-rows))
   :component-did-update
   (fn [{:keys [props state prev-state this]}]
     (when-not (= (:query-params @state) (:query-params prev-state))
       (this :refresh-rows))
     (when (and (:state-key props)
                (or (not= (:query-params @state) (:query-params prev-state))
                    (not= (:column-display @state) (:column-display prev-state))))
       (persistence/save {:key (:state-key props)
                          :state state
                          :only [:query-params :column-display :v]})))})
