(ns org.broadinstitute.firecloud-ui.page.method-repo
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common.components :as comps]
   [org.broadinstitute.firecloud-ui.common.style :as style]
   [org.broadinstitute.firecloud-ui.common.table :as table]
   [org.broadinstitute.firecloud-ui.paths :refer [get-methods-path]]
   [org.broadinstitute.firecloud-ui.utils :as utils]
   ))


(react/defc MethodsList
  {:render
   (fn [{:keys [props]}]
     (if (zero? (count (:methods props)))
       (style/create-message-well "No methods to display.")
       [table/Table
        {:columns [{:header "Namespace" :starting-width 150 :sort-by :value}
                   {:header "Name" :starting-width 150 :sort-by :value}
                   {:header "Synopsis" :starting-width 500 :sort-by :value}]
         :data (map (fn [m]
                      [(m "namespace")
                       (m "name")
                       (m "synopsis")])
                 (:methods props))}]))})


(defn- create-mock-methods []
  (map
    (fn [i]
      {:namespace (rand-nth ["broad" "public" "nci"])
       :name (str "Method " (inc i))
       :synopsis (str "This is method " (inc i))})
    (range (rand-int 100))))


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
       (get-methods-path)
       {:on-success (fn [{:keys [parsed-response]}]
                      (swap! state assoc :methods parsed-response))
        :on-failure (fn [{:keys [status-text]}]
                      (swap! state assoc :error {:message status-text}))
        :mock-data (create-mock-methods)}))})

