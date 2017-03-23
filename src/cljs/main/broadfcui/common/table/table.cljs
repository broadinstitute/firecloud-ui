(ns broadfcui.common.table.table
  (:require
    [dmohs.react :as react]
    [broadfcui.common.components :as comps]
    [broadfcui.common.table.body :as body]
    [broadfcui.common.table.column-editor :refer [ColumnEditButton]]
    [broadfcui.common.table.paginator :as paginator]
    [broadfcui.common.table.utils :as table-utils]
    [broadfcui.common.style :as style]
    [broadfcui.utils :as utils]
    ))


;; Define default props this way because we need to do a deep-merge,
;; instead of React's regular merge.
(def ^:private default-props
  {:table {:empty-message "There are no rows to display."
           :behavior {:reorderable-columns? true
                      :sortable-columns? true
                      :resizable-columns? true
                      :filterable? true}}
   :paginator {:style {:marginTop "1rem"}
               :per-page-options [10 20 100 500]}})


(react/defc Table
  {:get-initial-state
   (fn [{:keys [props]}]
     (let [columns (-> props :table :columns)
           initial-sort-column (or (first (filter :sort-initial columns))
                                   (first columns))
           initial-sort-order (get initial-sort-column :sort-initial :asc)]
       {:query-params {:page-number 1
                       :rows-per-page 20
                       :filter-text ""
                       :sort-column (table-utils/resolve-id initial-sort-column)
                       :sort-order initial-sort-order}
        :column-display (table-utils/build-column-display columns)
        :rows []}))
   :render
   (fn [{:keys [props state]}]
     (let [props (utils/deep-merge default-props props)
           {:keys [rows column-display total-count filtered-count query-params]} @state
           {:keys [table]} props
           {:keys [empty-message columns style behavior]} table
           update-column-display #(swap! state assoc :column-display %)]
       [:div {}
        [:div {:style (merge {:display "flex" :alignItems "baseline" :marginBottom "1rem"}
                             (-> props :toolbar :style))}
         (when (-> props :table :behavior :reorderable-columns?)
           [ColumnEditButton (utils/restructure columns column-display update-column-display)])
         (when (-> props :table :behavior :filterable?)
           [comps/TextFilter {:on-filter #(swap! state update :query-params assoc :filter-text %)}])
         (list* (-> props :toolbar :items))]
        [:div {:style {:overflowX "auto"}}
         (if (empty? rows)
           (style/create-message-well empty-message)
           [body/TableBody
            (utils/deep-merge
             (select-keys query-params [:sort-column :sort-order])
             (utils/restructure rows column-display columns style behavior update-column-display)
             {:set-sort (fn [col order] (swap! state update :query-params
                                               merge {:sort-column col :sort-order order}))})])]
        (paginator/paginator
         (utils/deep-merge
          (select-keys query-params [:rows-per-page :page-number])
          (:paginator props)
          (utils/restructure total-count filtered-count)
          {:page-selected #(swap! state assoc-in [:query-params :page-number] %)
           :per-page-selected #(swap! state update :query-params
                                      merge {:rows-per-page % :page-number 1})}))]))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :-refresh-rows!))
   :component-did-update
   (fn [{:keys [state prev-state this]}]
     (when-not (= (:query-params @state) (:query-params prev-state))
       (this :-refresh-rows!)))
   :-refresh-rows!
   (fn [{:keys [props state]}]
     (swap! state assoc :loading? true)
     ((-> props :table :data-source) {:columns (-> props :table :columns)
                                      :query-params (:query-params @state)
                                      :on-done (fn [{:keys [total-count filtered-count results]}]
                                                 (swap! state assoc
                                                        :total-count total-count
                                                        :filtered-count filtered-count
                                                        :rows results
                                                        :loading? false))}))})
