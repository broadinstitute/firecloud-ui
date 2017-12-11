(ns broadfcui.page.library.library-page
  (:require
   [dmohs.react :as react]
   [clojure.set :as set]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.filter :as filter]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.markdown :as markdown]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.components.autosuggest :refer [Autosuggest]]
   [broadfcui.components.modals :as modals]
   [broadfcui.components.spinner :refer [spinner]]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.nav :as nav]
   [broadfcui.page.library.research-purpose :refer [ResearchPurposeSection]]
   [broadfcui.persistence :as persistence]
   [broadfcui.utils :as utils]
   ))

(def ^:private tcga-access-instructions
  [:span {}
   [:p {} "For access to TCGA controlled data please apply for access via dbGaP [instructions can be found "
    (links/create-external {:href "https://wiki.nci.nih.gov/display/TCGA/Application+Process"} "here")
    "]."]])

(def ^:private target-access-instructions
  [:span {}
   [:p {} "For access to TARGET protected data please apply for access via dbGaP [instructions can be found "
    (links/create-external {:href "https://ocg.cancer.gov/programs/target/using-target-data"} "here")
    "]."]])

(defn- standard-access-instructions [data]
  [:span {}
   "Please contact "
   [:a {:target "_blank"
        :href (str "mailto:" (:library:contactEmail data))}
    (:library:datasetCustodian data) " <" (:library:contactEmail data) ">"
    (icons/render-icon {:style {:paddingLeft "0.25rem" :fontSize "80%"}} :email)]
   " and request access for the "
   (:namespace data) "/" (:name data) " workspace."])

(defn- translate-research-purpose [research-purpose]
  (as-> research-purpose $
        (dissoc $ :ds)
        (utils/map-keys {:methods "NMDS"
                         :control "NCTRL"
                         :aggregates "NAGR"
                         :poa "POA"
                         :commercial "NCU"} $)
        (merge {"NMDS" false
                "NCTRL" false
                "NAGR" false
                "POA" false
                "NCU" false} $)
        (assoc $ "DS" (vec (keys (:ds research-purpose))))))

(defn- render-tags [data]
  (->> data
       sort
       (map #(style/render-tag {:style {:margin "0 0.1rem" :padding "0 0.5rem" :display "inline-flex"}} %))))

(react/defc- DatasetsTable
  {:execute-search
   (fn [{:keys [refs]} reset-sort?]
     (let [query-params (merge {:page-number 1} (when reset-sort? {:sort-column nil :sort-order nil}))
           {:strs [table]} @refs]
       (when-not (table :update-query-params query-params)
         (table :refresh-rows))))
   :render
   (fn [{:keys [props state this]}]
     (let [attributes (:library-attributes props)
           search-result-columns (:search-result-columns props)
           extra-columns (subvec search-result-columns 5)]
       [Table
        {:ref "table" :persistence-key "library-table" :v 5
         :data-test-id "library-table"
         :fetch-data (this :-pagination)
         :style {:content {:marginLeft "2rem"}}
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
                                  (icons/render-icon (merge
                                                      {:style {:cursor "pointer" :marginLeft "-0.7rem"}}
                                                      (this :-get-link-props data))
                                                     :shield)))}
                     {:id "library:datasetName"
                      :header (:title (:library:datasetName attributes)) :initial-width 250
                      :sort-initial :asc :sort-by :library:datasetName
                      :as-text :library:datasetDescription
                      :render (fn [data]
                                (links/create-internal
                                 (merge {:data-test-id (str "dataset-" (:library:datasetName data))}
                                        (this :-get-link-props data))
                                 (:library:datasetName data)))}
                     {:id "library:indication" :header (:title (:library:indication attributes))
                      :column-data :library:indication :initial-width 180}
                     {:id "library:numSubjects" :header (:title (:library:numSubjects attributes))
                      :column-data :library:numSubjects :initial-width 100}
                     {:id "library:consentCodes" :header (:title (:library:consentCodes attributes))
                      :column-data :library:consentCodes :initial-width 180
                      :as-text (fn [data] (string/join ", " (sort data)))
                      :sortable? false
                      :render render-tags}
                     {:id "tag:tags" :header (:title (:tag:tags attributes))
                      :column-data :tag:tags :initial-width 180
                      :as-text (fn [data] (string/join ", " (sort data)))
                      :sortable? false
                      :render render-tags}]
                    (map
                     (fn [keyname]
                       {:id (name keyname) :header (:title (keyname attributes))
                        :initial-width 180 :show-initial? false
                        :column-data keyname
                        :render (fn [field]
                                  (let [tag? (= (get-in (keyname attributes) [:renderHint :type]) "tag")]
                                    (cond
                                      tag? (render-tags field)
                                      (sequential? field) (string/join ", " (sort field))
                                      :else field)))})
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
                      [(when (:show-request-access? @state)
                         (modals/render-message
                          {:header "Request Access"
                           :text (:request-access-message @state)
                           :confirm #(swap! state dissoc :show-request-access? :request-access-message)}))
                       [:div {:style {:fontSize "112%"}}
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
          :style {:alignItems "baseline" :marginBottom "0.5rem" :padding "1rem"
                  ;; The following to line up "Matching Cohorts" to the first (real) column
                  :paddingLeft "calc(3rem + 12px)"
                  :backgroundColor (:background-light style/colors)}
          :column-edit-button {:style {:order 1 :marginRight nil}
                               :anchor :right}}}]))
   :-get-link-props
   (fn [{:keys [state]} data]
     (let [built-in-groups #{"TCGA-dbGaP-Authorized", "TARGET-dbGaP-Authorized"}
           ws-auth-domains (set (:authorizationDomain data))]
       (if (= (:workspaceAccess data) "NO ACCESS")
         {:onClick
          (fn [_]
            (swap! state assoc
                   :show-request-access? true
                   :request-access-message
                   (cond (:library:dataAccessInstructions data)
                         [markdown/MarkdownView {:text (:library:dataAccessInstructions data)}]
                         (or (not-empty (set/difference ws-auth-domains built-in-groups))
                             (empty? ws-auth-domains))
                         (standard-access-instructions data)
                         :else
                         [:span {}
                          (let [tcga? (contains? ws-auth-domains "TCGA-dbGaP-Authorized")
                                target? (contains? ws-auth-domains "TARGET-dbGaP-Authorized")]
                            [:div {}
                             (when tcga? tcga-access-instructions)
                             (when target? target-access-instructions)
                             (when (or tcga? target?)
                               [:p {} "After dbGaP approves your application please link your eRA
                                       Commons ID in your FireCloud profile page."])])])))}
         {:href (nav/get-link :workspace-summary (common/row->workspace-id data))})))
   :-build-aggregate-fields
   (fn [{:keys [props]}]
     (reduce
      ;; Limit results to 5 unless (1) the section is expanded or (2) it's the tags section
      (fn [results field]
        (assoc results field (if (or (contains? (:expanded-aggregates props) field)
                                     (= field :tag:tags))
                               0
                               5)))
      {}
      (:aggregate-fields props)))
   :-pagination
   (fn [{:keys [props state this]}]
     (fn [{:keys [query-params on-done]}]
       (let [{:keys [page-number rows-per-page sort-column sort-order]} query-params]
         (when (seq (:aggregate-fields props))
           (endpoints/call-ajax-orch
            (let [from (* (dec page-number) rows-per-page)
                  update-aggregates? (or (= 1 page-number) (:no-aggregates? props))]
              {:endpoint endpoints/search-datasets
               :payload {:searchString (:filter-text props)
                         :filters ((:get-facets props))
                         :researchPurpose (translate-research-purpose (:research-purpose props))
                         :from from
                         :size rows-per-page
                         :sortField sort-column
                         :sortDirection sort-order
                         :fieldAggregations (if update-aggregates?
                                              (this :-build-aggregate-fields)
                                              {})}
               :headers utils/content-type=json
               :on-done
               (fn [{:keys [success? get-parsed-response status-text]}]
                 (if success?
                   (let [{:keys [total results aggregations]} (get-parsed-response)]
                     (swap! state assoc :total total)
                     (on-done {:total-count total
                               :results results})
                     (when update-aggregates?
                       ((:update-aggregates props) aggregations)))
                   (on-done {:error status-text})))}))))))})


(defn- highlight-suggestion [{:strs [suggestion highlight]}]
  [:div {:style {:textOverflow "ellipsis" :overflow "hidden"}}
   (if-not (string/blank? highlight)
     (let [suggestion-parts (string/split suggestion highlight)]
       (cond ;; different logic to handle permutations of where the highlight is in the suggestion
         (< (count suggestion-parts) 2) [:span {} (first suggestion-parts) [:strong {} highlight] (second suggestion-parts)]
         :else [:span {} (interpose [:strong {} highlight] suggestion-parts)]))
     suggestion)])

(defn- create-search-section [{:keys [search-text facet-filters on-input on-filter]}]
  [:div {:style {:paddingBottom "calc(16px - 0.9rem)"}} ;; cancel the padding on the hr and match the outer padding
   [Autosuggest
    {:value search-text
     :inputProps {:placeholder "Search" :data-test-id "library-search-input"}
     :get-suggestions (fn [query callback]
                        (utils/ajax-orch
                         "/library/suggest/"
                         {:data (utils/->json-string
                                 {:searchString query
                                  :filters (utils/map-keys name facet-filters)
                                  :from 0
                                  :size 10})
                          :method "POST"
                          :headers utils/content-type=json
                          :on-done (fn [{:keys [success? get-parsed-response]}]
                                     (callback (if success?
                                                 ; don't bother keywordizing, it's just going to be converted to js
                                                 ((get-parsed-response false) "results")
                                                 [:error])))})
                        [:loading])
     :get-value #(.-suggestion %)
     :renderSuggestion (fn [suggestion]
                         (react/create-element (highlight-suggestion (js->clj suggestion))))
     :highlightFirstSuggestion false
     :on-change on-input
     :on-submit on-filter
     :theme {:input {:width "100%" :marginBottom 0}
             :suggestionsContainerOpen {:marginTop -1}}}]])

(react/defc- FacetCheckboxes
  {:render
   (fn [{:keys [props this]}]
     (let [size (:numOtherDocs props)
           title (:title props)
           all-buckets (mapv :key (:buckets props))
           hidden-items (set/difference (:selected-items props) (set all-buckets))
           hidden-items-formatted (mapv (fn [item] {:key item}) hidden-items)]
       (filter/section
        {:title title
         :on-clear #(this :clear-all)
         :content
         [:div {}
          (filter/checkboxes {:items (map (fn [{:keys [key doc_count]}]
                                            {:item key :hit-count doc_count})
                                          (concat (:buckets props) hidden-items-formatted))
                              :checked-items (:selected-items props)
                              :on-change (fn [item checked?] (this :update-selected item checked?))})
          [:div {:style {:paddingTop 5}}
           (if (:expanded? props)
             (when (> (count (:buckets props)) 5)
               (links/create-internal {:onClick #(this :update-expanded false)} " less..."))
             (when (pos? size)
               (links/create-internal {:onClick #(this :update-expanded true)} " more...")))]]})))
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
   (fn [{:keys [props refs]}]
     (let [aggregate-field (:aggregate-field props)
           properties (:aggregate-properties props)
           title (:title properties)
           render-hint (get-in properties [:aggregate :renderHint])
           aggregations (get-aggregations-for-property aggregate-field (:aggregates props))]
       (case render-hint
         "checkbox"
         [FacetCheckboxes
          (merge
           {:title title :field aggregate-field}
           (select-keys aggregations [:numOtherDocs :buckets])
           (select-keys props [:expanded? :selected-items :update-filter :expanded-callback-function]))]
         "typeahead-multiselect"
         (let [tags (mapv :key (:buckets aggregations))
               selected-tags (:selected-items props)]
           (filter/section
            {:title title
             :content (react/create-element
                       [comps/TagAutocomplete
                        {:ref "tag-autocomplete"
                         :tags selected-tags
                         :data (distinct (concat selected-tags (set tags)))
                         :show-counts? false
                         :allow-new? false
                         :on-change #((:update-filter props) aggregate-field %)}])
             :on-clear #((@refs "tag-autocomplete") :set-tags #{})})))))})


(defn- facet-section [{:keys [aggregates aggregate-properties expanded-aggregates
                              facet-filters aggregate-fields] :as props}]
  (if (empty? aggregates)
    [(spinner "Loading...")]
    (map (fn [aggregate-field]
           [Facet (merge (utils/restructure aggregate-field aggregates)
                         (select-keys props [:update-filter :expanded-callback-function])
                         {:aggregate-properties (get aggregate-properties aggregate-field)
                          :expanded? (contains? expanded-aggregates aggregate-field)
                          :selected-items (set (get facet-filters aggregate-field))})])
         (cons :tag:tags (remove (partial = :tag:tags) aggregate-fields)))))

(def ^:private PERSISTENCE-KEY "library-page")
(def ^:private VERSION 4)

(react/defc- Page
  (->>
   {:update-filter
    (fn [{:keys [state this]} facet-name facet-list]
      ;; NOTE: The TagAutocomplete used here is very touchy and fires extra on-change events.
      ;; Check here that something is actually changing before firing off state updates and
      ;; table refreshes.
      (when (not= (not-empty facet-list) (not-empty (get-in @state [:facet-filters facet-name])))
        (if (empty? facet-list)
          (swap! state update :facet-filters dissoc facet-name)
          (swap! state assoc-in [:facet-filters facet-name] facet-list))
        (this :-refresh-table)))
    :set-expanded-aggregate
    (fn [{:keys [state this]} facet-name expanded?]
      (swap! state update :expanded-aggregates (if expanded? conj disj) facet-name)
      (this :-refresh-table))
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
    (fn [{:keys [state this]}]
      [:div {:style {:display "flex"}}
       (apply
        filter/area {:style {:width 260 :boxSizing "border-box"}}
        (create-search-section {:search-text (:search-text @state)
                                :facet-filters (:facet-filters @state)
                                :on-input #(swap! state assoc :search-text %)
                                :on-filter (fn [text]
                                             (swap! state assoc :search-text text)
                                             (this :-refresh-table true))})
        [ResearchPurposeSection
         {:research-purpose-values (:research-purpose @state)
          :on-search (fn [options]
                       ;; Throw out falses:
                       (swap! state assoc :research-purpose (utils/filter-values identity options))
                       (this :-refresh-table))}]
        (facet-section (merge
                        {:aggregates (:aggregates @state)
                         :aggregate-properties (:library-attributes @state)
                         :update-filter (fn [facet-name facet-list]
                                          (this :update-filter facet-name facet-list))
                         :expanded-callback-function (fn [facet-name expanded?]
                                                       (this :set-expanded-aggregate facet-name expanded?))}
                        (select-keys @state [:aggregate-fields :facet-filters :expanded-aggregates]))))
       [:div {:style {:flex "1 1 auto" :overflowX "auto"}}
        (when (and (:library-attributes @state) (:search-result-columns @state))
          [DatasetsTable (merge
                          {:ref "dataset-table"
                           :filter-text (:search-text @state)
                           :research-purpose (:research-purpose @state)
                           :update-aggregates #(swap! state assoc :aggregates %)
                           :no-aggregates? (empty? (:aggregates @state))
                           :get-facets #(utils/map-keys name (:facet-filters @state))}
                          (select-keys @state [:library-attributes :search-result-columns :aggregate-fields :expanded-aggregates]))])]])
    :-refresh-table
    (fn [{:keys [refs after-update]} & [reset-sort?]]
      (after-update #((@refs "dataset-table") :execute-search reset-sort?)))}
   (persistence/with-state-persistence {:key PERSISTENCE-KEY :version VERSION
                                        :initial {:search-text ""
                                                  :research-purpose {}
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
