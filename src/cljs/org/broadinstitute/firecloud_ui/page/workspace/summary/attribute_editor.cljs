(ns org.broadinstitute.firecloud-ui.page.workspace.summary.attribute-editor
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- create-section [& children]
  [:div {:style {:padding "1em 0 2em 0"}} children])

(defn view-attributes [state refs]
  [:div {:style {:display "inline-block"}}
   (style/create-section-header "Workspace Attributes")
   (create-section
     (when (or (:saving? @state) (:deleting? @state))
       [comps/Blocker {:banner "Updating..."}])
     [:div {}
      (map-indexed
        (fn [i a]
          [:div {}
           [:div {:style {:float "left" :marginRight "0.5em"}}
            (style/create-text-field
              {:ref (str "key_" i)
               :value (first a)
               :onChange #(swap! state update-in [:attrs-list i]
                           assoc 0 (-> % .-target .-value))
               :disabled (or (not (:editing? @state))
                           (contains? (:reserved-keys @state) i))
               :style (if (or (contains? (:reserved-keys @state) i)
                            (not (:editing? @state)))
                        {:backgroundColor (:background-gray style/colors)}
                        {:backgroundColor "#fff"})})]
           [:div {:style {:float "right"}}
            (style/create-text-field
              {:ref (str "val_" i)
               :value (second a)
               :onChange #(swap! state update-in [:attrs-list i]
                           assoc 1 (-> % .-target .-value))
               :disabled (not (:editing? @state))
               :style (if-not (:editing? @state)
                        {:backgroundColor (:background-gray style/colors)}
                        {:backgroundColor "#fff"})})
            (when (:editing? @state)
              (icons/font-icon
                {:style {:paddingLeft "0.5em" :padding "1em 0.7em"
                         :color "red" :cursor "pointer"}
                 :onClick (fn [e]
                            (when (contains? (:reserved-keys @state) i)
                              ;if it's reserved delete i from the reservation list
                              (swap! state update-in [:reserved-keys] utils/delete i))
                            ;delete the item from the list unconditionally
                            (swap! state update-in [:attrs-list] utils/delete i))}
                :x))]
           (common/clear-both)])
        (:attrs-list @state))
      (when (:editing? @state)
        [comps/Button {:style :add :text "Add new"
                       :onClick (fn [e]
                                  (swap! state update-in [:attrs-list] conj ["" ""])
                                  (js/setTimeout
                                    #(common/focus-and-select
                                       (->> @state :attrs-list count dec (str "key_") (@refs) .getDOMNode))
                                    0))}])])
   (when (and (not (:editing? @state))
              (empty? (:attrs-list @state))
              (not (:saving? @state)))
     (style/create-paragraph [:em {} "There are no attributes to display"]))])

(defn save-attributes [state props this]
  (let
    [orig-keys (mapv first (:orig-attrs @state))
     curr-keys (mapv first (:attrs-list @state))
     curr-vals (mapv second (:attrs-list @state))
     valid-keys? (every? pos? (map count curr-keys))
     valid-vals? (every? pos? (map count curr-vals))
     to-delete (vec (clojure.set/difference
                      (set orig-keys)
                      (set curr-keys)))
     workspace-id (:workspace-id props)
     make-delete-map-fn (fn [k]
                          {:op "RemoveAttribute"
                           :attributeName k})
     make-update-map-fn (fn [p]
                          {:op "AddUpdateAttribute"
                           :attributeName (first p)
                           :addUpdateAttribute (second p)})
     del-mapv (mapv make-delete-map-fn to-delete)
     up-mapv (mapv make-update-map-fn (:attrs-list @state))
     update-orch-fn (fn [add-update-ops]
                      (swap! state assoc :updating-attrs? true)
                      (endpoints/call-ajax-orch
                        {:endpoint (endpoints/update-workspace-attrs
                                     workspace-id)
                         :payload add-update-ops
                         :headers {"Content-Type" "application/json"}
                         :on-done (fn [{:keys [success? xhr]}]
                                    (swap! state dissoc :updating-attrs?)
                                    (if-not success?
                                      (do
                                        (js/alert (str "Exception:\n"
                                                    (.-statusText xhr)))
                                        (swap! state dissoc :orig-attrs)
                                        (react/call :load-workspace this))))}))
     del-orch-fn (fn [del-ops]
                   (swap! state assoc :deleting-attrs? true)
                   (endpoints/call-ajax-orch
                     {:endpoint (endpoints/update-workspace-attrs
                                  workspace-id)
                      :payload del-ops
                      :headers {"Content-Type" "application/json"}
                      :on-done (fn [{:keys [success? xhr]}]
                                 (swap! state dissoc :deleting-attrs?)
                                 (if-not success?
                                   (do
                                     (js/alert (str "Exception:\n"
                                                 (.-statusText xhr)))
                                     (swap! state assoc
                                       :attrs-list (:orig-attrs @state))
                                     (swap! state dissoc :orig-attrs)
                                     (react/call :load-workspace this))
                                   (when-not (empty? up-mapv)
                                     (update-orch-fn  up-mapv))))}))
     uniq-keys? (or (empty? curr-keys) (apply distinct? curr-keys))]
    (cond
      (not valid-keys?) (js/alert "Empty attribute keys are not allowed!")
      (not valid-vals?) (js/alert "Empty attribute values are not allowed!")
      (not uniq-keys?) (js/alert "Unique keys must be used!")
      :else (do
              (if (empty? to-delete)
                (when-not (empty? up-mapv)
                  (update-orch-fn  up-mapv))
                (del-orch-fn del-mapv))
              (swap! state assoc :editing? false)))))

