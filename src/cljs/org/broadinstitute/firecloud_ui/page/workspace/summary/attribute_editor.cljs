(ns org.broadinstitute.firecloud-ui.page.workspace.summary.attribute-editor
  (:require
    clojure.set
    [clojure.string :refer [join trim split]]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.table-utils :as table-utils]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- boolean? [x]
  (or (true? x) (false? x)))

(defn- parse-boolean [attr-value]
  (if (contains? #{"true" "yes"} (clojure.string/lower-case attr-value))
    true
    false))


(defn- process-attribute-value [attr-value]
  (if (and (map? attr-value)
           (= #{"itemsType" "items"} (set (keys attr-value))))
    (join ", " (attr-value "items"))
    attr-value))

(defn- get-type-and-string-rep [attr-value]
  (cond (string? attr-value) ["String" attr-value]
        (number? attr-value) ["Number" (str attr-value)]
        (boolean? attr-value) ["Boolean" (str attr-value)]
        (and (map? attr-value)
             (= #{"itemsType" "items"} (-> attr-value keys set)))
        (let [items (attr-value "items")
              first-item (first items)
              str-value (join ", " items)]
          (cond (string? first-item) ["List of Strings" str-value]
                (number? first-item) ["List of Numbers" str-value]
                (boolean? first-item) ["List of Booleans" str-value]
                :else (do (utils/cljslog "Unknown attribute list type:" first-item)
                          ["List of Strings" str-value])))
        :else (do (utils/cljslog "Unknown attribute type:" attr-value)
                  ["String" attr-value])))


(react/defc WorkspaceAttributeViewerEditor
  {:get-attributes
   (fn [{:keys [state]}]
     (let [{:keys [attributes]} @state
           duplicates (not-empty (utils/find-duplicates (map first attributes)))
           any-empty? (some (fn [[k v _]]
                              (let [[ek ev] (map (comp empty? trim) [k v])]
                                (or ek ev)))
                            attributes)
           with-spaces (->> attributes
                            (map (comp trim first))
                            (filter (partial re-find #"\s"))
                            not-empty)
           typed (->> attributes
                      (map (fn [[key value type]]
                             [key (case type
                                    "String" value
                                    "Number" (js/parseFloat value)
                                    "Boolean" (parse-boolean value)
                                    "List of Strings" (map trim (split value #","))
                                    "List of Numbers" (map (comp js/parseFloat trim) (split value #","))
                                    "List of Booleans" (map (comp parse-boolean trim) (split value #","))
                                    value)]))
                      (into {}))]
       (cond duplicates {:error (str "Duplicate keys: " (join ", " duplicates))}
             any-empty? {:error "Empty keys and values are not allowed."}
             with-spaces {:error (str "Keys cannot have spaces: " (join ", " with-spaces))}
             :else {:success typed})))
   :render
   (fn [{:keys [props state after-update]}]
     (let [{:keys [editing?]} props]
       [:div {}
        (style/create-section-header "Workspace Attributes")
        (style/create-paragraph
          [:div {}
           (when editing?
             [:div {:style {:marginBottom "0.25em"}}
              [comps/Button {:icon :add :text "Add new"
                             :onClick (fn [_]
                                        (swap! state update :attributes conj ["" ""])
                                        ;; have to do this by ID not ref, since the fields are generated within Table
                                        (after-update #(.focus (.getElementById js/document "focus"))))}]])
           [table/Table
            {:key (str editing? (count (:attributes @state)))
             :reorderable-columns? false :sortable-columns? (not editing?) :filterable? false :pagination :none
             :empty-message "No Workspace Attributes defined"
             :row-style {:alignItems "stretch"}
             :always-sort? (not editing?)
             :header-row-style {:borderBottom (str "2px solid " (:line-default style/colors))
                                :backgroundColor "white" :color "black" :fontWeight "bold"}
             :resize-tab-color (:line-default style/colors)
             :columns (if editing?
                        [{:starting-width 40 :resizable? false :as-text (constantly "Delete")
                          :content-renderer
                          (fn [index]
                            (icons/icon {:style {:color (:exception-state style/colors)
                                                 :verticalAlign "middle" :height 26
                                                 :cursor "pointer"}
                                         :onClick #(swap! state update :attributes utils/delete index)}
                                        :delete))}
                         {:header "Key" :starting-width 300 :as-text (constantly nil)
                          :content-renderer
                          (fn [{:keys [key index]}]
                            (style/create-text-field (merge
                                                       {:style {:marginBottom 0 :fontSize "100%" :height 26 :width "calc(100% - 2px)"}
                                                        :defaultValue key
                                                        :onChange #(swap! state update-in [:attributes index]
                                                                          assoc 0 (-> % .-target .-value))}
                                                       (when (= index (-> (:attributes @state) count dec))
                                                         {:id "focus"}))))}
                         {:header "Value" :starting-width :remaining :as-text (constantly nil) :resizable? false
                          :content-renderer
                          (fn [{:keys [value index]}]
                            (style/create-text-field {:style {:marginBottom 0 :fontSize "100%" :height 26 :width "calc(100% - 2px)"}
                                                      :defaultValue value
                                                      :onChange #(swap! state update-in [:attributes index]
                                                                        assoc 1 (-> % .-target .-value))}))}
                         {:header "Type" :starting-width 130 :as-text (constantly nil) :resizable? false
                          :content-renderer
                          (fn [{:keys [type index]}]
                            (style/create-identity-select
                              {:style {:marginBottom 0 :fontSize "100%" :height 26 :width "calc(100% - 2px)"}
                               :defaultValue type
                               :onChange #(swap! state update-in [:attributes index]
                                                 assoc 2 (-> % .-target .-value))}
                              ["String" "Number" "Boolean" "List of Strings" "List of Numbers" "List of Booleans"]))}]
                        [{:header "Key" :starting-width 300 :as-text name :sort-initial :asc}
                         {:header "Value" :starting-width :remaining :as-text process-attribute-value
                          :content-renderer (comp (table-utils/render-gcs-links (:workspace-bucket props)) process-attribute-value)}])
             :data (if editing?
                     (map-indexed (fn [index [key value type]]
                                    {:index index :key key :value value :type type})
                                  (:attributes @state))
                     (:workspace-attributes props))
             :->row (if editing?
                      (juxt :index identity identity identity)
                      identity)}]])]))
   :component-did-update
   (fn [{:keys [prev-props props state]}]
     (when (and (not (:editing? prev-props)) (:editing? props))
       (swap! state assoc :attributes
              (mapv (fn [[k v]]
                      (let [[type str-value] (get-type-and-string-rep v)]
                        [(name k) str-value type]))
                    (:workspace-attributes props)))))})
