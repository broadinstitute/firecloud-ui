(ns org.broadinstitute.firecloud-ui.page.workspace.copy-data-entities
  (:require
    [clojure.string]
    [dmohs.react :as react]
    [clojure.set :refer [union]]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common :as common]
    ))

(react/defc EntitiesList
  {:get-initial-state
   (fn [{:keys [props]}]
     {:entities (get (:entity-map props) (first (keys (:entity-map props))))})
   :render
   (fn [{:keys [props state refs]}]
     [:div {:style {:margin "1em"}}
      [:h3 {}
       "Select an entity to copy from "
       (get-in (:selected-from-workspace props) ["workspace" "namespace"])
       ":"
       (get-in (:selected-from-workspace props) ["workspace" "name"])]
      [:div {:style {:padding "0 0 0.5em 1em"}}
       (style/create-form-label "Select Entity Type")
       (style/create-select
         {:style {:width "50%" :minWidth 50 :maxWidth 200} :ref "filter"
          :onChange #(let [value (-> (@refs "filter") .getDOMNode .-value)
                           entities (get-in props [:entity-map value])]
                      (swap! state assoc :entities entities :entity-type value))}
         (keys (:entity-map props)))]
      (let [attribute-keys (apply union (map (fn [e] (set (keys (e "attributes")))) (:entities @state)))]
        [table/Table
         {:key (:entity-type @state)
          :empty-message "There are no entities to display."
          :columns (concat
                     [{:header "Entity Type" :starting-width 100 :sort-by :value}
                      {:header "Entity Name" :starting-width 100 :sort-by :value
                       :content-renderer
                       (fn [row-index entity]
                         [:a {:style {:color (:button-blue style/colors) :textDecoration "none"}
                              :href "javascript:;"
                              :onClick #((:onEntitySelected props) entity)}
                          (entity "name")])}]
                     (map (fn [k] {:header k :starting-width 100 :sort-by :value}) attribute-keys))
          :data (map (fn [m]
                       (concat
                         [(m "entityType")
                          m]
                         (map (fn [k] (get-in m ["attributes" k])) attribute-keys)))
                  (:entities @state))}])])})

(defn- close-modal [state]
  (swap! state dissoc :selected-from-entity))

(defn- label-text [text]
  [:div {:style {:float "left" :width "100px"}} text])

(react/defc ModalContent
  {:render
   (fn [{:keys [state props]}]
     [:div {}
      [comps/Dialog {:width "75%"
                     :blocking? true
                     :dismiss-self #(swap! state dissoc :display-modal?)
                     :content
                     (react/create-element
                       [:div {}
                        [:div {:style {:position "absolute" :right 2 :top 2}}
                         [comps/Button {:icon
                                        :x
                                        :onClick #(close-modal state)}]]
                        [:div {:style {:backgroundColor "#fff"
                                       :padding "20px 48px 18px"
                                       :height "50%"}}
                         [:h3 {} "Copy Entity:"]
                         (label-text "Entity Type: ") (get-in (:selected-from-entity props) ["entityType"])
                         [:br {}]
                         (label-text "Entity Name: ") (get-in (:selected-from-entity props) ["name"])
                         [:br {}]
                         [:div {:style {:float "left" :width "250px" :paddingTop "1em"}}
                          [:div {:style {:fontWeight "bold"}} "From:"]
                          (label-text "Namespace: ")(get-in (:selected-from-workspace props) ["workspace" "namespace"])
                          [:br {}]
                          (label-text "Name: ") (get-in (:selected-from-workspace props) ["workspace" "name"])]
                         [:div {:style {:float "left" :width "25px" :paddingTop "1em"}}
                          [:span {} "\u27A1"]]
                         [:div {:style {:float "left" :width "250px" :paddingTop "1em"}}
                          [:div {:style {:fontWeight "bold"}} "To:"]
                          (label-text "Namespace: ") (:namespace (:workspace-id props))
                          [:br {}]
                          (label-text "Name: ") (:name (:workspace-id props))]]])}]])})

(react/defc Page
  {:did-load-data? ; TODO: Fix this hack. It is necessary for the previous caller to know how to get back to it's original state. Ugh.
   (fn [{:keys [state]}]
     (:entity-map @state))

   :render
   (fn [{:keys [state props]}]
     (cond
       (:selected-from-entity @state) [ModalContent {:workspace-id (:workspace-id @state)
                                                        :selected-from-entity (:selected-from-entity @state)
                                                        :selected-from-workspace (:selected-from-workspace @state)
                                                        }]
       (:entity-map @state) [EntitiesList {:workspace-id (:workspace-id props)
                                           :selected-from-workspace (:selected-from-workspace props)
                                           :entity-map (:entity-map @state)
                                           :onEntitySelected
                                           (fn [entity] (swap! state assoc
                                                          :selected-from-entity entity
                                                          :workspace-id (:workspace-id props)
                                                          :selected-from-workspace (:selected-from-workspace props)))}]
       (:error-message @state) (style/create-server-error-message (:error-message @state))
       :else [:div {:style {:textAlign "center"}} [comps/Spinner {:text "Loading entities..."}]]))

   :component-did-mount
   (fn [{:keys [this]}]
     (react/call :load-entities this))

   :load-entities
   (fn [{:keys [state props]}]
     (let [name (get-in (:selected-from-workspace props) ["workspace" "name"])
           namespace (get-in (:selected-from-workspace props) ["workspace" "namespace"])]
       (endpoints/call-ajax-orch
         {:endpoint (endpoints/get-entities-by-type {:name name :namespace namespace})
          :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                     (if success?
                       (swap! state assoc :entity-map (group-by #(% "entityType") (get-parsed-response)))
                       (swap! state assoc :error status-text)))})))})
