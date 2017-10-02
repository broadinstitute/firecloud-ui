(ns broadfcui.common.table
  (:require
   [dmohs.react :as react]
   [clojure.set :as set]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.common.table.body :as body]
   [broadfcui.common.table.column-editor :refer [ColumnEditButton]]
   [broadfcui.common.table.paginator :refer [Paginator]]
   [broadfcui.common.table.utils :as table-utils]
   [broadfcui.config :as config]
   [broadfcui.persistence :as persistence]
   [broadfcui.utils :as utils]
   ))

;; Documentation:
;; https://broadinstitute.atlassian.net/wiki/display/GAWB/The+Table+UI+component


;; Define nested default props this way because we need to do a deep-merge,
;; instead of React's regular merge.
(def ^:private default-props
  {:blocker-delay-time-ms 200
   :tabs {:render (fn [label count] (str label " (" count ")"))}
   :body {:empty-message "There are no rows to display."
          :external-query-params #{}
          :behavior {:reorderable-columns? true
                     :fixed-column-count 0
                     :sortable-columns? true
                     :allow-no-sort? false
                     :resizable-columns? true
                     :filterable? true}}
   :toolbar {:style {:display "flex" :alignItems "baseline"}
             :column-edit-button {:style {:marginRight "1rem"}
                                  :button {:text "Columns" :icon :settings
                                           :style {:padding "0.4rem 0.8rem 0.4rem 0.4rem"}}
                                  :anchor :left}
             :filter-bar {:style {:marginRight "1rem"}}}
   :paginator {:style {:marginTop "1.5rem"}
               :width-threshold 700
               :per-page-options [10 20 100 500]}})

(def ^:private all-query-params #{:page-number :rows-per-page :filter-text :sort-column :sort-order})


;; !!!!!!!!!!!!!
;; !! WARNING !!
;; !!!!!!!!!!!!!
;;
;; Tabs are broken for a particular combination of properties. See the documentation for more details.
;;
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
     (swap! state assoc :data-test-state "loading")
     ((@refs "blocker") :show)
     (let [{:keys [data fetch-data]} props
           data-source (if data (table-utils/local data) fetch-data)
           query-params (merge (:query-params @state)
                               (when reset-page-number? {:page-number 1}))]
       (assert data-source "No data provided")
       (data-source {:columns (-> props :body :columns)
                     :tab (some-> (:tabs props) :items (get (:selected-tab-index @state)))
                     :query-params query-params
                     :on-done (fn [{:keys [total-count tab-count filtered-rows results]}]
                                ((@refs "blocker") :hide)
                                (swap! state merge
                                       {:data-test-state "ready"
                                        :rows results
                                        :tab-count (or tab-count total-count)}
                                       (utils/restructure total-count filtered-rows query-params)))})))
   :get-default-props
   (fn []
     {:load-on-mount true})
   :get-initial-state
   (fn [{:keys [this]}]
     (assoc (this :-fetch-initial-state)
       :rows []
       :data-test-state "initializing"))
   :render
   (fn [{:keys [props state]}]
     (let [props (utils/deep-merge default-props props)
           {:keys [data-test-state rows column-display tab-count query-params selected-tab-index filtered-rows]} @state
           {:keys [data-test-id toolbar sidebar tabs body paginator style]} props
           {:keys [empty-message columns behavior external-query-params on-column-change]} body
           {:keys [fixed-column-count allow-no-sort?]} behavior
           total-count (some :total-count [props @state])
           query-params (merge query-params (select-keys props external-query-params))
           update-column-display (fn [columns]
                                   (when on-column-change (on-column-change columns))
                                   (swap! state assoc :column-display columns))]
       [:div {:data-test-id data-test-id
              :data-test-state data-test-state
              :style (merge {:position "relative"} (:main style))}
        [comps/DelayedBlocker {:ref "blocker" :banner "Loading..."
                               :delay-time-ms (:blocker-delay-time-ms props)}]
        [:div {:style (merge {:marginBottom (if tabs "0.3rem" "1rem")}
                             (:style toolbar))}
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
                                 {:data-test-id "filter"
                                  :initial-text (:filter-text query-params)
                                  :on-filter #(swap! state update :query-params assoc :filter-text % :page-number 1)}
                                 (:inner filter-bar-props))]]))
         (when-let [get-items (:get-items toolbar)]
           (list* (get-items {:columns column-display})))]
        [:div {:style (merge {:display "flex"} (:content+sidebar style))}
         sidebar
         [:div {:style (merge {:flex "1 1 0" :minWidth 0} (:content style))}
          (when tabs
            (let [tab-counts (table-utils/compute-tab-counts {:tabs tabs :rows filtered-rows})]
              [:div {:style (merge {:marginBottom "0.3rem"}
                                   (:style tabs))}
               (map-indexed (fn [index {:keys [label size] :as tab}]
                              (let [selected? (= index selected-tab-index)
                                    tab-count (get tab-counts label)]
                                [:div {:data-test-id (str label "-tab")
                                       :style {:display "inline-block" :textAlign "center"
                                               :padding "0.5rem 1rem" :cursor "pointer"
                                               :fontWeight (when selected? 500)
                                               :letterSpacing (when-not selected? "0.007em") ; stops size from shifting when selected
                                               :borderBottom (when selected? (str "3px solid " (:button-primary style/colors)))
                                               :marginBottom (when-not selected? 3)}
                                       :onClick (fn []
                                                  (swap! state assoc :selected-tab-index index)
                                                  (when-let [f (:on-tab-selected tabs)]
                                                    (f tab)))}
                                 ((:render tabs) label (or size tab-count (count filtered-rows)))]))
                            (:items tabs))]))
          (if (empty? rows)
            (style/create-message-well empty-message)
            [body/TableBody
             (merge
              body
              (select-keys query-params [:sort-column :sort-order])
              (utils/restructure rows column-display update-column-display fixed-column-count allow-no-sort?)
              {:set-sort (fn [col order] (swap! state update :query-params
                                                merge {:sort-column col :sort-order order}))})])
          (when (not= paginator :none)
            [Paginator
             (merge paginator
                    (select-keys query-params [:rows-per-page :page-number])
                    (utils/restructure total-count tab-count)
                    {:page-selected #(swap! state assoc-in [:query-params :page-number] %)
                     :per-page-selected #(swap! state update :query-params
                                                merge {:rows-per-page % :page-number 1})})])]]]))
   :component-did-mount
   (fn [{:keys [props this]}]
     (when (:load-on-mount props)
       (this :refresh-rows)))
   :component-did-update
   (fn [{:keys [props state prev-props prev-state this]}]
     (let [data-change? (not= (:data props) (:data prev-props))
           [query-params-change? selected-tab-change? column-display-change?]
           (utils/changes [:query-params :selected-tab-index :column-display] @state prev-state)]
       (when (or query-params-change? selected-tab-change? data-change?)
         (this :refresh-rows (or data-change? selected-tab-change?)))
       (when (and (:persistence-key props)
                  (or query-params-change? selected-tab-change? column-display-change?))
         (persistence/save {:key (:persistence-key props)
                            :state state
                            :only [:query-params :column-display :selected-tab-index :v]}))))
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
                                         (first (filter #(get % :sortable? true) processed-columns))))
               initial-sort-order (some-> initial-sort-column (get :sort-initial :asc))]
           (merge
            {:query-params (select-keys
                            {:page-number 1
                             :rows-per-page (if (= :none (:paginator props)) Infinity 20)
                             :filter-text ""
                             :sort-column (some-> initial-sort-column table-utils/resolve-id)
                             :sort-order initial-sort-order}
                            (set/difference all-query-params (-> props :body :external-query-params)))
             :column-display (table-utils/build-column-display processed-columns)}
            (when-let [tabs (:tabs props)]
              {:selected-tab-index (or (some-> (:initial-selection tabs) (utils/first-matching-index (:items tabs)))
                                       0)})
            (when-let [v (:v props)] {:v v}))))}))})
