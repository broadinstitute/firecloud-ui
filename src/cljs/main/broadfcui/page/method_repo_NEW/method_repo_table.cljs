(ns broadfcui.page.method-repo-NEW.method-repo-table
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.utils :as utils]
   ))


(react/defc MethodRepoTable
  {:render
   (fn [{:keys [state]}]
     (let [{:keys [methods error]} @state]
       (cond error [comps/ErrorViewer {:error error}]
             (not methods) [comps/Spinner {:text "Loading..."}]
             :else
             [Table {:data methods
                     :style {:content {:paddingLeft "1rem" :paddingRight "1rem"}}
                     :tabs {:style {:margin "-0.6rem -1rem 0.3rem" :padding "0 1rem"
                                    :backgroundColor (:background-light style/colors)}
                            :items [{:label "Public Methods"
                                     :predicate :public}
                                    {:label "My Methods"
                                     :predicate (fn [method]
                                                  (or (not (:public method))
                                                      (contains? (set (:managers method)) (utils/get-user-email))))}]}
                     :body {:behavior {:reorderable-columns? false}
                            :style (utils/deep-merge table-style/table-light
                                                     {:body {:fontWeight "initial" :fontSize "120%"}
                                                      :body-row (constantly {:borderTop style/standard-line
                                                                             :alignItems "center"})})
                            :columns
                            [{:header "Method" :initial-width 300
                              :column-data (juxt :namespace :name)
                              :as-text (partial string/join "/")
                              :sort-by (comp string/lower-case second)
                              :render
                              (fn [[namespace name]]
                                [:div {}
                                 [:div {:style {:fontSize "80%"}} namespace]
                                 [:div {:style {:fontWeight 500}} name]])}
                             {:header "Synopsis" :initial-width 700
                              :column-data :synopsis
                              :sort-by string/lower-case}
                             {:header "Owners" :initial-width 175
                              ;; TODO: fix duplicate owners bug and remove 'distinct'
                              :column-data (comp distinct :managers)
                              :as-text (partial string/join ", ")}
                             {:header "Snapshots" :initial-width 94 :filterable? false
                              :column-data :numSnapshots}
                             {:header "Configurations" :initial-width 117 :filterable? false
                              :column-data :numConfigurations}]}
                     :toolbar {:filter-bar {:inner {:width 300}}
                               :style {:alignItems "flex-start"
                                       :padding "1rem" :margin 0
                                       :backgroundColor (:background-light style/colors)}
                               :get-items
                               (constantly
                                [[comps/Button {:style {:marginLeft "auto"}
                                                :text "Create New Method..."
                                                :icon :add-new}]])}}])))
   :component-did-mount
   (fn [{:keys [state]}]
     (endpoints/call-ajax-orch
      {:endpoint endpoints/list-method-definitions
       :on-done (fn [{:keys [success? get-parsed-response]}]
                  (if success?
                    (swap! state assoc :methods (get-parsed-response))
                    (swap! state assoc :error (get-parsed-response false))))}))})
