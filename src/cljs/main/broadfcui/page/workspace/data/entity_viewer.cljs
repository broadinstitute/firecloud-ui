(ns broadfcui.page.workspace.data.entity-viewer
  (:require
    [dmohs.react :as react]
    [clojure.set :refer [union]]
    clojure.string
    [broadfcui.common :as common]
    [broadfcui.common.components :as comps]
    [broadfcui.common.entity-table :as entity-table :refer [EntityTable]]
    [broadfcui.common.gcs-file-preview :refer [GCSFilePreviewLink]]
    [broadfcui.common.style :as style]
    [broadfcui.common.table :as table]
    [broadfcui.page.workspace.data.utils :as data-utils]
    [broadfcui.common.icons :as icons]
    [broadfcui.utils :as utils]
    ))

(defn- render-list-item [item]
  (if (entity-table/is-single-ref? item)
    (:entityName item)
    item))

(defn- get-column-name [entity-type]
  (case (str entity-type)
    "sample_set" "Sample"
    "participant_set" "Participant"
    "pair_set" "Pair"
    "Entity"))

(react/defc EntityViewer
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [update-parent-state entity-type entity-name attr-list]} props
           item-column-name (get-column-name entity-type)
           update-and-load (fn [item-type item-name]
                             (update-parent-state :selected-entity item-name
                                                  :selected-entity-type item-type
                                                  :selected-attr-list nil
                                                  :loading-attributes true)
                             (data-utils/get-entity-attrs {:entity-name item-name
                                                           :entity-type item-type
                                                           :workspace-id (:workspace-id props)
                                                           :update-parent-state update-parent-state}))
           item-link (fn [item-type item-name]
                       (style/create-link
                        {:text item-name
                         :onClick (fn []
                                    (swap! state assoc
                                           :last-entity (:entity-name props)
                                           :last-entity-type (:entity-type props))
                                    (update-and-load item-type item-name))}))]
       [:div {:style {:flex "0 0 30%" :padding "0.5rem" :marginLeft ".38em"
                      :border (str "1px solid " (:line-default style/colors))}}
        [:a {:onClick #(update-parent-state :selected-entity nil)
             :href "javascript:;"
             :style {:padding "1rem" :float "right"
                     :fontSize "80%" :color (:text-light style/colors)}}
         (icons/icon {} :close)]
        (when (:last-entity @state)
          [:a {:onClick #(update-and-load (:last-entity-type @state) (:last-entity @state))
               :href "javascript:;"
               :style {:padding "1rem"
                       :color (:text-light style/colors)}}
           (icons/icon {} :angle-left)])
        [:div {:style {:display "inline-block" :fontWeight "bold" :padding "1rem 0 0 1rem" :marginBottom "1em"}} entity-name]
        [table/Table {:reorderable-columns? false
                      :width :narrow
                      :pagination :none
                      :filterable? false
                      :initial-rows-per-page 500
                      :always-sort? true
                      :header-row-style {:borderBottom (str "2px solid " (:line-default style/colors))
                                         :backgroundColor "white" :color "black" :fontWeight "bold"}
                      :empty-message (if (= item-column-name "Entity")
                                       "No Entity Attributes defined"
                                       (str "No " item-column-name "s defined"))
                      :columns (if (data-utils/is-entity-set? entity-type)
                                 [{:header item-column-name
                                   :starting-width :remaining :resizable? false
                                   :sort-initial :asc :sort-by :text
                                   :as-text :entityName :content-renderer identity}]
                                 [{:header "Attribute" :starting-width 120 :sort-initial :asc}
                                  {:header "Value" :starting-width :remaining :resizable? false
                                   :as-text :name :sort-by :text
                                   :content-renderer
                                   (fn [attr-value]
                                     (if-let [parsed (common/parse-gcs-uri attr-value)]
                                       [GCSFilePreviewLink (assoc parsed
                                                             :attributes {:style {:display "inline"}}
                                                             :link-label attr-value)]
                                       attr-value))}])
                      :data (seq attr-list)
                      :->row
                      (fn [entity]
                        (cond
                          (map? entity)                     ; entity is a member of a set
                          [(item-link (:entityType entity) (:entityName entity))]
                          (map? (last entity))              ; entity is a set
                          (let [item-type (:entityType (last entity))]
                            [item-type
                             (item-link item-type (:entityName (last entity)))])
                          :else
                          (let [name (name (first entity))
                                item (last entity)]
                            [name
                             (if (common/attribute-list? item)
                               (let [items (map render-list-item (common/attribute-values item))]
                                 (if (empty? items)
                                   "0 items"
                                   (str (count items) " items: " (clojure.string/join ", " items))))
                               item)])))}]]))})
