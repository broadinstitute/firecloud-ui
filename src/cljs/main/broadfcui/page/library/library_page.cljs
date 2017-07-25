(ns broadfcui.page.library.library-page
  (:require
   [dmohs.react :as react]
   [clojure.set :as set]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.common.table.table :refer [Table]]
   [broadfcui.config :as config]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.persistence :as persistence]
   [broadfcui.utils :as utils]
   ))


(react/defc- DatasetsTable
  {:render
   (fn [{:keys [state this props]}]
     (let [attributes (:library-attributes props)
           search-result-columns (:search-result-columns props)
           extra-columns (subvec search-result-columns 4)]
       [Table
        {:ref "table" :persistence-key "library-table" :v 4
         :fetch-data (this :pagination)
         :body
         {:behavior {:allow-no-sort? true
                     :fixed-column-count 2}
          :external-query-params #{:filter-text}
          :filter-text (:filter-text props)
          :columns (concat
                    [{:id "access" :hidden? true :resizable? false :sortable? false :initial-width 12
                      :as-text (fn [data]
                                 (when (= (:workspaceAccess data) "NO ACCESS")
                                   "You must request access to this dataset."))
                      :render (fn [data]
                                (when (= (:workspaceAccess data) "NO ACCESS")
                                  (icons/icon (merge
                                               {:style {:alignSelf "center" :cursor "pointer"}}
                                               (this :-get-link-props data))
                                              :shield)))}
                     {:id "library:datasetName"
                      :header (:title (:library:datasetName attributes)) :initial-width 250
                      :sort-initial :asc :sort-by :library:datasetName
                      :as-text :library:datasetDescription
                      :render (fn [data]
                                (style/create-link (merge {:text (:library:datasetName data)}
                                                          (this :-get-link-props data))))}
                     {:id "library:indication" :header (:title (:library:indication attributes))
                      :column-data :library:indication :initial-width 180}
                     {:id "library:dataUseRestriction" :header (:title (:library:dataUseRestriction attributes))
                      :column-data :library:dataUseRestriction :initial-width 180}
                     {:id "library:numSubjects" :header (:title (:library:numSubjects attributes))
                      :column-data :library:numSubjects :initial-width 100}]
                    (map
                     (fn [keyname]
                       {:id (name keyname) :header (:title (keyname attributes))
                        :initial-width 180 :show-initial? false
                        :column-data keyname
                        :render (fn [field]
                                  (if (sequential? field)
                                    (string/join ", " field)
                                    field))})
                     extra-columns))
          :style {:header-row {:fontWeight 500 :fontSize "90%"
                               :backgroundColor nil
                               :color "black"
                               :borderBottom (str "2px solid " (:border-light style/colors))}
                  :resize-tab (table-style/tab :border-light)
                  :body {:fontSize "87.5%" :marginTop 4 :color (:text-light style/colors)}
                  :cell table-style/clip-text
                  :header-cell {:padding "0.5rem 0 0.5rem 1rem"}
                  :body-cell {:padding "0.3rem 0 0.3rem 1rem"}}}
         :toolbar ;; FIXME: magic numbers below:
         {:get-items (constantly
                  [[:div {:style {:fontSize "112%"}}
                    ;; 112% makes this the same size as "Data Library" / "Workspaces" / "Method Repository" above
                    [:span {:style {:fontWeight 700 :color (:text-light style/colors) :marginRight "0.5rem"}}
                     "Matching Cohorts"]
                    [:span {:style {:fontSize "80%"}}
                     (let [total (or (:total @state) 0)]
                       (str total
                            " Dataset"
                            (when-not (= 1 total) "s")
                            " found"))]]
                   flex/spring])
          :style {:alignItems "flex-start" :marginBottom 7} ;; 7 makes some lines line up
          :column-edit-button {:style {:order 1 :marginRight nil}
                               :anchor :right}}}]))
   :execute-search
   (fn [{:keys [refs]} reset-sort?]
     (let [query-params (merge {:page-number 1} (when reset-sort? {:sort-column nil :sort-order nil}))]
       (when-not ((@refs "table") :update-query-params query-params)
         ((@refs "table") :refresh-rows))))
   :-get-link-props
   (fn [_ data]
     (if (= (:workspaceAccess data) "NO ACCESS")
       {:onClick
        (fn [_]
          (comps/push-message
           {:header "Request Access"
            :message
            (if (= (config/tcga-namespace) (:namespace data))
              [:span {}
               [:p {} "For access to TCGA protected data please apply for access via dbGaP [instructions can be found "
                [:a {:href "https://wiki.nci.nih.gov/display/TCGA/Application+Process"
                     :target "_blank"}
                 "here" icons/external-link-icon] "]."]
               [:p {} "After dbGaP approves your application please link your eRA Commons ID in your FireCloud profile page."]]
              [:span {}
               "Please contact "
               [:a {:target "_blank"
                    :href (str "mailto:" (:library:contactEmail data))}
                (:library:datasetCustodian data) " <" (:library:contactEmail data) ">"
                (icons/icon {:style {:paddingLeft "0.25rem" :fontSize "80%"}} :email)]
               " and request access for the "
               (:namespace data) "/" (:name data) " workspace."])}))}
       {:href (nav/get-link :workspace-summary (common/row->workspace-id data))}))
   :build-aggregate-fields
   (fn [{:keys [props]}]
     (reduce
      (fn [results field] (assoc results field (if (contains? (:expanded-aggregates props) field) 0 5)))
      {}
      (:aggregate-fields props)))
   :pagination
   (fn [{:keys [this state props]}]
     (fn [{:keys [query-params on-done]}]
       (let [{:keys [page-number rows-per-page sort-column sort-order]} query-params]
         (when-not (empty? (:aggregate-fields props))
           (endpoints/call-ajax-orch
            (let [from (* (- page-number 1) rows-per-page)
                  update-aggregates? (or (= 1 page-number) (:no-aggregates? props))]
              {:endpoint endpoints/search-datasets
               :payload {:searchString (:filter-text props)
                         :filters ((:get-facets props))
                         :from from
                         :size rows-per-page
                         :sortField sort-column
                         :sortDirection sort-order
                         :fieldAggregations (if update-aggregates?
                                              (this :build-aggregate-fields)
                                              {})}
               :headers utils/content-type=json
               :on-done
               (fn [{:keys [success? get-parsed-response status-text]}]
                 (if success?
                   (let [{:keys [total results aggregations]} (get-parsed-response)]
                     (swap! state assoc :total total)
                     (on-done {:total-count total
                               :filtered-count total
                               :results results})
                     (when update-aggregates?
                       ((:update-aggregates props) aggregations)))
                   (on-done {:error status-text})))}))))))})

(defn- encode [text]
  ;; character replacements modeled after Lucene's SimpleHTMLEncoder.
  (string/escape text {\" "&quot;" \& "&amp;" \< "&lt;", \> "&gt;", \\ "&#x27;" \/ "&#x2F;"}))

(defn- highlight-suggestion [suggestion highlight]
  (if (not (string/blank? highlight))
    (string/replace (encode suggestion) (encode highlight) (str "<strong>" (encode highlight) "</strong>"))
    (encode suggestion)))

(react/defc- SearchSection
  {:get-filters
   (fn [{:keys [props]}]
     (utils/map-keys name (:facet-filters props)))
   :render
   (fn [{:keys [props this]}]
     [:div {:style {:padding "16px 12px 0 12px"}}
      [comps/AutocompleteFilter
       {:ref "text-filter"
        :on-filter (:on-filter props)
        :typeahead-events ["typeahead:select"]
        :width "100%"
        :field-attributes {:defaultValue (:search-text props)
                           :placeholder "Search"}
        :facet-filters (:facet-filters props)
        :bloodhoundInfo {:url (str (config/api-url-root) "/api/library/suggest")
                         :transform (fn [response]
                                      (clj->js
                                       (mapv (partial hash-map :value) (aget response "results"))))
                         :cache false
                         :prepare (fn [query settings]
                                    (clj->js
                                     (assoc (js->clj settings)
                                       :headers {:Authorization (str "Bearer " (utils/get-access-token))}
                                       :type "POST"
                                       :contentType "application/json; charset=UTF-8"
                                       :data (utils/->json-string
                                              {:searchString query
                                               :filters (this :get-filters)
                                               :from 0
                                               :size 10}))))}
        :typeaheadDisplay (fn [result]
                            ;; underlying typeahead library uses the result of this function
                            ;; via $input.val(x), which is safe from xss. So we explicitly
                            ;; do not want to encode anything here.
                            (aget result "value" "suggestion"))
        :typeaheadSuggestionTemplate (fn [result]
                                       (let [suggestion (aget result "value" "suggestion")
                                             highlight (aget result "value" "highlight")
                                             display (highlight-suggestion suggestion highlight)]
                                         (str "<div style='textOverflow: ellipsis; overflow: hidden; font-size: smaller;'>" display "</div>")))}]])})

(react/defc- FacetCheckboxes
  {:render
   (fn [{:keys [props this]}]
     (let [size (:numOtherDocs props)
           title (:title props)
           all-buckets (mapv :key (:buckets props))
           hidden-items (set/difference (:selected-items props) (set all-buckets))
           hidden-items-formatted (mapv (fn [item] {:key item}) hidden-items)]
       [:div {:style {:paddingBottom "1em"}}
        [:hr {}]
        [:span {:style {:fontWeight "bold"}} title]
        [:div {:style {:fontSize "80%" :float "right"}}
         (style/create-link {:text "Clear" :onClick #(this :clear-all)})]
        [:div {:style {:paddingTop "1em"}}
         (map
          (fn [{:keys [key doc_count]}]
            [:div {:style {:paddingTop 5}}
             [:label {:style {:display "inline-block" :width "calc(100% - 30px)"
                              :textOverflow "ellipsis" :overflow "hidden" :whiteSpace "nowrap"}
                      :title key}
              [:input {:type "checkbox"
                       :checked (contains? (:selected-items props) key)
                       :onChange #(this :update-selected key (.. % -target -checked))}]
              [:span {:style {:marginLeft "0.3em"}} key]]
             (some-> doc_count style/render-count)])
          (concat (:buckets props) hidden-items-formatted))
         [:div {:style {:paddingTop 5}}
          (if (:expanded? props)
            (when (> (count (:buckets props)) 5)
              (style/create-link {:text " less..."
                                  :onClick #(this :update-expanded false)}))
            (when (> size 0)
              (style/create-link {:text " more..."
                                  :onClick #(this :update-expanded true)})))]]]))
   :clear-all
   (fn [{:keys [props]}]
     ((:update-filter props) (:field props) #{}))
   :update-expanded
   (fn [{:keys [props]} newValue]
     ((:expanded-callback-function props) (:field props) newValue))
   :update-selected
   (fn [{:keys [props]} name checked?]
     (let [updated-items ((if checked? conj disj) (:selected-items props) name)]
       ((:update-filter props) (:field props) updated-items)))})

(defn get-aggregations-for-property [aggregate-field aggregates]
  (first (keep (fn [m] (when (= (:field m) (name aggregate-field))
                         (:results m)))
               aggregates)))

(react/defc- Facet
  {:render
   (fn [{:keys [props]}]
     (let [aggregate-field (:aggregate-field props)
           properties (:aggregate-properties props)
           title (:title properties)
           render-hint (get-in properties [:aggregate :renderHint])
           aggregations (get-aggregations-for-property aggregate-field (:aggregates props))]
       (cond
         (= render-hint "checkbox") [FacetCheckboxes
                                     (merge
                                      {:title title :field aggregate-field}
                                      (select-keys aggregations [:numOtherDocs :buckets])
                                      (select-keys props [:expanded? :selected-items :update-filter
                                                          :expanded-callback-function]))])))})

(react/defc- FacetSection
  {:render
   (fn [{:keys [props]}]
     (if (empty? (:aggregates props))
       [:div {:style {:fontSize "80%"}} "loading..."]
       [:div {:style {:fontSize "85%" :padding "16px 12px"}}
        (map
         (fn [aggregate-field] [Facet {:aggregate-field aggregate-field
                                       :aggregate-properties (get (:aggregate-properties props) aggregate-field)
                                       :aggregates (:aggregates props)
                                       :expanded? (contains? (:expanded-aggregates props) aggregate-field)
                                       :selected-items (set (get-in props [:facet-filters aggregate-field]))
                                       :update-filter (:update-filter props)
                                       :expanded-callback-function (:expanded-callback-function props)}])
         (:aggregate-fields props))]))})

(def ^:private PERSISTENCE-KEY "library-page")
(def ^:private VERSION 4)

(react/defc- Page
  (->>
   {:update-filter
    (fn [{:keys [state after-update refs]} facet-name facet-list]
      (if (empty? facet-list)
        (swap! state update :facet-filters dissoc facet-name)
        (swap! state assoc-in [:facet-filters facet-name] facet-list))
      (after-update #((@refs "dataset-table") :execute-search false)))
    :set-expanded-aggregate
    (fn [{:keys [state refs after-update]} facet-name expanded?]
      (swap! state update :expanded-aggregates (if expanded? conj disj) facet-name)
      (after-update #((@refs "dataset-table") :execute-search false)))
    :component-did-mount
    (fn [{:keys [state]}]
      (endpoints/get-library-attributes
       (fn [{:keys [success? get-parsed-response]}]
         (if success?
           (let [{:keys [properties searchResultColumns]} (get-parsed-response)
                 aggs (->> properties (utils/filter-values :aggregate) keys)
                 facets (:facet-filters @state)]
             (swap! state assoc
                    :library-attributes properties
                    :aggregate-fields aggs
                    :facet-filters (select-keys facets aggs)
                    :search-result-columns (mapv keyword searchResultColumns)))))))
    :render
    (fn [{:keys [this refs state after-update]}]
      ;; TODO: Refactor this to use filter.cljs
      [:div {:style {:display "flex" :margin "1.5rem 1rem 0"}}
       [:div {:style {:width 260 :marginRight "2em"
                      :background (:background-light style/colors)
                      :border style/standard-line}}
        [SearchSection {:search-text (:search-text @state)
                        :facet-filters (:facet-filters @state)
                        :on-filter (fn [text]
                                     (swap! state assoc :search-text text)
                                     (after-update #((@refs "dataset-table") :execute-search true)))}]
        [FacetSection (merge
                       {:ref "facets"
                        :aggregates (:aggregates @state)
                        :aggregate-properties (:library-attributes @state)
                        :update-filter (fn [facet-name facet-list]
                                         (this :update-filter facet-name facet-list))
                        :expanded-callback-function (fn [facet-name expanded?]
                                                      (this :set-expanded-aggregate facet-name expanded?))}
                       (select-keys @state [:aggregate-fields :facet-filters :expanded-aggregates]))]]
       [:div {:style {:flex "1 1 auto" :overflowX "auto"}}
        (when (and (:library-attributes @state) (:search-result-columns @state))
          [DatasetsTable (merge
                          {:ref "dataset-table"
                           :filter-text (:search-text @state)
                           :update-aggregates #(swap! state assoc :aggregates %)
                           :no-aggregates? (empty? (:aggregates @state))
                           :get-facets #(utils/map-keys name (:facet-filters @state))}
                          (select-keys @state [:library-attributes :search-result-columns :aggregate-fields :expanded-aggregates]))])]])}
   (persistence/with-state-persistence {:key PERSISTENCE-KEY :version VERSION
                                        :initial {:search-text ""
                                                  :facet-filters {}
                                                  :expanded-aggregates #{}}
                                        :except [:library-attributes :aggregates]})))

(defn add-nav-paths []
  (nav/defpath
   :library
   {:component Page
    :regex #"library"
    :make-props (fn [_] {})
    :make-path (fn [] "library")}))
