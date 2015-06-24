(ns org.broadinstitute.firecloud-ui.page.method-repo
  (:require
   [clojure.string]
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.common :as common]
   [org.broadinstitute.firecloud-ui.common.table :as table]
   [org.broadinstitute.firecloud-ui.utils :as utils :refer [rlog jslog cljslog]]
   ))


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


(react/defc Page
  {:render
   (fn [{:keys [state]}]
     [:div {:style {:padding "1em"}}
      [:h2 {} "Method Repository"]
      [:div {}
       (if-not (:methods-loaded? @state)
         [common/Spinner {:text "Loading methods..."}]
         [MethodsList {:methods (:methods @state)}])]])
   :component-did-mount
   (fn [{:keys [state]}]
     (utils/ajax {:url "todo"
                  :on-done (fn [{:keys [xhr]}]
                             (let [methods (js->clj (js/JSON.parse (.-responseText xhr)))]
                               (swap! state assoc :methods-loaded? true :methods methods)))
                  :canned-response
                  {:responseText (js/JSON.stringify
                                  (clj->js
                                   (if (zero? (rand-int 2))
                                     []
                                     [{"namespace" "broad"
                                       "name" "Print"
                                       "synopsis" "Just prints something"}])))
                   :delay-ms 1000}}))})
