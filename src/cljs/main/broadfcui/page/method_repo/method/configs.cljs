(ns broadfcui.page.method-repo.method.configs
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.net :as net]
   [broadfcui.utils :as utils]
   ))

(react/defc Configs
  {:render
   (fn [{:keys [state]}]
     (let [{:keys [configs-error configs]} @state]
       [:div {:style {:margin "2.5rem 1.5rem"}}
        (if configs-error
          [:div {:style {:textAlign "center" :color (:exception-state style/colors)}}
           "Error loading method: " configs-error]
          (if-not configs
            [:div {:style {:textAlign "center" :padding "1rem"}}
             [comps/Spinner {:text "Loading configs..."}]]
            [Table
             {:data configs
              :body {:empty-message "You don't have access to any published configurations for this method."
                     :style table-style/table-light
                     :behavior {:reorderable-columns? false}
                     :columns [{:header "Name" :initial-width 240
                                :column-data :name}
                               {:header "Namespace" :initial-width 240
                                :column-data :namespace}
                               {:header "Snapshot" :initial-width 100 :filterable? false
                                :column-data :snapshotId}
                               {:header "Method Snapshot" :initial-width 150 :filterable? false
                                :column-data #(get-in (utils/log %) [:payload :methodRepoMethod :methodVersion]) :sort-by :text}
                               {:header "Synopsis" :initial-width :auto
                                :column-data :synopsis}]}}]))]))
   :component-did-mount
   (fn [{:keys [this]}]
     (this :refresh))
   :refresh
   (fn [{:keys [props state]}]
     (swap! state dissoc :configs :configs-error)
     (endpoints/call-ajax-orch
      {:endpoint (endpoints/get-agora-method-configs (:method-id props))
       :on-done (net/handle-ajax-response
                 (fn [{:keys [success? parsed-response]}]
                   (if success?
                     (let [configs (map #(assoc % :payload (utils/parse-json-string (:payload %) true)) parsed-response)]
                       (swap! state assoc :configs configs))
                     (swap! state assoc :configs-error (:message parsed-response)))))}))})
