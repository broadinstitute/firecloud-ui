(ns broadfcui.common.table.utils
  (:require
    [broadfcui.utils :as utils]
    ))


(defn- filter-rows [{:keys [filter-text]} columns data]
  ;; TODO
  data)

(defn- sort-rows [{:keys [sort-column sort-order]} columns data]
  (if-not sort-column
    data
    (let [sorted (sort-by (get-in columns [sort-column :sort-fn]) data)]
      (if (= sort-order :desc)
        (reverse sorted)
        sorted))))

(defn- trim-rows [{:keys [page-number rows-per-page]} data]
  (->> data
       (drop (* rows-per-page (dec page-number)))
       (take rows-per-page)))

(defn local
  "Create a data source from a sequence, given the column definitions"
  [{:keys [columns data]}]
  (fn [{:keys [query-params on-done]}]
    (on-done
     (->> data
          (filter-rows query-params columns)
          (sort-rows query-params columns)
          (trim-rows query-params)))))
