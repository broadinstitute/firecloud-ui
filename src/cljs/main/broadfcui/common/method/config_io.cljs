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
   (fn [{:keys [props state this]}]
     (let [id (gensym "io-table-")]
       [:div {:id id :style (:style props)}
        (when (:editing? @state)
          [:style {} (str ".select2-results__option" ;; TODO - just make this global?
                          "{font-size: 80%}"
                          "#" id " .select2-container--default .select2-selection--multiple .select2-selection__choice__remove"
                          "{display: none}"
                          "#" id " .select2-container .selection li.select2-selection__choice"
                          "{background: none; color: black; margin-right: 0; padding: 0}"
                          "#" id " .select2-selection__rendered"
                          "{padding-top: 0.1rem}"
                          "#" id " .select2-selection__rendered > li+li"
                          "{width: 10px}")])
        [Collapse {:title "Inputs"
                   :contents (this :-render-table :inputs)}]
        [Collapse {:style {:marginTop "1rem"}
                   :title "Outputs"
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
                                          {:type (process-type (or inputType outputType))
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
                                :column-data :task
                                :render (fn [task]
                                          [:div {:style (clip table-style/table-cell-plank-left)} task])}
                               {:header "Variable" :initial-width 180
                                :as-text (fn [{:keys [variable optional?]}]
                                           (str variable (when optional? " (optional)")))
                                :sort-by :text
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
                                   type])}]
                              (when values
                                [{:header "Attribute" :initial-width 200
                                  :as-text :value
                                  :sort-by :text
                                  :render
                                  (fn [{:keys [name value optional?]}]
                                    (let [value (when-not (string/blank? value) value)]
                                      [:div {:style (clip table-style/default-cell-left)}
                                       (if editing?
                                         ;; (ab)using TagAutocomplete instead of Typeahead because it
                                         ;; plays nicer with tables
                                         [comps/TagAutocomplete
                                          {:show-counts? false :allow-clear? true
                                           :minimum-input-length 0
                                           :tags [value] :maximum-selection-length 1
                                           :language {:maximumSelected (fn [] "")}
                                           ;; `value` ensures that custom selections are initially selected when going to edit
                                           ;; `distinct` because having multiple copies of the same screws things up
                                           :data (distinct (if value (conj data value) data))
                                           :placeholder (if optional? "Optional" "Select or enter")
                                           :on-change (fn [value]
                                                        (swap! locals update io-key assoc (keyword name)
                                                               (if (empty? value) "" (first value))))}]
                                         (if value
                                           [:span {:style (when optional? table-style/table-cell-optional)} value]
                                           (when optional?
                                             [:span {:style {:color (:text-lighter style/colors)}} "Optional"])))]))}])
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
