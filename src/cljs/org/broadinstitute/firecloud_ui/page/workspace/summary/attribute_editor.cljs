(ns org.broadinstitute.firecloud-ui.page.workspace.summary.attribute-editor
  (:require
    clojure.set
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
  [:div {}
   (style/create-section-header "Workspace Attributes")
   (create-section
     (when (or (:saving? @state) (:deleting? @state))
       [comps/Blocker {:banner "Updating..."}])
     [:div {}
      (map-indexed
        (fn [i [attr-key attr-value]]
          [:div {:style {:display "flex" :alignItems "baseline"}}
           [:div {:style {:flex "35 0 auto" :marginRight "0.5em"}}
            (style/create-text-field
              {:ref (str "key_" i)
               :value attr-key
               :onChange #(swap! state update-in [:attrs-list i]
                           assoc 0 (-> % .-target .-value))
               :disabled (or (not (:editing? @state))
                           (contains? (:reserved-keys @state) i))
               :style (merge
                        (if (or (contains? (:reserved-keys @state) i)
                                (not (:editing? @state)))
                          {:backgroundColor (:background-gray style/colors)}
                          {:backgroundColor "#fff"})
                        {:width "100%"})})]
           [:div {:style {:flex "65 0 auto"}}
            (style/create-text-field
              {:ref (str "val_" i)
               :value attr-value
               :onChange #(swap! state update-in [:attrs-list i]
                           assoc 1 (-> % .-target .-value))
               :disabled (not (:editing? @state))
               :style (merge
                        (if-not (:editing? @state)
                          {:backgroundColor (:background-gray style/colors)}
                          {:backgroundColor "#fff"})
                        {:width "100%"})})]
           (when (:editing? @state)
             (icons/font-icon
               {:style {:color "red" :cursor "pointer" :margin "0 0.5em"}
                :onClick (fn [e]
                           (when (contains? (:reserved-keys @state) i)
                             ;if it's reserved delete i from the reservation list
                             (swap! state update-in [:reserved-keys] utils/delete i))
                           ;delete the item from the list unconditionally
                           (swap! state update-in [:attrs-list] utils/delete i))}
               :x))
           (common/clear-both)])
        (:attrs-list @state))
      (when (:editing? @state)
        [comps/Button {:style :add :text "Add new"
                       :onClick (fn [_]
                                  (swap! state update-in [:attrs-list] conj ["" ""])
                                  (js/setTimeout
                                    #(common/focus-and-select
                                       (->> @state :attrs-list count dec (str "key_") (@refs)))
                                    0))}])
      (when (and (not (:editing? @state))
              (empty? (:attrs-list @state))
              (not (:saving? @state)))
        (style/create-paragraph [:em {} "There are no attributes to display"]))])])

(defn save-attributes [state props this description]
  (let
    [orig-keys (mapv first (:orig-attrs @state))
     curr-keys (mapv first (:attrs-list @state))
     curr-vals (mapv second (:attrs-list @state))
     del-mapv (mapv (fn [k] {:op "RemoveAttribute" :attributeName k})
                (clojure.set/difference (set orig-keys) (set curr-keys)))
     up-mapv (mapv (fn [[name attr]] {:op "AddUpdateAttribute" :attributeName name :addUpdateAttribute attr})
               (conj (:attrs-list @state) ["description" description]))
     update-orch-fn (fn [add-update-ops]
                      (swap! state assoc :updating-attrs? true)
                      (endpoints/call-ajax-orch
                        {:endpoint (endpoints/update-workspace-attrs (:workspace-id props))
                         :payload add-update-ops
                         :headers {"Content-Type" "application/json"}
                         :on-done (fn [{:keys [success? xhr]}]
                                    (swap! state dissoc :updating-attrs?)
                                    (when-not success?
                                      (js/alert (str "Exception:\n" (.-statusText xhr)))
                                      (swap! state dissoc :orig-attrs)
                                      (react/call :load-workspace this)))}))
     del-orch-fn (fn [del-ops]
                   (swap! state assoc :deleting-attrs? true)
                   (endpoints/call-ajax-orch
                     {:endpoint (endpoints/update-workspace-attrs (:workspace-id props))
                      :payload del-ops
                      :headers {"Content-Type" "application/json"}
                      :on-done (fn [{:keys [success? xhr]}]
                                 (swap! state dissoc :deleting-attrs?)
                                 (if-not success?
                                   (do
                                     (js/alert (str "Exception:\n" (.-statusText xhr)))
                                     (swap! state assoc :attrs-list (:orig-attrs @state))
                                     (swap! state dissoc :orig-attrs)
                                     (react/call :load-workspace this))
                                   (when-not (empty? up-mapv)
                                     (update-orch-fn up-mapv))))}))]
    (cond
      (some empty? curr-keys) (js/alert "Empty attribute keys are not allowed!")
      (some empty? curr-vals) (js/alert "Empty attribute values are not allowed!")
      (not (or (empty? curr-keys) (apply distinct? curr-keys))) (js/alert "Unique keys must be used!")
      :else (do
              (if (empty? del-mapv)
                (when-not (empty? up-mapv)
                  (update-orch-fn up-mapv))
                (del-orch-fn del-mapv))
              (swap! state update-in [:server-response :workspace "workspace" "attributes"] assoc "description" description)
              (swap! state assoc :editing? false)))))

