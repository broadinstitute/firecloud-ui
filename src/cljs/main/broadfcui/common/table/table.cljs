(ns broadfcui.common.table.table
  (:require
    [dmohs.react :as react]
    [broadfcui.common.table.body :as body]
    [broadfcui.common.table.paginator :as paginator]
    [broadfcui.common.table.utils :as table-utils]
    [broadfcui.utils :as utils]
    ))


(react/defc Table
  {:merge-query-params
   (fn [{:keys [state]} new-params]
     (swap! state update :query-params merge new-params))
   :get-query-params
   (fn [{:keys [state]}]
     (:query-params @state))
   :get-default-props
   (fn []
     {:behavior {:resizable-columns? true
                 :sortable-columns? true}
      :paginator {:style {:marginTop "1rem"}
                  :per-page-options [10 20 100 500]}})
   :get-initial-state
   (fn [{:keys [props]}]
     (let [initial-sort-column (or (first (filter :sort-initial (:columns props)))
                                   (first (:columns props)))
           initial-sort-order (get initial-sort-column :sort-initial :asc)]
       {:query-params {:page-number 1
                       :rows-per-page 20
                       :filter-text ""
                       :sort-column (table-utils/resolve-id initial-sort-column)
                       :sort-order initial-sort-order}
        :rows []}))
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [rows total-count query-params]} @state
           {:keys [columns style behavior]} props]
       [:div {}
        [:div {:style {:overflowX "auto"}}
         [body/TableBody
          (utils/deep-merge
           (select-keys query-params [:sort-column :sort-order])
           (utils/restructure rows columns style behavior)
           {:set-sort (fn [col order] (swap! state update :query-params
                                             merge {:sort-column col :sort-order order}))})]
        (paginator/paginator
         (utils/deep-merge
          (select-keys query-params [:rows-per-page :page-number])
          (:paginator props)
          {:filtered-count total-count
           :total-count total-count
           :page-selected #(swap! state assoc-in [:query-params :page-number] %)
           :per-page-selected #(swap! state update :query-params
                                      merge {:rows-per-page % :page-number 1})}))]]))
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
     ((:data-source props) {:columns (:columns props)
                            :query-params (:query-params @state)
                            :on-done (fn [{:keys [total-count results]}]
                                       (swap! state assoc
                                              :total-count total-count
                                              :rows results
                                              :loading? false))}))})
