(ns broadfcui.common.table.table
  (:require
    [dmohs.react :as react]
    [broadfcui.common.table.body :as body]
    [broadfcui.common.table.paginator :as paginator]
    [broadfcui.utils :as utils]
    ))


(react/defc Table
  {:get-default-props
   (fn []
     {:paginator {:style {:marginTop "1rem"}
                  :per-page-options [10 20 100 500]}})
   :get-initial-state
   (fn [{:keys []}]
     {:query-params {:page-number 1
                     :rows-per-page 20
                     :filter-text ""
                     :sort-column nil
                     :sort-order nil}
      :rows []})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [rows query-params]} @state
           {:keys [columns]} props]
       [:div {}
        [:div {:style {:overflowX "auto"}}
         [body/TableBody (merge (select-keys query-params [:sort-column :sort-order])
                                (utils/restructure rows columns)
                                {})]
        (paginator/paginator (merge (select-keys query-params [:rows-per-page :page-number])
                                    (:paginator props)
                                    {:filtered-count (count rows)
                                     :total-count (count rows)
                                     :page-selected #(swap! state assoc-in [:query-params :page-number] %)
                                     :per-page-selected #(swap! state assoc-in [:query-params :rows-per-page] %)}))]]))
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
                            :on-done #(swap! state assoc :rows % :loading? false)}))})
