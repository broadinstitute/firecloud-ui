(ns org.broadinstitute.firecloud-ui.page.library.library_page
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.input :as input]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.table-utils :refer [flex-strut]]
    [org.broadinstitute.firecloud-ui.config :as config]
    [org.broadinstitute.firecloud-ui.nav :as nav]
    [org.broadinstitute.firecloud-ui.persistence :as persistence]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(react/defc DatasetsTable
  {:set-filter-text
   (fn [{:keys [refs]} new-filter-text]
     (react/call :update-query-params (@refs "table") {:filter-text new-filter-text :current-page 1}))
   :render
   (fn [{:keys [state this props]}]
     [table/Table
      {:ref "table" :state-key "library-table"
       :header-row-style {:fontWeight 500 :fontSize "90%"
                          :backgroundColor nil
                          :color "black"
                          :borderBottom (str "2px solid " (:border-light style/colors))}
       :header-style {:padding "0.5em 0"}
       :resizable-columns? false :filterable? false
       :reorder-anchor :right
       :toolbar
       (fn [{:keys [reorderer]}]
         [:div {:style {:display "flex" :alignItems "top"}}
          [:div {:style {:fontWeight 700 :fontSize "125%" :marginBottom "1em"}} "Search Results: "
           [:span {:style {:fontWeight 100}}
            (let [total (or (:total @state) 0)]
              (str total
                   " Dataset"
                   (when-not (= 1 total) "s")
                   " found"))]]
          flex-strut
          reorderer])
       :body-style {:fontSize "87.5%" :fontWeight nil :marginTop 4
                    :color (:text-light style/colors)}
       :row-style {:backgroundColor nil :height 20}
       :cell-content-style {:padding nil}
       :columns [{:header "Dataset Name" :starting-width 250
                  :sort-by (comp clojure.string/lower-case :library:datasetName)
                  :as-text :library:datasetDescription
                  :content-renderer (fn [data]
                                      (style/create-link {:text (:library:datasetName data)
                                                          :onClick #(react/call :check-access this data)}))}
                 {:header "Phenotype/indication" :starting-width 180
                  :sort-by clojure.string/lower-case}
                 {:header "Data Use Restrictions" :starting-width 180
                  :sort-by clojure.string/lower-case}
                 {:header "# of Participants" :starting-width 100}]
       :pagination (react/call :pagination this)
       :->row (juxt identity :library:indication :library:dataUseRestriction :library:numSubjects)}])
   ;:component-will-receive-props
   ;(utils/log "next-props in component will receive props")
   ;(utils/cljslog (:facet-filters next-props))
   ;(let [current-search-text (:filter-text (react/call :get-query-params (@refs "table")))
   ;     new-search-text (:search-text next-props)]
   ; (when-not (= current-search-text new-search-text)
   ;   (react/call :update-query-params (@refs "table") {:filter-text new-search-text})))
   :execute-search
   (fn [{:keys [props refs]}]
     (react/call :execute-search (@refs "table")))
   :check-access
   (fn [{:keys [props]} data]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/check-bucket-read-access (common/row->workspace-id data))
        :on-done (fn [{:keys [success?]}]
                   (if success?
                     (nav/navigate (:nav-context props) "workspaces" (common/row->workspace-id data))
                     (comps/push-message {:header "Request Access"
                                          :message
                                            (if (= (config/tcga-namespace) (:namespace data))
                                             [:span {}
                                               [:p {} "For access to TCGA protected data please apply for access via dbGaP [instructions can be found "
                                               [:a {:href "https://wiki.nci.nih.gov/display/TCGA/Application+Process" :target "_blank"} "here"] "]." ]
                                               [:p {} "After dbGaP approves your application please link your eRA Commons ID in your FireCloud profile page."]]
                                             [:span {}
                                             "Please contact " [:a {:target "_blank" :href (str "mailto:" (:library:contactEmail data))} (str (:library:datasetCustodian data) " <" (:library:contactEmail data) ">")]
                                               " and request access for the "
                                               (:namespace data) "/" (:name data) " workspace."])})))}))
   :pagination
   (fn [{:keys [this state props]}]
     ;(utils/cljslog "the props: " props);(:facet-filters props))
     ;(utils/cljslog  (keys (:aggregate-fields props)))
     ;(utils/cljslog "pagination search text first line "(:search-text props))
     (fn [{:keys [current-page rows-per-page]} callback]
       (endpoints/call-ajax-orch
         (let [from (* (- current-page 1) rows-per-page)]
           {:endpoint endpoints/search-datasets
            :payload {:searchString (utils/cljslog (:search-text props))
                      :filters (utils/map-kv (fn [k v]
                                               [(name k) v])
                                             (:facet-filters props))
                      :from from
                      :size rows-per-page
                      :fieldAggregations (:aggregate-fields props)}
            :headers utils/content-type=json
            :on-done
            (fn [{:keys [success? get-parsed-response status-text]}]
              (if success?
                (let [{:keys [total results aggregations]} (get-parsed-response)]
                  (swap! state assoc :total total)
                  (callback {:group-count total
                             :filtered-count total
                             :rows results})
                  ;(react/call :update-aggregates this aggregations)
                  ((:callback-function props) aggregations))
                (callback {:error status-text})))})))
     )})


(react/defc SearchSection
  {:render
   (fn [{:keys [props]}]
     [:div {}
      [:div {:style {:fontWeight 700 :fontSize "125%" :marginBottom "1em"}} "Search Filters:"]
      [:div {:style {:background (:background-light style/colors) :padding "16px 12px"}}
       [comps/TextFilter {:ref "text-filter"
                          :initial-text (:search-text props)
                          :width "100%" :placeholder "Search"
                          :on-filter (:on-filter props)}]]])})


;; TODO: "Clear" link should reset the checkboxes to unchecked and reset the data tables search
(react/defc FacetCheckboxes
  {:render
   (fn [{:keys [props this state]}]
     (let [size (:numOtherDocs props)
           title (:title props)]
       [:div {:style {:fontWeight "bold" :paddingBottom "1em"}}
        [:hr {}] title
        [:div {:style {:fontSize "80%" :fontWeight "normal" :float "right"}}
         (style/create-link {:text "Clear" :onClick #(swap! state assoc :expanded? false)})]
        [:div {:style {:paddingTop "1em" :fontWeight "normal"}}
         (map
           (fn [m]
             [:div {:style {:paddingTop "5"}}
              [comps/Checkbox {:label (:key m)
                               :initial-checked? (contains? (:selected-items props) (:key m))
                               :onChange #(react/call :update-selected this (:key m) %)}]
              [:div {:style {:fontSize "80%" :fontWeight "normal" :float "right"}}
               [:span {:style {
                               :display "inline-block"
                               :minWidth "10px"
                               :padding "3px 7px"
                               :color "#fff"
                               :fontWeight "bold"
                               :textAlign "center"
                               :whiteSpace "nowrap"
                               :verticalAlign "middle"
                               :backgroundColor "#aaa"
                               :borderRadius "3px"
                               }} (:doc_count m)]]])
           (if (:expanded? @state)
             (:buckets props)
             (take 5 (:buckets props))))
         (if (and (not (:expanded? @state)) (> size 5))
           [:div {:style {:paddingTop "5"}}
            ;; do something else here?
            (style/create-link {:text (str (- size 5) " more...") :onClick #(swap! state assoc :expanded? true)})])]]))
   :component-did-mount
   (fn [{:keys [state props]}]
     (swap! state assoc :selected-items (or (:selected-items props) #{})))
   :update-selected
   (fn [{:keys [state props]} name checked?]
     (let [updated-items (if checked?
                           (conj (:selected-items @state) name)
                           (disj (:selected-items @state) name))]
       (swap! state assoc :selected-items updated-items)
       ((:callback-function props) (:field props) updated-items)))})


;; See GAWB-978 for slider implementation story
;; TODO: "Clear" link should reset the slider and reset the data tables search
;; TODO: Look at where the data is, once indexing is taking place, the values should come from `buckets`, not `results`
; (react/defc FacetSlider
;   {:render
;    (fn [{:keys [props state]}]
;      (let [term (:term props)
;            results (:results props)
;            counts (map (fn [m] (term m)) results)
;            max-count (apply max counts)
;            title (:title props)]
;            [:div {:style {:fontWeight "bold" :paddingBottom "1em"}} title
;              [:div {:style {:fontSize "80%" :fontWeight "normal" :float "right"}}
;                (style/create-link {:text "Clear" :onClick #(swap! state assoc :expanded? false)})]
;                [:div {:style {:paddingTop "1em" :fontWeight "normal"}}
;                  [:input {:ref "slider" :type "range" :multiple "true" :min 0 :max max-count :onChange (fn[])}]]]))})

;; TODO: Need to deal with making this an autocomplete solely on the values inside the bucket.
;; TODO: "Clear" link should empty out the input value and reset the data tables search
;(react/defc FacetAutocomplete
;  {:render
;    (fn [{:keys [props state]}]
;      (let [buckets (:buckets props)
;            values (get-in buckets [key])
;            title (:title props)]
;            [:div {:style {:fontWeight "bold" :paddingBottom "1em" :paddingTop "1em"}}
;              [:hr {}] title
;              [:div {:style {:fontSize "80%" :fontWeight "normal" :float "right"}}
;                (style/create-link {:text "Clear" :onClick #(swap! state assoc :expanded? false)})]
;              [:div {:style {:paddingTop "1em" :fontWeight "normal"}}
;                [input/TextField {:style {:width "100%"}}]]]
;      )
;    )
;  }
;)

(defn get-aggregations-for-property [agg-name aggregates]
  (first (keep (fn [m] (when (= (:field m) (name agg-name)) (:results m))) aggregates)))

;; TODO: Styling to match layout model.
;; TODO: OnChange handler to swap state and filter dataset-table based on filter selection
;; TODO: error case for loading content
;; TODO: Deal with sorting
;; TODO: Advanced Search feature for DUR filter
(react/defc Facet
  {:render
   (fn [{:keys [props state]}]
     (let [k (:aggregate-field props)
           properties (:aggregate-properties props)
           title (:title properties)
           render-hint (get-in properties [:aggregate :renderHint])
           aggregations (get-aggregations-for-property k (:aggregates props))]
        [:div {:style {:fontSize "80%"}}
         (cond
              ;(= render-hint "text") [FacetAutocomplete {:title title :buckets buckets}] ;; add page ref here?
              (= render-hint "checkbox") [FacetCheckboxes
                                          {:title title
                                           :numOtherDocs (:numOtherDocs aggregations)
                                           :buckets (:buckets aggregations)
                                           :field k
                                           :selected-items (:selected-items props)
                                           :callback-function (:callback-function props)}]
              ;(= render-hint "slider") [FacetSlider {:title title :term k :results (:results @state)}]
            )]))})


(react/defc FacetSection
  {:update-aggregates
   (fn [{:keys [state]} aggregate-data]
     (swap! state assoc :aggregates aggregate-data))
   :render
   (fn [{:keys [props state]}]
     (if-not (:aggregates @state)
       [:div {:style {:fontSize "80%"}} "loading..."]
       (let [aggregate-fields (:aggregate-fields props)]
         [:div {:style {:background (:background-light style/colors) :padding "16px 12px"}}
          (map
            (fn [m] [Facet {:aggregate-field m
                            :aggregate-properties (m (:aggregate-properties props))
                            :aggregates (:aggregates @state)
                            :selected-items (get-in props [:facet-filters m])
                            :callback-function (:callback-function props)}])
            aggregate-fields)])))})


(def ^:private PERSISTENCE-KEY "library-page")
(def ^:private VERSION 1)

(react/defc Page
  {:update-filter
   (fn [{:keys [state]} facet-name facet-list]
     (swap! state assoc-in [:facet-filters facet-name] facet-list))
   :get-initial-state
   (fn []
     (persistence/try-restore
       {:key PERSISTENCE-KEY
        :initial (fn []
                   {:v VERSION
                    :search-text ""
                    :facet-filters {}})
        :validator (comp (partial = VERSION) :v)}))
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/get-library-attributes
       (fn [{:keys [success? get-parsed-response]}]
         (if success?
           (let [response (get-parsed-response)]
             (swap! state assoc
                    :library-attributes (:properties response)
                    :aggregate-fields (keep (fn [[k m]] (when (:aggregate m) k)) (:properties response))))))))
   :render
   (fn [{:keys [this refs state]}]
     [:div {:style {:display "flex" :marginTop "2em"}}
      [:div {:style {:flex "0 0 250px" :marginRight "2em"}}
       [SearchSection {:search-text (:search-text @state)
                       :on-filter #(swap! state assoc :search-text %)}]
       [FacetSection {:ref "facets"
                      :aggregate-fields (:aggregate-fields @state)
                      :aggregate-properties (:library-attributes @state)
                      :facet-filters (:facet-filters @state)
                      :callback-function (fn [facet-name facet-list]
                                           (react/call :update-filter this facet-name facet-list))}]]
      [:div {:style {:flex "1 1 auto" :overflowX "auto"}}
       [DatasetsTable {:ref "dataset-table"
                       :search-text (:search-text @state)
                       :facet-filters (:facet-filters @state)
                       :aggregate-fields (:aggregate-fields @state)
                       :callback-function (fn [aggregates]
                                            (react/call :update-aggregates (@refs "facets") aggregates))}]]])
   :component-did-update
   (fn [{:keys [state refs]}]
     (persistence/save {:key PERSISTENCE-KEY :state state})
     (react/call :execute-search (@refs "dataset-table")))})
