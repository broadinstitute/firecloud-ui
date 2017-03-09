(ns broadfcui.page.library.library_page
  (:require
    [clojure.set]
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.common.style :as style]
    [broadfcui.common.table :as table]
    [broadfcui.common.table-utils :refer [flex-strut]]
    [broadfcui.config :as config]
    [broadfcui.nav :as nav]
    [broadfcui.persistence :as persistence]
    [broadfcui.utils :as utils]
    [broadfcui.common.icons :as icons]
    ))


(react/defc DatasetsTable
  {:set-filter-text
   (fn [{:keys [refs]} new-filter-text]
     (react/call :update-query-params (@refs "table") {:filter-text new-filter-text :current-page 1}))
   :render
   (fn [{:keys [state this props]}]
     (let [attributes (:library-attributes props)
           search-result-columns (:search-result-columns props)
           extra-columns (subvec search-result-columns 4)]
       [table/Table
        {:ref "table" :state-key "library-table" :v 2
         :header-row-style {:fontWeight 500 :fontSize "90%"
                            :backgroundColor nil
                            :color "black"
                            :borderBottom (str "2px solid " (:border-light style/colors))}
         :header-style {:padding "0.5em 0 0.5em 1em"}
         :resizable-columns? true
         :sortable-columns? false
         :filterable? false
         :resize-tab-color (:border-light style/colors)
         :reorder-anchor :right
         :reorder-style {:width "300px" :whiteSpace "nowrap" :overflow "hidden" :textOverflow "ellipsis"}
         :reorder-prefix "Columns"
         :toolbar
         (fn [{:keys [reorderer]}]
           [:div {:style {:display "flex" :alignItems "top"}}
            [:div {:style {:fontSize "112%" :marginBottom "1em"}}
             [:span {:style {:fontWeight 700 :color (:text-light style/colors) :marginRight "0.5rem"}}
              "Matching Cohorts"]
             [:span {:style {:fontSize "80%"}}
              (let [total (or (:total @state) 0)]
                (str total
                     " Dataset"
                     (when-not (= 1 total) "s")
                     " found"))]]
            flex-strut
            reorderer])
         :body-style {:fontSize "87.5%" :fontWeight nil :marginTop 4
                      :color (:text-light style/colors)}
         :row-style {:backgroundColor nil :height 20 :padding "0 0 0.5em 1em"}
         :cell-content-style {:padding nil}
         :columns (concat
                   [{:resizable? false :width 30 :reorderable? false
                     :as-text (fn [data]
                                (if (= (:workspaceAccess data) "NO ACCESS") "You must request access to this dataset."))
                     :content-renderer (fn [data]
                                         (if (= (:workspaceAccess data) "NO ACCESS")
                                           (icons/icon {:style {:alignSelf "center" :cursor "pointer"}
                                                        :onClick #(react/call :check-access this data)} :shield)))}
                    {:header (:title (:library:datasetName attributes)) :starting-width 250 :show-initial? true
                     :as-text :library:datasetDescription :reorderable? false
                     :content-renderer (fn [data]
                                         (style/create-link {:text (:library:datasetName data)
                                                             :onClick #(react/call :check-access this data)}))}
                    {:header (:title (:library:indication attributes)) :starting-width 180 :show-initial? true}
                    {:header (:title (:library:dataUseRestriction attributes)) :starting-width 180 :show-initial? true}
                    {:header (:title (:library:numSubjects attributes)) :starting-width 100 :show-initial? true}]
                   (map
                    (fn [keyname]
                      {:header (:title ((keyword keyname) attributes)) :starting-width 180 :show-initial? false})
                    extra-columns))
         :pagination (react/call :pagination this)
         :->row (fn [data]
                  (->> extra-columns
                       (concat [:library:indication :library:dataUseRestriction :library:numSubjects])
                       (map data)
                       (cons data)
                       (cons data)))}]))
   :execute-search
   (fn [{:keys [refs]}]
     (react/call :update-query-params (@refs "table") {:current-page 1})
     (react/call :execute-search (@refs "table")))
   :check-access
   (fn [{:keys [props]} data]
     (if (= (:workspaceAccess data) "NO ACCESS")
       (comps/push-message
        {:header "Request Access"
         :message
         (if (= (config/tcga-namespace) (:namespace data))
           [:span {}
            [:p {} "For access to TCGA protected data please apply for access via dbGaP [instructions can be found "
             [:a {:href "https://wiki.nci.nih.gov/display/TCGA/Application+Process" :target "_blank"} "here"] "]."]
            [:p {} "After dbGaP approves your application please link your eRA Commons ID in your FireCloud profile page."]]
           [:span {}
            "Please contact "
            [:a {:target "_blank"
                 :href (str "mailto:" (:library:contactEmail data))}
             (str (:library:datasetCustodian data) " <" (:library:contactEmail data) ">")]
            " and request access for the "
            (:namespace data) "/" (:name data) " workspace."])})
       (nav/navigate (:nav-context props) "workspaces" (common/row->workspace-id data))))
   :build-aggregate-fields
   (fn [{:keys [props]}]
     (reduce
      (fn [results field] (assoc results field (if (contains? (:expanded-aggregates props) field) 0 5)))
      {}
      (:aggregate-fields props)))
   :pagination
   (fn [{:keys [this state props]}]
     (fn [{:keys [current-page rows-per-page]} callback]
       (when-not (empty? (:aggregate-fields props))
         (endpoints/call-ajax-orch
          (let [from (* (- current-page 1) rows-per-page)]
            {:endpoint endpoints/search-datasets
             :payload {:searchString (:search-text props)
                       :filters (utils/map-keys name (:facet-filters props))
                       :from from
                       :size rows-per-page
                       :fieldAggregations (if (= 1 current-page)
                                            (react/call :build-aggregate-fields this)
                                            {})}
             :headers utils/content-type=json
             :on-done
             (fn [{:keys [success? get-parsed-response status-text]}]
               (if success?
                 (let [{:keys [total results aggregations]} (get-parsed-response)]
                   (swap! state assoc :total total)
                   (callback {:group-count total
                              :filtered-count total
                              :rows results})
                   (when (= 1 current-page)
                     ((:callback-function props) aggregations)))
                 (callback {:error status-text})))})))))})

(react/defc SearchSection
  {:get-filters
   (fn [{:keys [props]}]
     (utils/map-keys name (:facet-filters props)))
   :render
   (fn [{:keys [props this]}]
     [:div {:style {:padding "16px 12px 0 12px"}}
      [comps/AutocompleteFilter
       {:ref "text-filter"
        :on-filter (:on-filter props)
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
                                               :filters (react/call :get-filters this)
                                               :from 0
                                               :size 10}))))}
        :typeaheadDisplay (fn [result]
                            (.text (js/$ (str "<div>" (aget result "value") "</div>"))))
        :typeaheadSuggestionTemplate (fn [result]
                                       (str "<div style='textOverflow: ellipsis; overflow: hidden; font-size: smaller;'>" (aget result "value") "</div>"))}]])})

(react/defc FacetCheckboxes
  {:render
   (fn [{:keys [props this]}]
     (let [size (:numOtherDocs props)
           title (:title props)
           all-buckets (mapv :key (:buckets props))
           hidden-items (clojure.set/difference (:selected-items props) (set all-buckets))
           hidden-items-formatted (mapv (fn [item] {:key item}) hidden-items)]
       [:div {:style {:paddingBottom "1em"}}
        [:hr {}]
        [:span {:style {:fontWeight "bold"}} title]
        [:div {:style {:fontSize "80%" :float "right"}}
         (style/create-link {:text "Clear" :onClick #(react/call :clear-all this)})]
        [:div {:style {:paddingTop "1em"}}
         (map
           (fn [{:keys [key doc_count]}]
             [:div {:style {:paddingTop 5}}
              [:label {:style {:display "inline-block" :width "calc(100% - 30px)"
                               :textOverflow "ellipsis" :overflow "hidden" :whiteSpace "nowrap"}
                       :title key}
               [:input {:type "checkbox"
                        :checked (contains? (:selected-items props) key)
                        :onChange #(react/call :update-selected this key (.. % -target -checked))}]
               [:span {:style {:marginLeft "0.3em"}} key]]
              (some-> doc_count style/render-count)])
           (concat (:buckets props) hidden-items-formatted))
         [:div {:style {:paddingTop 5}}
          (if (:expanded? props)
            (when (> (count (:buckets props)) 5)
              (style/create-link {:text " less..."
                                  :onClick #(react/call :update-expanded this false)}))
            (when (> size 0)
              (style/create-link {:text " more..."
                                  :onClick #(react/call :update-expanded this true)})))]]]))
   :clear-all
   (fn [{:keys [props]}]
     ((:callback-function props) (:field props) #{}))
   :update-expanded
   (fn [{:keys [props]} newValue]
     ((:expanded-callback-function props) (:field props) newValue))
   :update-selected
   (fn [{:keys [props]} name checked?]
     (let [updated-items ((if checked? conj disj) (:selected-items props) name)]
       ((:callback-function props) (:field props) updated-items)))})

(defn get-aggregations-for-property [agg-name aggregates]
  (first (keep (fn [m] (when (= (:field m) (name agg-name))
                         (:results m)))
               aggregates)))

(react/defc Facet
  {:render
   (fn [{:keys [props]}]
     (let [k (:aggregate-field props)
           properties (:aggregate-properties props)
           title (:title properties)
           render-hint (get-in properties [:aggregate :renderHint])
           aggregations (get-aggregations-for-property k (:aggregates props))]
       (cond
         (= render-hint "checkbox") [FacetCheckboxes
                                     (merge
                                      {:title title :field k}
                                      (select-keys aggregations [:numOtherDocs :buckets])
                                      (select-keys props [:expanded? :selected-items :callback-function
                                                          :expanded-callback-function]))])))})

(react/defc FacetSection
  {:update-aggregates
   (fn [{:keys [state]} aggregate-data]
     (swap! state assoc :aggregates aggregate-data))
   :render
   (fn [{:keys [props state]}]
     (if (empty? (:aggregates @state))
       [:div {:style {:fontSize "80%"}} "loading..."]
       (let [aggregate-fields (:aggregate-fields props)]
         [:div {:style {:fontSize "85%" :padding "16px 12px"}}
          (map
            (fn [prop-name] [Facet {:aggregate-field prop-name
                                    :aggregate-properties (prop-name (:aggregate-properties props))
                                    :aggregates (:aggregates @state)
                                    :expanded? (contains? (:expanded-aggregates props) prop-name)
                                    :selected-items (set (get-in props [:facet-filters prop-name]))
                                    :callback-function (:callback-function props)
                                    :expanded-callback-function (:expanded-callback-function props)}])
            aggregate-fields)])))})

(def ^:private PERSISTENCE-KEY "library-page")
(def ^:private VERSION 3)

(react/defc Page
  {:update-filter
   (fn [{:keys [state]} facet-name facet-list]
     (swap! state assoc-in [:facet-filters facet-name] facet-list))
   :set-expanded-aggregate
   (fn [{:keys [state]} facet-name expanded?]
     (swap! state update :expanded-aggregates (if expanded? conj disj) facet-name))
   :get-initial-state
   (fn []
     (persistence/try-restore
       {:key PERSISTENCE-KEY
        :initial (fn []
                   {:v VERSION
                    :search-text ""
                    :facet-filters {}
                    :expanded-aggregates #{}})
        :validator (comp (partial = VERSION) :v)}))
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/get-library-attributes
       (fn [{:keys [success? get-parsed-response]}]
         (if success?
           (let [{:keys [properties searchResultColumns]} (get-parsed-response)]
             (swap! state assoc
                    :library-attributes properties
                    :aggregate-fields (->> properties (utils/filter-values :aggregate) keys)
                    :search-result-columns (mapv keyword searchResultColumns)))))))
   :render
   (fn [{:keys [this refs state]}]
     [:div {:style {:display "flex" :marginTop "2em"}}
      [:div {:style {:width "20%" :minWidth 250 :marginRight "2em"
                     :background (:background-light style/colors)
                     :border style/standard-line}}
       [SearchSection {:search-text (:search-text @state)
                       :facet-filters (:facet-filters @state)
                       :on-filter #(swap! state assoc :search-text %)}]
       [FacetSection (merge
                      {:ref "facets"
                       :aggregate-properties (:library-attributes @state)
                       :callback-function (fn [facet-name facet-list]
                                            (react/call :update-filter this facet-name facet-list))
                       :expanded-callback-function (fn [field new-value]
                                                     (react/call :set-expanded-aggregate this field new-value))}
                      (select-keys @state [:aggregate-fields :facet-filters :expanded-aggregates]))]]
      [:div {:style {:flex "1 1 auto" :overflowX "auto"}}
       (when (and (:library-attributes @state) (:search-result-columns @state))
         [DatasetsTable (merge
                         {:ref "dataset-table"
                          :callback-function (fn [aggregates]
                                               (react/call :update-aggregates (@refs "facets") aggregates))}
                         (select-keys @state [:library-attributes :search-result-columns :search-text
                                              :facet-filters :aggregate-fields :expanded-aggregates]))])]])
   :component-did-update
   (fn [{:keys [state refs]}]
     (persistence/save {:key PERSISTENCE-KEY :state state})
     (react/call :execute-search (@refs "dataset-table")))})
