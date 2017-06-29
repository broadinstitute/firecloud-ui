(ns broadfcui.page.workspace.summary.attribute-editor
  (:require
    clojure.set
    [clojure.string :refer [join trim split]]
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.common.table :as table]
    [broadfcui.common.table-utils :as table-utils]
    [broadfcui.config :as config]
    [broadfcui.page.workspace.data.import-data :as import-data]
    [broadfcui.utils :as utils]
    ))


(def ^:private STRING "String")
(def ^:private NUMBER "Number")
(def ^:private BOOLEAN "Boolean")
(def ^:private LIST_STRING "List of strings")
(def ^:private LIST_NUMBER "List of numbers")
(def ^:private LIST_BOOLEAN "List of booleans")
(def ^:private list-types #{LIST_STRING LIST_NUMBER LIST_BOOLEAN})
(def ^:private all-types [STRING NUMBER BOOLEAN LIST_STRING LIST_NUMBER LIST_BOOLEAN])


(defn- parse-boolean [attr-value]
  (if (contains? #{"true" "yes"} (clojure.string/lower-case attr-value))
    true
    false))


(defn- process-attribute-value [attr-value]
  (if (and (map? attr-value)
           (= #{:itemsType :items} (set (keys attr-value))))
    (join ", " (:items attr-value))
    attr-value))

(defn- get-type-and-string-rep [attr-value]
  (cond (nil? attr-value) [STRING ""]
        (string? attr-value) [STRING attr-value]
        (number? attr-value) [NUMBER (str attr-value)]
        (boolean? attr-value) [BOOLEAN (str attr-value)]
        (and (map? attr-value)
             (= #{:itemsType :items} (-> attr-value keys set)))
        (let [items (:items attr-value)
              first-item (first items)
              str-value (join ", " items)]
          (cond (string? first-item) [LIST_STRING str-value]
                (number? first-item) [LIST_NUMBER str-value]
                (boolean? first-item) [LIST_BOOLEAN str-value]
                :else (do (utils/cljslog "Unknown attribute list type:" first-item)
                          [LIST_STRING str-value])))
        :else (do (utils/cljslog "Unknown attribute type:" attr-value)
                  [STRING attr-value])))

(defn- valid-number? [string]
  (re-matches #"-?(?:\d*\.)?\d+" string))


(defn- header [text]
  [:span {:style {:fontSize "120%"}} text])

(react/defc WorkspaceAttributeViewerEditor
  {:get-attributes
   (fn [{:keys [state]}]
     (let [{:keys [attributes]} @state
           listified-attributes (map (fn [[key value type]]
                                       [(trim key)
                                        (if (contains? list-types type)
                                          (map trim (split value #","))
                                          (trim value))
                                        type])
                                     attributes)
           duplicates (not-empty (utils/find-duplicates (map first listified-attributes)))
           any-empty? (some (fn [[key value _]]
                              (or (empty? key) (empty? value)))
                            listified-attributes)
           with-spaces (->> listified-attributes
                            (map first)
                            (filter (partial re-find #"\s"))
                            not-empty)
           invalid-numbers (->> listified-attributes
                                (keep (fn [[key value type]]
                                        (cond (= type NUMBER) (when-not (valid-number? value) key)
                                              (= type LIST_NUMBER) (when-not (every? valid-number? value) key))))
                                not-empty)
           typed (->> listified-attributes
                      (map (fn [[key value type]]
                             [key (condp = type
                                    NUMBER (js/parseFloat value)
                                    BOOLEAN (parse-boolean value)
                                    LIST_NUMBER (map js/parseFloat value)
                                    LIST_BOOLEAN (map parse-boolean value)
                                    value)]))
                      (into {}))]
       (cond duplicates {:error (str "Duplicate keys: " (join ", " duplicates))}
             any-empty? {:error "Empty keys and values are not allowed."}
             with-spaces {:error (str "Keys cannot have spaces: " (join ", " with-spaces))}
             invalid-numbers {:error (str "Invalid number for key(s): " (join ", " invalid-numbers))}
             :else {:success typed})))
   :render
   (fn [{:keys [props state after-update]}]
     (let [{:keys [editing? writer?]} props]
       [common/Expando
        {:style {:marginBottom "2rem"}
         :default-hidden? true
         :title
         [:div {:style {:flexShrink 0}} (style/create-section-header "Workspace Attributes")]
         :title-expand
         [:div {:style {:flexGrow 1 :fontSize "125%" :fontWeight 500}}
          (when-not editing?
            [:span {:style {:fontSize "initial" :fontWeight "initial"}}
             [:a {:style {:textDecoration "none" :marginLeft "1em"}
                  :href (str (config/api-url-root) "/cookie-authed/workspaces/"
                             (:namespace (:workspace-id props)) "/"
                             (:name (:workspace-id props)) "/exportAttributesTSV")
                  :onClick #(utils/set-access-token-cookie (utils/get-access-token))
                  :target "_blank"}
              (str "Download Attributes")]
             (when writer?
               [comps/Button {:text "Import Attributes..."
                              :style {:float "right" :marginTop -7}
                              :onClick #(modal/push-modal
                                         [comps/OKCancelForm
                                          {:header "Import Attributes" :show-cancel? true :cancel-text "Close"
                                           :content [:div {:style {:width 720 :backgroundColor "white" :padding "1em"}}
                                                     [import-data/Page (merge (select-keys props [:workspace-id])
                                                                              {:on-data-imported (:request-refresh props)}
                                                                              {:import-type "workspace-attributes"})]]}])}])])]
         :contents
         [:div {:style {:marginTop "1rem" :fontSize "90%" :lineHeight 1.}}
          (if editing?
            [:div {:style {:marginBottom "0.25em"}}
             [comps/Button {:icon :add-new :text "Add new"
                            :onClick (fn [_]
                                       (swap! state update :attributes conj ["" ""])
                                       ;; have to do this by ID not ref, since the fields are generated within Table
                                       (after-update #(.focus (.getElementById js/document "focus"))))}]])
          [table/Table
           {:key (str editing? (count (:attributes @state)))
            :reorderable-columns? false :sortable-columns? (not editing?) :filterable? false :pagination :none
            :empty-message "No Workspace Attributes defined"
            :row-style {:alignItems "center" :fontSize "120%"}
            :always-sort? (not editing?)
            :header-row-style {:borderBottom (str "2px solid " (:line-default style/colors))
                               :backgroundColor "white" :color "black" :fontWeight "bold"}
            :resize-tab-color (:line-default style/colors)
            :columns (if editing?
                       [{:starting-width 40 :resizable? false :as-text (constantly "Delete")
                         :content-renderer
                         (fn [index]
                           (icons/icon {:style {:color (:text-lightest style/colors)
                                                :verticalAlign "middle" :fontSize 22
                                                :cursor "pointer"}
                                        :onClick #(swap! state update :attributes utils/delete index)}
                                       :remove))}
                        {:header (header "Key") :starting-width 300 :as-text (constantly nil)
                         :content-renderer
                         (fn [{:keys [key index]}]
                           (style/create-text-field (merge
                                                     {:style {:marginBottom 0 :fontSize "100%" :height 26 :width "calc(100% - 2px)"}
                                                      :defaultValue key
                                                      :onChange #(swap! state update-in [:attributes index]
                                                                        assoc 0 (-> % .-target .-value))}
                                                     (when (= index (-> (:attributes @state) count dec))
                                                       {:id "focus"}))))}
                        {:header (header "Value") :starting-width :remaining :as-text (constantly nil) :resizable? false
                         :content-renderer
                         (fn [{:keys [value index]}]
                           (style/create-text-field {:style {:marginBottom 0 :fontSize "100%" :height 26 :width "calc(100% - 2px)"}
                                                     :defaultValue value
                                                     :onChange #(swap! state update-in [:attributes index]
                                                                       assoc 1 (-> % .-target .-value))}))}
                        {:header (header "Type") :starting-width 150 :as-text (constantly nil) :resizable? false
                         :content-renderer
                         (fn [{:keys [type index]}]
                           (style/create-identity-select
                            {:style {:marginBottom 0 :fontSize "100%" :height 26 :width "calc(100% - 2px)"}
                             :defaultValue type
                             :onChange #(swap! state update-in [:attributes index]
                                               assoc 2 (-> % .-target .-value))}
                            all-types))}]
                       [{:header (header "Key") :starting-width 300 :as-text name :sort-initial :asc}
                        {:header (header "Value") :starting-width :remaining :as-text process-attribute-value
                         :content-renderer (comp (table-utils/render-gcs-links (:workspace-bucket props)) process-attribute-value)}])
            :data (if editing?
                    (map-indexed (fn [index [key value type]]
                                   {:index index :key key :value value :type type})
                                 (:attributes @state))
                    (:workspace-attributes props))
            :->row (if editing?
                     (juxt :index identity identity identity)
                     identity)}]]}]))
   :component-did-update
   (fn [{:keys [prev-props props state]}]
     (when (and (not (:editing? prev-props)) (:editing? props))
       (swap! state assoc :attributes
              (mapv (fn [[k v]]
                      (let [[type str-value] (get-type-and-string-rep v)]
                        [(name k) str-value type]))
                    (:workspace-attributes props)))))})
