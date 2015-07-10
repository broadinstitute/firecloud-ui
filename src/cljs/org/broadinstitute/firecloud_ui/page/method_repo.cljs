;; Create a namespace for these utilities
;; requires imports
(ns org.broadinstitute.firecloud-ui.page.method-repo
  (:require
    ;; require clojure.string
   [clojure.string]
    ;; require dmohs.react
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.table :as table]
   [org.broadinstitute.firecloud-ui.log-utils :as utils :refer [rlog jslog cljslog]]
   [org.broadinstitute.firecloud-ui.utils :as net_utils]
   ))




;; HTML definition for methods list
(react/defc MethodsList
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:padding "0 4em"}}
      (if (zero? (count (:methods props)))
        [:div {:style {:textAlign "center" :backgroundColor (:background-gray common/colors)
                       :padding "1em 0" :borderRadius 8}}
         "No methods to display."]
        [table/Table
         (let [cell-style {:flexBasis "8ex" :flexGrow 1 :whiteSpace "nowrap" :overflow "hidden"
                           :borderLeft (str "1px solid " (:line-gray common/colors))}
               header-label (fn [text & [padding]]
                              [:span {:style {:paddingLeft (or padding "1em")}}
                               [:span {:style {:fontSize "90%"}} text]])]
           {:columns [{:label (header-label "Namespace")
                       :style (merge cell-style {:borderLeft "none"})}
                      {:label (header-label "Name")
                       :style cell-style
                       :header-style {:borderLeft "none"}}
                      {:label (header-label "Synopsis")
                       :style (merge cell-style {:flexBasis "30ex"})
                       :header-style {:borderLeft "none"}}]
            :data (map (fn [m]
                         [(m "namespace")
                          (m "name")
                          (m "synopsis")])
                       (:methods props))})])])})



;; helper function to test for containment
(defn- contains-text [text fields]
  (fn [method]
    (some #(not= -1 (.indexOf (method %) text)) fields)))



;; filtering methods by containment
(defn- filter-methods [methods fields text]
  (filter (contains-text text fields) methods))


;; create mock methods as placeholders
(defn- create-mock-methods []
  (map
    (fn [i]
      {:namespace (rand-nth ["broad" "public" "nci"])
       :name (str "Method " (inc i))
       :synopsis (str "This is method " (inc i))})
    (range (rand-int 100))))


;; define 'Page' to be rendered for HTML
(react/defc Page
  {:render
   (fn [{:keys [state refs]}]
     [:div {:style {:padding "1em"}}
      [:h2 {} "Method Repository"]
      [:div {}
       (cond
         (:methods-loaded? @state) [:div {}
                                    (let [apply-filter #(swap! state assoc
                                                          :filter-text
                                                          (.-value (.getDOMNode (@refs "input"))))]
                                      [:div {:style {:paddingBottom "1em" :paddingLeft "4em"}}
                                       [:input {:style common/input-text-style
                                                :type "text" :ref "input" :placeholder "Filter"
                                                :onKeyDown (fn [e]
                                                             (when (= 13 (.-keyCode e))
                                                               (apply-filter)))}]
                                       [:span {:style {:paddingLeft "1em"}}]
                                       [common/Button {:text "Go" :onClick apply-filter}]])
                                    [MethodsList {:methods (filter-methods
                                                             (:methods @state)
                                                             '("namespace" "name" "synopsis")
                                                             (or (:filter-text @state) ""))}]]
         (:error-message @state) [:div {:style {:color "red"}}
                                  "FireCloud service returned error: " (:error-message @state)]
         :else [common/Spinner {:text "Loading methods..."}])]])
   :component-did-mount
   (fn [{:keys [state]}]
     (net_utils/ajax-orch
       "/methods"
       {:on-done (fn [{:keys [success? xhr]}]
                   (if success?
                     (let [methods (net_utils/parse-json-string (.-responseText xhr))]
                       (swap! state assoc :methods-loaded? true :methods methods))
                     (swap! state assoc :error-message (.-statusText xhr))))
        ;; TODO, replace create-mock methods!
        :canned-response {:responseText (net_utils/->json-string (create-mock-methods))
                          :status 200
                          :delay-ms (rand-int 2000)}}))})

