(ns org.broadinstitute.firecloud-ui.page.library.library_page
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.table-utils :refer [flex-strut]]
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
          [:div {:style {:fontWeight 700 :fontSize "125%"}} "Search Results: "
           [:span {:style {:fontWeight 100}}
            (let [total (or (:total @state) 0)]
              (str total
                   " Dataset"
                   (when-not (= 1 total) "s")
                   " found"))]]
          flex-strut
          reorderer])
       :body-style {:fontSize nil :fontWeight nil}
       :row-style {:backgroundColor nil :height 22}
       :cell-content-style {:padding nil
                            :color (:text-light style/colors)
                            :fontSize "90%"}
       :columns [{:header "Dataset Name" :starting-width 300
                  :sort-by (comp clojure.string/lower-case :library:datasetName)
                  :as-text :library:datasetDescription
                  :content-renderer (fn [data]
                                      (style/create-link {:text (:library:datasetName data)
                                                          :onClick #(react/call :check-access this data props)}))}
                 {:header "Phenotype/indication" :starting-width 200
                  :sort-by clojure.string/lower-case}
                 {:header "Data Use Restrictions" :starting-width 200
                  :sort-by clojure.string/lower-case}
                 {:header "# of Participants" :starting-width 150}]
       :pagination (react/call :pagination this)
       :->row (juxt identity :library:indication :library:dataUseRestriction :library:numSubjects)}])
   :component-will-receive-props
   (fn [{:keys [next-props refs]}]
     (let [current-search-text (:filter-text (react/call :get-query-params (@refs "table")))
           new-search-text (:search-text next-props)]
       (when-not (= current-search-text new-search-text)
         (react/call :update-query-params (@refs "table") {:filter-text new-search-text}))))
   :check-access
   (fn [data props]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/check-bucket-read-access {:namespace (:namespace data) :name (:name data)})
        :on-done (fn [{:keys [success?]}]
                   (if success?
                     (nav/navigate (:nav-context props) "workspaces" {:namespace (:namespace data) :name (:name data)})
                     (modal/push-message {:header "No Access!"
                                          :message (str "You do not have access to this data set.\n Please contact " (:library:datasetCustodian data) " for access.")})))}))
   :pagination
   (fn [{:keys [state]}]
     (fn [{:keys [current-page rows-per-page filter-text]} callback]
       (endpoints/call-ajax-orch
         (let [from (* (- current-page 1) rows-per-page)]
           {:endpoint endpoints/search-datasets
            :payload {:searchTerm filter-text :from from :size rows-per-page}
            :headers utils/content-type=json
            :on-done
            (fn [{:keys [success? get-parsed-response status-text]}]
              (if success?
                (let [{:keys [total results]} (get-parsed-response)]
                  (swap! state assoc :total total)
                  (callback {:group-count total
                             :filtered-count total
                             :rows results}))
                (callback {:error status-text})))}))))})


(react/defc SearchSection
  {:render
   (fn [{:keys [props]}]
     [:div {}
      [:div {:style {:fontWeight 700 :fontSize "125%"}} "Search Filters:"]
      [:div {:style {:background (:background-light style/colors) :padding "16px 12px"}}
       [comps/TextFilter {:ref "text-filter"
                          :initial-text (:search-text props)
                          :width "100%" :placeholder "Search"
                          :on-filter (:on-filter props)}]]])})


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
   :render
   (fn [{:keys [state]}]
     [:div {:style {:display "flex" :padding "20px 0"}}
      [:div {:style {:flex "0 0 250px" :marginRight "2em"}}
       [SearchSection {:search-text (:search-text @state)
                       :on-filter #(swap! state assoc :search-text %)}]]
      [:div {:style {:flex "1 1 auto" :overflowX "auto"}}
       [DatasetsTable {:search-text (:search-text @state)}]]])
   :component-did-update
   (fn [{:keys [state]}]
     (persistence/save {:key PERSISTENCE-KEY :state state}))})
