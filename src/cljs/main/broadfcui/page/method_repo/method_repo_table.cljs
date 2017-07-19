(ns broadfcui.page.method-repo.method-repo-table
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.common.table.table :refer [Table]]
   [broadfcui.common.table.utils :as table-utils]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.persistence :as persistence]
   [broadfcui.utils :as utils]
   ))


(react/defc MethodRepoTable
  (->>
   {:reload
    (fn [{:keys [this]}]
      (this :load-data))
    :render
    (fn [{:keys [props state]}]
      (assert (:render-name props) "render-name not provided")
      (cond
        (:error-message @state) (style/create-server-error-message (:error-message @state))
        (or (nil? (:methods @state)) (nil? (:configs @state)))
        [comps/Spinner {:text "Loading methods and configurations..."}]
        :else
        [Table
         {:persistence-key "method-repo-table" :v 1
          :data (or (:filtered-data @state) [])
          :body
          {:columns
           [{:header "Type" :initial-width 100
             :column-data :entityType}
            {:header "Name" :initial-width 350
             :sort-by (juxt (comp string/lower-case :name) (comp int :snapshotId))
             :filter-by (fn [{:keys [name snapshotId]}] (str name " " (int snapshotId)))
             :as-text (fn [{:keys [name snapshotId]}] (str name " Snapshot ID: " snapshotId))
             :render (:render-name props)}
            {:header "Namespace" :initial-width 160
             :sort-by (comp string/lower-case :namespace)
             :sort-initial :asc
             :as-text :namespace
             :render (or (:render-namespace props) :namespace)}
            {:header "Synopsis" :initial-width 160 :column-data :synopsis}
            (table-utils/date-column {:header "Created" :column-data :createDate})
            {:header "Referenced Method" :initial-width 250
             :column-data (fn [item]
                            (when (= :config (:type item))
                              (mapv (get item :method {}) [:namespace :name :snapshotId])))
             :as-text (fn [[namespace name snapshotId]]
                        (if namespace
                          (str namespace "/" name " Snapshot ID: " snapshotId)
                          "N/A"))
             :render (fn [fields]
                       (if fields
                         (apply style/render-entity fields)
                         "N/A"))}]
           :style table-style/table-heavy}
          :toolbar
          {:items (cons [comps/FilterGroupBar
                         {:data (concat (:methods @state) (:configs @state))
                          :selected-index (:filter-group-index @state)
                          :on-change (fn [index data]
                                       (swap! state assoc
                                              :filter-group-index index
                                              :filtered-data data))
                          :filter-groups [{:text "All"}
                                          {:text "Methods Only" :pred (comp (partial = :method) :type)}
                                          {:text "Configs Only" :pred (comp (partial = :config) :type)}]}]
                        (:toolbar-items props))}}]))
    :component-did-mount
    (fn [{:keys [this]}]
      (this :load-data))
    :load-data
    (fn [{:keys [state]}]
      (swap! state dissoc :configs :methods :error-message)
      (endpoints/call-ajax-orch
       {:endpoint endpoints/list-configurations
        :on-done
        (fn [{:keys [success? get-parsed-response status-text]}]
          (if success?
            (swap! state assoc :configs (map #(assoc % :type :config) (get-parsed-response)))
            (swap! state assoc :error-message status-text)))})
      (endpoints/call-ajax-orch
       {:endpoint endpoints/list-methods
        :on-done
        (fn [{:keys [success? get-parsed-response status-text]}]
          (if success?
            (swap! state assoc :methods (map #(assoc % :type :method) (get-parsed-response)))
            (swap! state assoc :error-message status-text)))}))}
   (persistence/with-state-persistence
    {:key "method-repo-table-container" :version 1
     :initial {:filter-group-index 0}
     :only [:v :filter-group-index]})))
