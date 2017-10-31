(ns broadfcui.page.workspace.data.entity-viewer
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
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
   :component-will-mount
   (fn [{:keys [this props]}]
     (let [{:keys [entity-type entity-name]} props]
       (this :-update-and-load entity-type entity-name)))
   :component-will-receive-props
   (fn [{:keys [this next-props]}]
     (let [{:keys [entity-type entity-name]} next-props]
       (this :-update-and-load entity-type entity-name)))
   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [update-parent-state]} props
           {:keys [selected-entity selected-entity-type selected-attr-list]} @state
           item-column-name (get-column-name selected-entity-type)
           item-link (fn [item-type item-name]
                       (links/create-internal
                        {:onClick (fn []
                                    (swap! state update
                                           :last-entity conj {:type (:entity-type props)
                                                              :name (:entity-name props)})
                                    (this :-update-and-load item-type item-name))}
                        item-name))]
       [:div {:style {:position "relative" :width "30%" :padding "0.5rem" :marginLeft ".38em"
                      :border (str "1px solid " (:line-default style/colors))}}
        [:a {:onClick #(update-parent-state :selected-entity nil)
             :href "javascript:;"
             :style {:padding "1rem" :float "right"
                     :fontSize "80%" :color (:text-light style/colors)}}
         (icons/render-icon {} :close)]
        (when (:loading-attributes? @state)
          [comps/Blocker {:banner "Loading entity attributes..."}])
        (when (not-empty (:last-entity @state))
          (let [last-entity (last (:last-entity @state))]
            [:a {:onClick (fn []
                            (swap! state update :last-entity pop)
                            (this :-update-and-load (:type last-entity) (:name last-entity)))
                 :href "javascript:;"
                 :style {:padding "1rem"
                         :color (:text-light style/colors)}}
             (icons/render-icon {} :angle-left)]))
        [:div {:style {:display "inline-block" :fontWeight "bold" :padding "1rem 0 0 1rem" :marginBottom "1em"}} selected-entity]
        [Table {:key selected-entity-type ; key to enforce re-evaluating columns when changing entity types
                :data (or selected-attr-list [])
                :body {:empty-message (if (= item-column-name "Entity")
                                        "No Entity Attributes defined"
                                        (str "No " item-column-name "s defined"))
                       :style (utils/deep-merge
                               table-style/table-heavy
                               {:table {:maxWidth "100%"}
                                :header-row {:borderBottom (str "2px solid " (:line-default style/colors))
                                             :backgroundColor "white" :color "black" :fontWeight "bold"}})
                       :behavior {:reorderable-columns? false :filterable? false}
                       :columns (if (data-utils/is-entity-set? selected-entity-type)
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
                :paginator :none}]]))
   :-update-and-load
   (fn [{:keys [state props]} item-type item-name]
     (let [update-viewer-state (fn [& args]
                                 (apply swap! state assoc args))]
       (swap! state assoc :selected-entity item-name :selected-entity-type item-type
              :loading-attributes? true :selected-attr-list nil)
       (data-utils/get-entity-attrs {:entity-name item-name
                                     :entity-type item-type
                                     :workspace-id (:workspace-id props)
                                     :update-parent-state update-viewer-state})))})
