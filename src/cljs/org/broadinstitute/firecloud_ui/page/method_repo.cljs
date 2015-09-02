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


(react/defc MethodsList
  {:render
   (fn [{:keys [props state]}]
     (if (:show-import-overlay? @state)
       [comps/Dialog
        {:blocking? true
         :width "80%"
         :dismiss-self #(swap! state dissoc :show-import-overlay?)
         :content  (importmc/render-import-overlay
                     nil ; nil workspace because entrypoint is  from method repo
                     #(swap! state dissoc :show-import-overlay?) ; on-close
                     nil ; on-import nil because entry from method re
                     (:selected-method @state))}]
     [table/Table
      {:empty-message "No methods to display."
       :columns [{:header "Namespace" :starting-width 150 :sort-by :value}
                 {:header "Name" :starting-width 150 :sort-by :value
                  :content-renderer (fn [row-index msm]
                                      [:a {:style {:color (:button-blue style/colors)
                                                   :textDecoration "none"}
                                           :href "javascript:;"
                                           :onClick #(swap! state assoc :show-import-overlay? true
                                                      :selected-method (str msm))}
                                       (str msm)])}
                 {:header "Synopsis" :starting-width 500 :sort-by :value}]
       :data (map (fn [m]
                    [(m "namespace")
                     (m "name")
                     (m "synopsis")])
               (:methods props))}]))})


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
   (fn [{:keys [state]}]
     (utils/call-ajax-orch
       {:endpoint endpoints/list-methods
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :methods (get-parsed-response))
                     (swap! state assoc :error {:message status-text})))}))})

