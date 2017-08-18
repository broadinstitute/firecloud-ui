(ns broadfcui.page.workspace.data.entity-viewer
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.entity-table :as entity-table]
   [broadfcui.common.gcs-file-preview :refer [GCSFilePreviewLink]]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.common.table :refer [Table]]
   [broadfcui.common.table.style :as table-style]
   [broadfcui.page.workspace.data.utils :as data-utils]
   [broadfcui.utils :as utils]
   ))

(defn- render-list-item [item]
  (if (entity-table/is-single-ref? item)
    (:entityName item)
    item))

(defn- get-column-name [entity-type]
  (case entity-type
    "sample_set" "Sample"
    "participant_set" "Participant"
    "pair_set" "Pair"
    "Entity"))

(react/defc EntityViewer
  {:get-initial-state
   (fn []
     {:last-entity []})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [update-parent-state entity-type entity-name attr-list]} props
           item-column-name (get-column-name entity-type)
           update-and-load (fn [item-type item-name]
                             (update-parent-state :selected-entity item-name
                                                  :selected-attr-list nil
                                                  :loading-attributes true)
                             (data-utils/get-entity-attrs {:entity-name item-name
                                                           :entity-type item-type
                                                           :workspace-id (:workspace-id props)
                                                           :update-parent-state update-parent-state}))
           item-link (fn [item-type item-name]
                       (links/create-internal
                         {:onClick (fn []
                                     (swap! state update
                                            :last-entity conj {:type (:entity-type props)
                                                               :name (:entity-name props)})
                                     (update-and-load item-type item-name))}
                         item-name))]
       [:div {:style {:width "30%" :padding "0.5rem" :marginLeft ".38em"
                      :border (str "1px solid " (:line-default style/colors))}}
        [:a {:onClick #(update-parent-state :selected-entity nil)
             :href "javascript:;"
             :style {:padding "1rem" :float "right"
                     :fontSize "80%" :color (:text-light style/colors)}}
         (icons/icon {} :close)]
        (when (not-empty (:last-entity @state))
          (let [last-entity (last (:last-entity @state))]
            [:a {:onClick (fn []
                            (swap! state update :last-entity pop)
                            (update-and-load (:type last-entity) (:name last-entity)))
                 :href "javascript:;"
                 :style {:padding "1rem"
                         :color (:text-light style/colors)}}
             (icons/icon {} :angle-left)]))
        [:div {:style {:display "inline-block" :fontWeight "bold" :padding "1rem 0 0 1rem" :marginBottom "1em"}} entity-name]
        [Table {:key entity-type ; key to enforce re-evaluating columns when changing entity types
                :data (or attr-list [])
                :body {:empty-message (if (= item-column-name "Entity")
                                        "No Entity Attributes defined"
                                        (str "No " item-column-name "s defined"))
                       :style (utils/deep-merge
                               table-style/table-heavy
                               {:table {:maxWidth "100%"}
                                :header-row {:borderBottom (str "2px solid " (:line-default style/colors))
                                             :backgroundColor "white" :color "black" :fontWeight "bold"}})
                       :behavior {:reorderable-columns? false :filterable? false}
                       :columns (if (data-utils/is-entity-set? entity-type)
                                  [{:header item-column-name :initial-width :auto
                                    :as-text :entityName :sort-by :text
                                    :sort-initial :asc
                                    :render
                                    (fn [{:keys [entityType entityName]}]
                                      (item-link entityType entityName))}]
                                  [{:header "Attribute" :initial-width 120
                                    :column-data
                                    ;; TODO: can't this just be (comp name key)?
                                    ;; is there ever a time when they don't match?
                                    (fn [[k v]]
                                      (if (map? v)
                                        (:entityType v)
                                        (name k)))
                                    :sort-initial :asc}
                                   {:header "Value" :initial-width :auto
                                    :column-data
                                    (fn [[_ v]]
                                      (cond (map? v)
                                            {:for-sort (string/lower-case (:entityName v))
                                             :for-render (item-link (:entityType v) (:entityName v))}

                                            (common/attribute-list? v)
                                            (let [items (map render-list-item (common/attribute-values v))
                                                  display (if (empty? items)
                                                            "0 items"
                                                            (str (count items) " items: " (string/join ", " items)))]
                                              {:for-sort (string/lower-case display)
                                               :for-render display})

                                            :else
                                            (if-let [parsed (common/parse-gcs-uri v)]
                                              {:for-sort (string/lower-case v)
                                               :for-render [GCSFilePreviewLink (assoc parsed
                                                                                 :attributes {:style {:display "inline"}}
                                                                                 :link-label v)]}
                                              {:for-sort (string/lower-case v)
                                               :for-render v})))
                                    :sort-by :for-sort
                                    :render :for-render}])}
                :paginator :none}]]))})
