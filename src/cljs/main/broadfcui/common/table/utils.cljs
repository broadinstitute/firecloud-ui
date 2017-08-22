(ns broadfcui.common.table.utils
  (:require
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.gcs-file-preview :refer [GCSFilePreviewLink]]
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
  (map (fn [column]
         (let [func (or (:as-text column) str)
               column-data-fn (or (:column-data column) identity)
               column-data (column-data-fn row)]
           (func column-data)))
       columns))

(defn- apply-tab [{:keys [predicate]} data]
  (if predicate
    (filter predicate data)
    data))

(defn- matches-filter-text [filter-tokens source]
  (let [lc-source (string/lower-case source)]
    (every? (fn [word] (utils/contains lc-source word)) filter-tokens)))

(defn- filter-rows [{:keys [filter-text]} columns data]
  (if (string/blank? filter-text)
    data
    (let [filter-tokens (string/split (string/lower-case filter-text) #"\s+")
          filterable-columns (filter #(get % :filterable? true) columns)]
      (filter (fn [row]
                (some (partial matches-filter-text filter-tokens)
                      (row->text row filterable-columns)))
              data))))

(defn- sort-rows [{:keys [sort-column sort-order]} columns data]
  (if sort-column
    (let [column (find-by-id sort-column columns)
          column-data (or (:column-data column) identity)
          sorter (let [sort-by (:sort-by column)]
                   (cond (= sort-by :text) (:as-text column)
                         (nil? sort-by) identity
                         :else sort-by))
          sorted (sort-by (comp sorter column-data) data)]
      (if (= sort-order :desc)
        (reverse sorted)
        sorted))
    data))

(defn- trim-rows [{:keys [page-number rows-per-page]} data]
  (->> data
       (drop (* rows-per-page (dec page-number)))
       (take rows-per-page)))

(defn local
  "Create a data source from a local sequence"
  [data & [total-count]]
  (fn [{:keys [columns tab query-params on-done]}]
    (let [filtered (filter-rows query-params columns data)
          tabbed (apply-tab tab filtered)
          displayed (->> tabbed
                         (sort-rows query-params columns)
                         (trim-rows query-params))]
      (on-done
       {:total-count (or total-count (count data))
        :filtered-rows filtered
        :tab-count (count tabbed)
        :results displayed}))))


(defn compute-tab-counts
  "Compute the number of items in each tab"
  [{:keys [tabs rows]}]
  (->> (:items tabs)
       ;; Ignore ones that have an explicit size (we wouldn't use the result anyway)
       (remove :size)
       (map (fn [{:keys [predicate label]}]
              [label (if predicate
                       (->> rows (filter predicate) count)
                       (count rows))]))
       (into {})))


(defn build-column-display [user-columns]
  (mapv (fn [column]
          {:id (resolve-id column)
           :width (get column :initial-width 100)
           :visible? (get column :show-initial? true)})
        user-columns))


(defn date-column [props]
  (merge {:header "Create Date"
          :initial-width 200
          :as-text #(common/format-date % (:format props))}
         props))


(defn default-render [data]
  (cond (map? data) (utils/map-to-string data)
        (sequential? data) (string/join ", " data)
        :else (str data)))

(defn render-gcs-links [workspace-bucket]
  (fn [maybe-uri]
    (if (string? maybe-uri)
      (if-let [parsed (common/parse-gcs-uri maybe-uri)]
        [GCSFilePreviewLink
         (assoc parsed
           :workspace-bucket workspace-bucket
           :attributes {:style {:direction "rtl" :marginRight "0.5em"
                                :overflow "hidden" :textOverflow "ellipsis"
                                :textAlign "left"}})]
        maybe-uri)
      (default-render maybe-uri))))
