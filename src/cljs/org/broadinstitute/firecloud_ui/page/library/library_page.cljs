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
   :component-will-receive-props
   (fn [{:keys [next-props refs]}]
     (let [current-search-text (:filter-text (react/call :get-query-params (@refs "table")))
           new-search-text (:search-text next-props)]
       (when-not (= current-search-text new-search-text)
         (react/call :update-query-params (@refs "table") {:filter-text new-search-text}))))
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
   (fn [{:keys [this state]}]
     (fn [{:keys [current-page rows-per-page filter-text]} callback]
       (endpoints/call-ajax-orch
         (let [from (* (- current-page 1) rows-per-page)]
           {:endpoint endpoints/search-datasets
            :payload {:searchTerm filter-text :from from :size rows-per-page :fieldAggregations (map (fn [{:keys [name]}] (name name)) (:aggregates props))}
            :headers utils/content-type=json
            :on-done
            (fn [{:keys [success? get-parsed-response status-text]}]
              (if success?
                (let [{:keys [total results aggregations]} (get-parsed-response)]
                  (swap! state assoc :total total)
                  (callback {:group-count total
                             :filtered-count total
                             :rows results})
                  (react/call :update-aggregates this aggregations))
                (callback {:error status-text})))}))))})


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


(react/defc FacetCheckboxes
  {:render
    (fn [{:keys [props state]}]
      [:div {}
      (map
        (fn [m]
          [:div {:style {:paddingTop "5"}}
            [comps/Checkbox {:label (:key m)}]
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
                }} (:doc_count m)]]]
        )
        (take 5 (:buckets props))
      )
      ;; I think we should just hide those > 5 and show when we click on the more link.
      (when-let [size (count (:buckets props))]
        (if (> size 5) [:div {:style {:paddingTop "5"}} (- size 5) " more..."]))]
    )
  }
)


;; See http://leaverou.github.io/multirange/ for a better idea.
;; Aggregate values for this facet are not useful and instead we need to
;; iterate over the full result set and pull out the ones we want.
;; This is likely because they are not indexed yet!!!
(react/defc FacetSlider
  {:render
   (fn [{:keys [props state]}]
     (let [term (:term props)
           results (:results props)
           counts (map (fn [m] (term m)) results)
           max-count (apply max counts)]
       [:div {:style {:paddingTop "5"}}
         [:input {:type "range" :multiple "true" :value (str "0," max-count) :onChange (fn [])}]]
     )
    )
  }
)

;; TODO: Need to deal with making this an autocomplete solely on the values inside the bucket.
(react/defc FacetAutocomplete
  {:render
    (fn [{:keys [props state]}]
      (let [buckets (:buckets props)
            values (get-in buckets [key])]
        [input/TextField {}]
      )
    )
  }
)

;; TODO: Styling to match layout model.
;; TODO: OnChange handler to swap state and filter dataset-table based on filter selection
;; TODO: OnChange handler for the "clear" link to swap state on facet and trigger filter on data-set table
;; TODO: error case for loading content
;; TODO: Deal with sorting
;; TODO: Deal with linking the "XX more ..." text
(react/defc Facet
  {:render
   (fn [{:keys [props state]}]
     ;(utils/cljslog "aggregate-field" (:aggregate-field props))
      (let [k (first (keys (:aggregate-field props)))
            m (k (:aggregate-field props))
            title (:title m)
            render-hint (get-in m [:aggregate :renderHint])
            buckets (get-in (:aggregations @state) [0 :results :buckets])]
        ;(utils/cljslog "render-hint" render-hint)
        [:div {:style {:fontSize "80%"}}
          [:div {:style {:fontWeight "bold"}} title
            [:div {:style {:fontSize "80%" :fontWeight "normal" :float "right"}} "Clear"]]
            (if-not (:aggregations @state)
              "loading..."
              (cond
                (= render-hint "checkbox") [FacetCheckboxes {:buckets buckets}]
                (= render-hint "slider") [FacetSlider {:term k :results (:results @state)}]
                (= render-hint "text") [FacetAutocomplete {:buckets buckets}]
              )
            )
            [:div {:style {:padding "5 0 5 0"}} [:hr {}]]
        ]
      )
    )
    :component-did-mount
    (fn [{:keys [props state]}]
       (let [k (first (keys (:aggregate-field props)))]
         (endpoints/call-ajax-orch
           {:endpoint endpoints/search-datasets
            :payload {"fieldAggregations" [k]}
            :headers utils/content-type=json
            :on-done
            (fn [{:keys [success? get-parsed-response status-text]}]
              (if success?
                (let [{:keys [results aggregations]} (get-parsed-response)]
                  (swap! state assoc :results results :aggregations aggregations)
                 )
               )
             )
           }
         )
       )
    )
  }
)


(react/defc FacetSection
  {:render
    (fn [{:keys [refs props]}]
      (let [aggregate-fields (:aggregate-fields props)]
        ; (utils/cljslog "aggregate-fields" aggregate-fields)
        [:div {:style {:background (:background-light style/colors) :padding "16px 12px"}}
            (map
              (fn [m] [Facet {:aggregate-field m}])
              aggregate-fields)]))})


(def ^:private PERSISTENCE-KEY "library-page")
(def ^:private VERSION 1)

(react/defc Page
  {:get-initial-state
   (fn []
     (persistence/try-restore
       {:key PERSISTENCE-KEY
        :initial (fn []
                   {:v VERSION
                    :search-text ""})
        :validator (comp (partial = VERSION) :v)}))
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/get-library-attributes
       (fn [{:keys [success? get-parsed-response]}]
         (if success?
           (let [response (get-parsed-response)]
             (swap! state assoc
                    :library-attributes (:properties response)
                    :aggregate-fields (utils/cljslog (keep (fn [[k m]] (when (:aggregate m) {k m})) (:properties response)))))))))
   :render
   (fn [{:keys [refs state]}]
     [:div {:style {:display "flex" :marginTop "2em"}}
      [:div {:style {:flex "0 0 250px" :marginRight "2em"}}
       [SearchSection {:search-text (:search-text @state)
                       :on-filter #(swap! state assoc :search-text %)}]
       [FacetSection {:aggregate-fields (:aggregate-fields @state)
                      ;:on-change #(react/call :set-filter-text (@refs "datasets-table") %)
                      }]]
      [:div {:style {:flex "1 1 auto" :overflowX "auto"}}
       [DatasetsTable {:search-text (:search-text @state)}]]])
   :component-did-update
   (fn [{:keys [state]}]
     (persistence/save {:key PERSISTENCE-KEY :state state}))})
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/get-library-attributes
       (fn [{:keys [success? get-parsed-response]}]
         (if success?
           (let [response (get-parsed-response)]
             (swap! state assoc
                    :library-attributes (:properties response)
                    :aggregates (keep (fn [[k {:keys [aggregate title]}]] (when aggregate {:name k :title title})) (:properties response))))))))
   :render
   (fn [{:keys [state refs]}]
     [:div {:style {:display "flex" :marginTop "2em"}}
     ;[:div {:style {:display "flex" :padding "20px 0"}}
      [:div {:style {:flex "0 0 250px" :marginRight "2em"}}
       [:div {:style {:fontWeight 700 :fontSize "125%"}} "Search Filters: "]
       [:div {:style {:background (:background-light style/colors) :padding "16px 12px"}}
        [comps/TextFilter {:width "100%" :placeholder "Search"
                           :on-filter #(react/call :set-filter-text (@refs "datasets-table") %)}]
        (map
          (fn [{:keys [name title]}]
            [comps/FacetFilter {:ref name
                                :name name
                                :title title
                                ;:on-change #(react/call :set-filter-facet (@refs "datasets-table") %)
                                }])
          (:aggregates (utils/cljslog @state)))]
       [:div {:style {:flex "1 1 auto" :overflowX "auto"}}
        [DatasetsTable {:ref "datasets-table" :aggregates (:aggregates @state)
                        :on-filter #(react/call :set-filter-text (@refs "datasets-table") %)}]]]])})
