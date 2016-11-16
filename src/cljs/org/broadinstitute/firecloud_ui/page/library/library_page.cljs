(ns org.broadinstitute.firecloud-ui.page.library.library_page
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.modal :as modal]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.table-utils :refer [float-right]]
    [org.broadinstitute.firecloud-ui.nav :as nav]
    [org.broadinstitute.firecloud-ui.common.table-utils :as table-utils]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(react/defc DatasetsTable
  {:render
   (fn [{:keys [state this]}]
     (cond
       (:error-message @state) (style/create-server-error-message (:error-message @state))
       :else
       [:div {:style {:flex "2 0 auto" :marginRight "1em"}}
        [:div {:style {:fontWeight 700 :fontSize "125%"}} "Search Results: "
         [:span {:style {:fontWeight 100}} (str (:total @state) " Datasets found")]]
        [:div {:style {:padding "4"}}]
        [table/Table
         {:ref "table"
          :header-row-style {:fontWeight 500 :fontSize "90%"
                             :backgroundColor nil
                             :color "black"
                             :borderBottom (str "2px solid " (:border-light style/colors))}
          :header-style {:padding "0.5em 0"}
          :resizable-columns? false :reorderable-columns? true
          :body-style {:fontSize nil :fontWeight nil}
          :row-style {:backgroundColor nil :height 22}
          :cell-content-style {:padding nil
                               :color (:text-light style/colors)
                               :fontSize "90%"}
          :filterable? false
          :columns [{:header "Dataset Name" :starting-width 300
                     :sort-by (comp clojure.string/lower-case :library:datasetName)
                     :as-text :library:datasetDescription
                     :content-renderer (fn [data]
                                         (style/create-link {:text (:library:datasetName data)
                                                             :href(str "#workspaces/" (js/encodeURIComponent (str (:namespace data) ":" (:name data))))}))}
                    {:header "Phenotype/indication" :starting-width 200
                     :sort-by clojure.string/lower-case}
                    {:header "Data Use Restrictions" :starting-width 200
                     :sort-by clojure.string/lower-case}
                    {:header "# of Participants" :starting-width 150}]
          :pagination (react/call :pagination this)
          :->row (juxt identity :library:indication :library:dataUseRestriction :library:numSubjects)}]]))
   :set-filter-text
   (fn [{:keys [refs]} new-filter-text]
     (react/call :update-query-params (@refs "table") {:filter-text new-filter-text}))
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

(react/defc Page
  {:render
   (fn [{:keys [refs]}]
     [:div {:style {:display "flex" :justifyContent "space-between" :padding "20px 0"}}
      [:div {:style {:flex "1 0 auto" :marginRight "1em"}}
       [:div {:style {:fontWeight 700 :fontSize "125%"}} "Search Filters: "]
       [:div {:style {:padding "4"}}]
       [:div {:style {:display "inline-flex" :background (:background-light style/colors) :padding "16px 8px 10px 8px"}}
        [comps/TextFilter {:on-filter #(react/call :set-filter-text (@refs "datasets-table") %)}]]]
      [DatasetsTable {:ref "datasets-table"}]])})
