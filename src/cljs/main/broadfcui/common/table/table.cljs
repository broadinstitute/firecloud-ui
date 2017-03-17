(ns broadfcui.common.table.table
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.style :as style]
    [broadfcui.common.table.components :as tc]
    [broadfcui.persistence :as persistence]
    [broadfcui.utils :as utils]
    ))


(react/defc Table
  {:render
   (fn [{:keys [props state this]}]
     (let [{:keys [rows]} @state
           columns (this :-build-columns)]
       [:div {}
        (tc/table-header (utils/restructure columns))
        (tc/table-body (utils/restructure rows columns))]))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :-refresh-rows!))
   :component-did-update
   (fn [{:keys [state prev-state this]}]
     (when-not (= (:query-params @state) (:query-params prev-state))
       (this :-refresh-rows!)))
   :-build-columns
   (fn [{:keys [props state]}])
   :-refresh-rows!
   (fn [{:keys [props state]}]
     ((:data-source props) {:query-params (:query-params @state)
                            :on-done #(swap! state assoc :rows %)}))})
