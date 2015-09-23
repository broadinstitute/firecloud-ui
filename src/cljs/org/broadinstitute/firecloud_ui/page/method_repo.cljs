(ns org.broadinstitute.firecloud-ui.page.method-repo
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common.components :as comps]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.common.table :as table]
   [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
   [org.broadinstitute.firecloud-ui.page.workspace.method-config-importer :as importmc]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))


(defn- get-ordered-id-fields [method]
  (mapv #(get-in method ["method" %]) ["namespace" "name" "snapshotId"]))


(react/defc MethodsList
  {:render
   (fn [{:keys [props state]}]
     [:div {}
     (when (:show-import-overlay? @state)
       [comps/Dialog
        {:width "75%"
         :blocking? true
         :content (importmc/render-import-overlay
                    nil ; no workspace selected
                    #(swap! state assoc :show-import-overlay? false) ;on-close
                    #(swap! state assoc
                      :show-import-overlay? false
                      :selected-config nil
                      :selected-method nil) ;on-export
                    (:selected-method @state)
                    (:selected-config @state))}])
     [table/Table
      {:empty-message "No method configurations to display."
       :columns [{:header "Method" :starting-width 250
                  :sort-by  #(get-ordered-id-fields %)
                  :filter-by #(clojure.string/join ":" (get-ordered-id-fields %))
                  :content-renderer
                  (fn [method]
                    [:a {:style {:color (:button-blue style/colors) :textDecoration "none"}
                         :href "javascript:;"
                         :onClick (fn []
                                    (swap! state assoc
                                      :selected-method (get-in method ["method" "name"])
                                      :selected-config method
                                      :show-import-overlay? true))}
                     (clojure.string/join ":" (get-ordered-id-fields method))])}
                 {:header "Method Synopsis"
                  :content-renderer #(get-in % ["method" "synopsis"])
                  :starting-width 150 :sort-by :value }
                 {:header "Configuration Name" :starting-width 150 :sort-by :value}
                 {:header "Namespace" :starting-width 150 :sort-by :value}
                 {:header "Snapshot ID" :starting-width 150 :sort-by :value  }
                 {:header "Synopsis" :starting-width 500 :sort-by :value}]
       :data (map (fn [m]
                      [m
                       m
                       (m "name")
                       (m "namespace")
                       (m "snapshotId")
                       (m "synopsis")])
               (:methods props))}]])})


(react/defc Page
  {:render
   (fn [{:keys [state]}]
     [:div {:style {:padding "1em"}}
      [:h2 {} "Method Repository"]
      (cond
        (:methods @state) [MethodsList {:methods (:methods @state)}]
        (:error @state) (style/create-server-error-message (get-in @state [:error :message]))
        :else [comps/Spinner {:text "Loading methods..."}])])
   :component-did-mount
   (fn [{:keys [state this]}]
     (endpoints/call-ajax-orch
       {:endpoint endpoints/list-configurations
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :methods (get-parsed-response))
                     (swap! state assoc :error {:message status-text})))}))})

