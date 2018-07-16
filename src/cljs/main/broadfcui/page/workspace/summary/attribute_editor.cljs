(ns broadfcui.page.workspace.summary.attribute-editor
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.common.table.utils :as table-utils]
   [broadfcui.components.buttons :as buttons]
   [broadfcui.components.collapse :refer [Collapse]]
   [broadfcui.components.modals :as modals]
   [broadfcui.config :as config]
   [broadfcui.page.workspace.data.import-data :as import-data]
   [broadfcui.utils :as utils]
   [broadfcui.utils.user :as user]
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
  (if (contains? #{"true" "yes"} (string/lower-case attr-value))
    true
    false))


(defn- process-attribute-value [attr-value]
  (if (and (map? attr-value)
           (= #{:itemsType :items} (set (keys attr-value))))
    (string/join ", " (:items attr-value))
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
              str-value (string/join ", " items)]
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
                                       [(string/trim key)
                                        (if (contains? list-types type)
                                          (map string/trim (string/split value #","))
                                          (string/trim value))
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
       (cond duplicates {:error (str "Duplicate keys: " (string/join ", " duplicates))}
             any-empty? {:error "Empty keys and values are not allowed."}
             with-spaces {:error (str "Keys cannot have spaces: " (string/join ", " with-spaces))}
             invalid-numbers {:error (str "Invalid number for key(s): " (string/join ", " invalid-numbers))}
             :else {:success typed})))
   :get-initial-state
   (fn []
     {:table-key (gensym)})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [editing? writer?]} props]
       [Collapse
        {:data-test-id "attribute-editor"
         :style {:marginBottom "2rem"}
         :default-hidden? true
         :title
         [:div {:style {:flexShrink 0}} (style/create-section-header "Workspace Attributes")]
         :title-expand
         [:div {:style {:flexGrow 1 :fontSize "125%" :fontWeight 500}}
          (when (:show-import? @state)
            [modals/OKCancelForm
             {:header "Import Attributes" :show-cancel? true :cancel-text "Close"
              :dismiss #(swap! state dissoc :show-import?)
              :content [:div {:style {:width 720 :backgroundColor "white" :padding "1em"}}
                        [import-data/Page (merge (select-keys props [:workspace-id])
                                                 {:on-data-imported (:request-refresh props)}
                                                 {:import-type "workspace-attributes"})]]}])
          (when-not editing?
            [:span {:style {:fontSize "initial" :fontWeight "initial"}}
             [:a {:style {:textDecoration "none" :marginLeft "1em"}
                  :href (str (config/api-url-root) "/cookie-authed/workspaces/"
                             (:namespace (:workspace-id props)) "/"
                             (:name (:workspace-id props)) "/exportAttributesTSV")
                  :onClick #(user/set-access-token-cookie (user/get-access-token))
                  :target "_blank"}
              (str "Download Attributes")]
             (when writer?
               [buttons/Button {:text "Import Attributes..."
                                :style {:float "right" :marginTop -7}
                                :onClick #(swap! state assoc :show-import? true)}])])]
         :contents
         [:div {:style {:marginTop "1rem" :fontSize "90%" :lineHeight 1.}}
          (if editing?
            [:div {:style {:marginBottom "0.25em"}}
             [buttons/Button {:icon :add-new :text "Add new"
                              :onClick (fn [_]
                                         (utils/multi-swap! state (assoc :table-key (gensym))
                                                                  (update :attributes conj ["" ""])))}]])
          [Table
           {:data-test-id "workspace-attributes"
            :key (:table-key @state)
            :data (if editing?
                    (map-indexed (fn [index [key value type]]
                                   (utils/restructure index key value type))
                                 (:attributes @state))
                    (:workspace-attributes props))
            :body {:empty-message "No Workspace Attributes defined"
                   :style (utils/deep-merge table-style/table-light
                                            {:header-row {:borderBottom (str "2px solid " (:line-default style/colors))
                                                          :backgroundColor "white" :color "black" :fontWeight "bold"}
                                             :body-row (constantly {:alignItems "center" :fontSize "120%"})})
                   :behavior {:reorderable-columns? false :filterable? false
                              :sortable-columns? (not editing?) :allow-no-sort? editing?}
                   :columns (if editing?
                              [{:id "delete" :initial-width 40 :resizable? false
                                :as-text (constantly "Delete")
                                :render
                                (fn [{:keys [key index]}]
                                  (icons/render-icon {:data-test-id (str key "-delete")
                                                      :style {:color (:text-lightest style/colors)
                                                              :verticalAlign "middle" :fontSize 22
                                                              :cursor "pointer"}
                                                      :onClick #(utils/multi-swap! state (assoc :table-key (gensym))
                                                                                         (update :attributes utils/delete index))}
                                                     :remove))}
                               {:id "key" :header (header "Key") :initial-width 300
                                :as-text (constantly nil)
                                :render
                                (fn [{:keys [key index]}]
                                  (style/create-text-field {:data-test-id (str key "-key")
                                                            :style {:marginBottom 0 :fontSize "100%" :height 26 :width "calc(100% - 2px)"}
                                                            :defaultValue key
                                                            :onChange #(swap! state update-in [:attributes index]
                                                                              assoc 0 (-> % .-target .-value))
                                                            :autoFocus (= index (-> (:attributes @state) count dec))}))}
                               {:id "value" :header (header "Value") :initial-width :auto :resizable? false
                                :as-text (constantly nil)
                                :render
                                (fn [{:keys [value index]}]
                                  (style/create-text-field {:data-test-id (str value "-value")
                                                            :style {:marginBottom 0 :fontSize "100%" :height 26 :width "calc(100% - 2px)"}
                                                            :defaultValue value
                                                            :onChange #(swap! state update-in [:attributes index]
                                                                              assoc 1 (-> % .-target .-value))}))}
                               {:id "type" :header (header "Type") :initial-width 150 :resizable? false
                                :as-text (constantly nil)
                                :render
                                (fn [{:keys [type index]}]
                                  (style/create-identity-select
                                    {:data-test-id (str key "-type")
                                     :style {:marginBottom 0 :fontSize "100%" :height 26 :width "calc(100% - 2px)"}
                                     :defaultValue type
                                     :onChange #(swap! state update-in [:attributes index]
                                                       assoc 2 (-> % .-target .-value))}
                                    all-types))}]
                              [{:id "key" :header (header "Key") :initial-width 300
                                :column-data key
                                :as-text name :sort-initial :asc}
                               {:id "value" :header (header "Value") :initial-width :auto
                                :column-data val
                                :as-text process-attribute-value
                                :render (comp (table-utils/render-gcs-links (:workspace-bucket props) (get-in props [:workspace-id :namespace])) process-attribute-value)}])}
            :paginator :none}]]}]))
   :component-did-update
   (fn [{:keys [prev-props props state]}]
     (when (and (not (:editing? prev-props)) (:editing? props))
       (swap! state assoc
              :table-key (gensym)
              :attributes (mapv (fn [[k v]]
                                  (let [[type str-value] (get-type-and-string-rep v)]
                                    [(name k) str-value type]))
                                (:workspace-attributes props)))))
   :component-will-receive-props
   (fn [{:keys [props next-props state]}]
     (when (not= (:editing? props) (:editing? next-props))
       (swap! state assoc :table-key (gensym))))})
