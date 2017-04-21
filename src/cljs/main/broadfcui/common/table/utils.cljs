(ns broadfcui.common.table.utils
  (:require
    [broadfcui.utils :as utils]
    ))


(defn resolve-id [{:keys [header id]}]
  (assert (or (not id) (string? id)) "Column id must be a string, if present")
  (assert (or id (string? header)) "Every column must have a string header or id")
  (or id header))


(defn canonical-name [{:keys [header id]}]
  (if (string? header) header id))


(defn index-by-id [column-definitions]
  (utils/index-by resolve-id column-definitions))


(defn find-by-id [id column-definitions]
  (first (filter (comp (partial = id) resolve-id) column-definitions)))


(defn resolve-canonical-name [id column-definitions]
  (canonical-name (find-by-id id column-definitions)))


(defn- row->text [row columns]
  (keep (fn [column]
          (when (get column :filterable? true)
            (let [func (or (:as-text column) str)
                  column-data-fn (or (:column-data column) identity)
                  column-data (column-data-fn row)]
              (func column-data))))
        columns))

(defn- filter-rows [{:keys [filter-text]} columns data]
  (if (clojure.string/blank? filter-text)
    data
    (filter (fn [row]
              (some (partial utils/matches-filter-text filter-text)
                    (row->text row columns)))
            data)))

(defn- sort-rows [{:keys [sort-column sort-order]} columns data]
  (let [column (find-by-id sort-column columns)
        column-data (or (:column-data column) identity)
        sorter (let [sort-by (:sort-by column)]
                 (cond (= sort-by :text) (:as-text column)
                       (nil? sort-by) identity
                       :else sort-by))
        sorted (sort-by (comp sorter column-data) data)]
    (if (= sort-order :desc)
      (reverse sorted)
      sorted)))

(defn- trim-rows [{:keys [page-number rows-per-page]} data]
  (->> data
       (drop (* rows-per-page (dec page-number)))
       (take rows-per-page)))

(defn local
  "Create a data source from a local sequence"
  [data & [total-count]]
  (fn [{:keys [columns query-params on-done]}]
    (let [filtered (filter-rows query-params columns data)
          displayed (->> filtered
                         (sort-rows query-params columns)
                         (trim-rows query-params))]
      (on-done
       {:total-count (or total-count (count data))
        :filtered-count (count filtered)
        :results displayed}))))


(defn build-column-display [user-columns]
  (mapv (fn [column]
          {:id (resolve-id column)
           :width (get column :initial-width 100)
           :visible? (get column :show-initial? true)})
        user-columns))
