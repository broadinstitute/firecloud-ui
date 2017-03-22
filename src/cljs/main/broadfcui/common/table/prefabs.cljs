(ns broadfcui.common.table.prefabs
  (:require
    [dmohs.react :as react]
    [broadfcui.common.components :as comps]
    [broadfcui.common.table.table :as table]
    [broadfcui.common.table.style :as table-style]
    [broadfcui.utils :as utils]
    ))


(react/defc LightTable
  {:get-default-props
   (fn []
     {:filterable? true
      :reorderable-columns? false})
   :render
   (fn [{:keys [props refs]}]
     [:div {}
      [:div {:style (merge {:display "flex" :alignItems "baseline" :marginBottom "1rem"}
                           (-> props :toolbar :style))}
       (when (:filterable? props)
         [comps/TextFilter {:ref "filter"
                            :on-filter #((@refs "table") :merge-query-params {:filter-text %})}])
       (list* (:toolbar-items props))]
      [table/Table (merge {:ref "table"
                           :style table-style/table-light}
                          (:table props))]])
   :component-did-mount
   (fn [{:keys [refs]}]
     ((@refs "filter") :set-text (:filter-text ((@refs "table") :get-query-params))))})
