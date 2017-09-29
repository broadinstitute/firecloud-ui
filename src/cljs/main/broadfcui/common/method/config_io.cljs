(ns broadfcui.common.method.config-io
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.components.autosuggest :refer [Autosuggest]]
   [broadfcui.components.collapse :refer [Collapse]]
   [broadfcui.utils :as utils]
   ))


(defn- process-type [string]
  (re-find #"[^?]+" string))


(def clip (partial merge table-style/clip-text))


(react/defc IOTables
  {:start-editing
   (fn [{:keys [props state locals]}]
     (swap! locals merge (:values props))
     (swap! state assoc :editing? true))
   :cancel-editing
   (fn [{:keys [state]}]
     (swap! state dissoc :editing?))
   :save
   (fn [{:keys [state locals]}]
     (swap! state dissoc :editing?)
     @locals)
   :render
   (fn [{:keys [props this]}]
     (let [id (gensym "io-table-")]
       [:div {:id id :style (:style props)}
        [Collapse {:title "Inputs"
                   :default-hidden? (:default-hidden? props)
                   :contents (this :-render-table :inputs)}]
        [Collapse {:style {:marginTop "1rem"}
                   :title "Outputs"
                   :default-hidden? (:default-hidden? props)
                   :contents (this :-render-table :outputs)}]]))
   :-render-table
   (fn [{:keys [props state locals]} io-key]
     (let [{:keys [inputs-outputs values invalid-values data]} props
           {:keys [editing?]} @state]
       [Table {:data (->> (io-key inputs-outputs)
                          (map (fn [{:keys [name inputType outputType optional] :as item}]
                                 (let [[task variable] (take-last 2 (string/split name "."))
                                       k-name (keyword name)
                                       error-message (get-in invalid-values [io-key k-name])
                                       value (get-in values [io-key k-name])]
                                   (merge (dissoc item :inputType :outputType :optional)
                                          (utils/restructure task variable error-message value)
                                          {:type (some-> (or inputType outputType) process-type)
                                           :optional? optional})))))
               :body {:empty-message (str "No " (string/capitalize (name io-key)))
                      :style (merge
                              table-style/table-light
                              {:body-cell {:padding 0}
                               :header-row {:borderBottom style/standard-line}
                               :body-row (constantly {:margin "4px 0" :alignItems "baseline"})})
                      :behavior {:filterable? false :reorderable-columns? false :allow-no-sort? true
                                 :sortable-columns? (not editing?)}
                      :columns
                      (concat [{:header "Task" :initial-width 180
                                :sort-by (comp (partial mapv string/lower-case) (juxt :task :variable))
                                :sort-initial :asc
                                :as-text :task
                                :render (fn [{:keys [task]}]
                                          [:div {:style (clip table-style/table-cell-plank-left)} task])}
                               {:header "Variable" :initial-width 180
                                :as-text (fn [{:keys [variable optional?]}]
                                           (str variable (when optional? " (optional)")))
                                :sort-by (comp string/lower-case :text)
                                :render (fn [{:keys [variable optional?]}]
                                          [:div {:style (clip (if optional?
                                                                (merge table-style/table-cell-plank-middle
                                                                       table-style/table-cell-optional)
                                                                table-style/table-cell-plank-middle))} variable])}
                               {:header "Type" :initial-width 120
                                :sort-by :type
                                :as-text (fn [{:keys [type optional?]}]
                                           (str type (when optional? " (optional)")))
                                :render
                                (fn [{:keys [type optional?]}]
                                  [:div {:style (clip (if optional?
                                                        (merge table-style/table-cell-plank-right
                                                               table-style/table-cell-optional)
                                                        table-style/table-cell-plank-right))}
                                   (or type "unknown")])}]
                              (when values
                                [{:header "Attribute" :initial-width 200
                                  :as-text :value
                                  :sort-by :text
                                  :render
                                  (fn [{:keys [name value optional?]}]
                                    [:div {:style (clip table-style/default-cell-left)}
                                     (if editing?
                                       [Autosuggest
                                        {:default-value value
                                         :caching? true
                                         :data data
                                         :shouldRenderSuggestions (constantly true)
                                         :inputProps {:data-test-id (str name "-text-input")
                                                      :placeholder (if optional? "Optional" "Select or enter")}
                                         :on-change (fn [value]
                                                      (swap! locals update io-key assoc (keyword name)
                                                             (if (empty? value) "" value)))
                                         :theme {:input {:width "calc(100% - 16px)"}}}]
                                       (if value
                                         [:span {:style (when optional? table-style/table-cell-optional)} value]
                                         (when optional?
                                           [:span {:style {:color (:text-lighter style/colors)}} "Optional"])))])}])
                              (when invalid-values
                                [{:header "Message" :initial-width 400
                                  :as-text :error-message :sort-by :text
                                  :render
                                  (fn [{:keys [optional? error-message]}]
                                    (when (and error-message (not optional?))
                                      [:div {:style (clip table-style/default-cell-left)}
                                       (icons/icon {:style {:marginRight "0.5rem"
                                                            :color (:exception-state style/colors)}}
                                                   :error)
                                       error-message]))}]))}
               :paginator :none}]))})
