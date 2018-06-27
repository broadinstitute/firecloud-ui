(ns broadfcui.common.method.config-io
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.links :as links]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.components.autosuggest :refer [Autosuggest]]
   [broadfcui.components.collapse :refer [Collapse]]
   [broadfcui.components.foundation-dropdown :as dropdown]
   [broadfcui.components.modals :as modals]
   [broadfcui.page.workspace.data.import-data :as import-data]
   [broadfcui.utils :as utils]
   ))


(defn- process-type [string]
  (re-find #"[^?]+" string))


(def clip (partial merge table-style/clip-text))


(react/defc IOTables
  {:start-editing
   (fn [{:keys [props state]}]
     (let [{:keys [inputs outputs]} @state]
       (swap! state assoc :editing? true)
       (swap! state utils/deep-merge (:values props))
       (swap! state assoc
              :original-inputs inputs
              :original-outputs outputs)))
   :cancel-editing
   (fn [{:keys [state]}]
     (let [{:keys [original-inputs original-outputs]} @state]
       (swap! state dissoc :editing?)
       (swap! state assoc
              :inputs original-inputs
              :outputs original-outputs)))
   :save
   (fn [{:keys [props state]}]
     (swap! state dissoc :editing?)
     {:inputs (select-keys (:inputs @state) (map (comp keyword :name) (:inputs (:inputs-outputs props))))
      :outputs (select-keys (:outputs @state) (map (comp keyword :name) (:outputs (:inputs-outputs props))))})
   :-get-defaultable-outputs
   (fn [{:keys [props state]}]
     (let [{:keys [inputs-outputs]} props
           {:keys [outputs]} @state]
       (filter #(string/blank? ((keyword (:name %)) outputs))
               (:outputs inputs-outputs))))
   :-add-default-outputs
   (fn [{:keys [after-update props locals state this]}]
     (let [{:keys [begin-editing]} props
           {:keys [editing?]} @state
           new-outputs (->> (this :-get-defaultable-outputs)
                            (map (fn [{:keys [name]}] [(keyword name) (str "this." (-> name (string/split ".") last))]))
                            (into {}))]
       (when-not editing? (begin-editing))
       (after-update #(doseq [[k v] (vec new-outputs)] ((k @locals) :set-value v)))
       (swap! state update :outputs merge new-outputs)))
   :component-will-mount
   (fn [{:keys [props state]}]
     (swap! state utils/deep-merge (:values props)))
   :render
   (fn [{:keys [after-update locals props state this]}]
     (let [{:keys [default-hidden? entity-type? style can-edit? begin-editing]} props
           {:keys [editing?]} @state
           id (gensym "io-table-")]
       [:div {:id id :style style}
        (when (:show-upload? @state)
          [modals/OKCancelForm
           {:header "Upload JSON" :show-cancel? true :cancel-text "Close"
            :dismiss #(swap! state dissoc :show-upload?)
            :content [:div {:style {:width 720 :backgroundColor "white" :padding "1em"}}
                      [import-data/Page
                       {:on-upload
                        (fn [{:keys [file-contents]}]
                          (let [uploaded-inputs (utils/parse-json-string file-contents true)
                                new-inputs (merge (:inputs @state) uploaded-inputs)]
                            (when-not editing? (begin-editing))
                            (after-update #(doseq [[k v] (vec new-inputs)] ((k @locals) :set-value v)))
                            (swap! state assoc :inputs new-inputs)
                            (swap! state dissoc :show-upload?)))}]]}])
        [Collapse {:title "Inputs"
                   :secondary-title (when can-edit?
                                      [:div {} (links/create-internal {:data-test-id "populate-with-json-link"
                                                                       :on-click #(swap! state assoc :show-upload? true)}
                                                 "Populate with a .json file...")
                                       (dropdown/render-info-box {:text (links/create-external {:href "https://software.broadinstitute.org/wdl/documentation/inputs.php"
                                                                                                :style {:white-space "nowrap"}} "Learn more about the expected format")})])
                   :default-hidden? default-hidden?
                   :contents (this :-render-table :inputs)}]
        [Collapse {:style {:marginTop "1rem"}
                   :title "Outputs"
                   :secondary-title (when (and entity-type? can-edit? (seq (this :-get-defaultable-outputs)))
                                      (links/create-internal {:onClick #(this :-add-default-outputs)}
                                        "Populate blank attributes with defaults"))
                   :default-hidden? default-hidden?
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
                                       value (get-in @state [io-key k-name])]
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
                               {:header "Variable" :initial-width 220
                                :as-text (fn [{:keys [variable optional?]}]
                                           (str variable (when optional? " (optional)")))
                                :sort-by (comp string/lower-case :text)
                                :render (fn [{:keys [variable optional?]}]
                                          [:div {:style (clip (if optional?
                                                                (merge table-style/table-cell-plank-middle
                                                                       table-style/table-cell-optional)
                                                                table-style/table-cell-plank-middle))} variable])}
                               {:header "Type" :initial-width 110
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
                                [{:header "Attribute" :initial-width 360
                                  :as-text :value
                                  :sort-by :text
                                  :render
                                  (fn [{:keys [name value optional?]}]
                                    [:div {:style (clip table-style/default-cell-left)}
                                     (if editing?
                                       [Autosuggest
                                        {:ref #(swap! locals assoc (keyword name) %)
                                         :key name
                                         :default-value value
                                         :caching? true
                                         :data data
                                         :shouldRenderSuggestions (constantly true)
                                         :inputProps {:data-test-id (str name "-text-input")
                                                      :placeholder (if optional? "Optional" "Select or enter")}
                                         :suggestionsProps {:data-test-id (str name "-suggestions")}
                                         :on-change (fn [value]
                                                      (swap! state update io-key assoc (keyword name)
                                                             (if (empty? value) "" value)))
                                         :theme {:input {:width "calc(100% - 16px)"}}}]
                                       [:span {:style (when optional? table-style/table-cell-optional)}
                                        (if-not (string/blank? value)
                                          [:span {:data-test-id (str name "-display")} value]
                                          [:span {:style {:color (:text-lighter style/colors)}}
                                           (if optional? "Optional" "Required")])])])}])
                              (when invalid-values
                                [{:header "Message" :initial-width 200
                                  :as-text :error-message :sort-by :text
                                  :render
                                  (fn [{:keys [optional? error-message]}]
                                    (when (and error-message (not optional?))
                                      [:div {:style (clip table-style/default-cell-left)}
                                       (icons/render-icon {:style {:marginRight "0.5rem"
                                                                   :color (:state-exception style/colors)}}
                                                          :error)
                                       error-message]))}]))}
               :paginator :none}]))})
