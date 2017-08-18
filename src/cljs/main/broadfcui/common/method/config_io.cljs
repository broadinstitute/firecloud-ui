(ns broadfcui.common.method.config-io
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.components.collapse :refer [Collapse]]
   [broadfcui.utils :as utils]
   ))


(defn- process-name [{:keys [name] :as item}]
  (let [[task variable] (take-last 2 (string/split name "."))]
    (merge item (utils/restructure task variable))))


(def clip (partial merge table-style/clip-text))


(react/defc IOTables
  {:start-editing
   (fn [{:keys [state]}]
     (swap! state assoc :editing? true))
   :cancel-editing
   (fn [{:keys [state]}]
     (swap! state dissoc :editing?))
   :render
   (fn [{:keys [this]}]
     [:div {}
      [Collapse
       {:title "Inputs"
        :contents (this :-render-table :inputs)}]
      [Collapse
       {:style {:marginTop "1rem"}
        :title "Outputs"
        :contents (this :-render-table :outputs)}]])
   :-render-table
   (fn [{:keys [props state]} io-key]
     (let [{:keys [inputs-outputs values invalid-values data]} props
           {:keys [editing?]} @state]
       [Table {:data (map process-name (io-key inputs-outputs))
               :body {:empty-message (str "No " (string/capitalize (name io-key)))
                      :style (merge
                              table-style/table-light
                              {:body-cell {:padding 0}
                               :header-row {:borderBottom style/standard-line}
                               :body-row (constantly {:margin "4px 0" :alignItems (if editing? "center" "baseline")})})
                      :behavior {:filterable? false :reorderable-columns? false :allow-no-sort? true
                                 :sortable-columns? (not editing?)}
                      :columns
                      (concat [{:header "Task" :initial-width 200
                                :column-data :task
                                :render (fn [task]
                                          [:div {:style (clip table-style/table-cell-plank-left)} task])}
                               {:header "Variable" :initial-width 200
                                :column-data :variable
                                :render (fn [variable]
                                          [:div {:style (clip table-style/table-cell-plank-middle)} variable])}
                               {:header "Type" :initial-width 100
                                :column-data (fn [{:keys [inputType outputType optional]}]
                                               {:type (or inputType outputType)
                                                :optional? optional})
                                :sort-by :type
                                :as-text (fn [{:keys [type optional?]}]
                                           (str type (when optional? (" (optional)"))))
                                :render
                                (fn [{:keys [type optional?]}]
                                  [:div {:style (clip table-style/table-cell-plank-right)}
                                   (str type (when optional? (" (optional)")))])}]
                              (when values
                                [{:header "Attribute" :initial-width 200
                                  :as-text (fn [{:keys [name]}] (get (io-key values) (keyword name)))
                                  :sort-by :text
                                  :render
                                  (fn [{:keys [name]}]
                                    (let [value (get (io-key values) (keyword name))]
                                      [:div {:style table-style/default-cell-left}
                                       (if editing?
                                         ;; (ab)using TagAutocomplete instead of Typeahead because it
                                         ;; plays nicer with tables
                                         [comps/TagAutocomplete
                                          {:multiple false :show-counts? false :allow-clear? true
                                           :tags value :data data
                                           :placeholder "Select a value"}]
                                         value)]))}])
                              (when invalid-values
                                [{:header "Message" :initial-width 400
                                  :column-data (fn [{:keys [name]}]
                                                 (get (io-key invalid-values) (keyword name)))
                                  :render
                                  (fn [message]
                                    (when message
                                      [:div {:style (merge table-style/clip-text table-style/default-cell-left)
                                             :title message}
                                       (icons/icon {:style {:marginRight "0.5rem"
                                                            :color (:exception-state style/colors)}}
                                                   :error)
                                       message]))}]))}
               :paginator :none}]))})
