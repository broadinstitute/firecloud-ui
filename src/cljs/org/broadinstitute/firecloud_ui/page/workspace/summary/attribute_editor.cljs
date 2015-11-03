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

(defn- reload-attributes [props state on-success]
  (endpoints/call-ajax-orch
    {:endpoint (endpoints/get-workspace (:workspace-id props))
     :on-done  (fn [{:keys [success? get-parsed-response status-text]}]
                 (if success?
                   (let [response (get-parsed-response)
                         ws (response "workspace")
                         attrs-list (mapv (fn [[k v]] [k v]) (ws "attributes"))]
                     (swap! state assoc :server-response
                       {:attrs-list attrs-list} :loaded-attrs? true :saving? true)
                     (on-success))
                   (swap! state assoc :server-response {:error-message status-text})))}))

(defn- add-update-attributes [props state attrs on-success]
  (let [workspace-id (:workspace-id props)
        add-update-ops (for [pair attrs]
                         {:op "AddUpdateAttribute"
                          :attributeName (key pair)
                          :addUpdateAttribute (val pair)})]
    (endpoints/call-ajax-orch
      {:endpoint (endpoints/update-workspace-attrs workspace-id)
       :payload add-update-ops
       :headers {"Content-Type" "application/json"}
       :on-done (fn [{:keys [success? xhr]}]
                  (if-not success?
                    (do (swap! state assoc :saving? false)
                        (js/alert (str "Exception:\n" (.-statusText xhr))))
                    (on-success)))})))

(defn- delete-attribute [props attr on-success]
  (let [workspace-id (:workspace-id props)
        delete-op [{:op "RemoveAttribute"
                    :attributeName attr}]]
    (endpoints/call-ajax-orch
      {:endpoint (endpoints/update-workspace-attrs workspace-id)
       :payload delete-op
       :headers {"Content-Type" "application/json"}
       :on-done (fn [{:keys [success? xhr]}]
                  (if-not success?
                    (js/alert (str "Exception:\n" (.-statusText xhr)))
                    (on-success)))})))


(defn render-attributes [props state refs]
  [:div {}
   [:div {:style {:display "inline-block"}}
    [:div {:style {:display "inline-block"}}
     (create-section
       (when (or (:saving? @state) (:deleting? @state))
         [comps/Blocker {:banner "Updating..."}])
       [:div {}
        (map-indexed
          (fn [i a]
            [:div {}
             [:div {:style {:float "left" :marginRight "0.5em"}}
              (style/create-text-field
                {:ref (str "field" i)
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
                {:value (second a)
                 :onChange #(swap! state update-in [:attrs-list i]
                             assoc 1 (-> % .-target .-value))
                 :disabled (not (:editing? @state))
                 :style (if-not (:editing? @state)
                          {:backgroundColor (:background-gray style/colors)}
                          {:backgroundColor "#fff"})})
              (icons/font-icon
                {:style {:paddingLeft "0.5em" :padding "1em 0.7em"
                         :color "red" :cursor "pointer"
                         :display (when-not (:editing? @state) "none")}
                 :onClick (fn [e]
                            (if (contains? (:reserved-keys @state) i)
                              (do
                                (swap! state assoc :deleting? true)
                                (delete-attribute props (first a)
                                  (fn [e] (swap! state #(-> %
                                                         (assoc :deleting? false)
                                                         (update-in [:reserved-keys] utils/delete i)
                                                         (update-in [:attrs-list] utils/delete i))))))
                              (swap! state update-in [:attrs-list] utils/delete i)))}
                :x)]
             (common/clear-both)])
          (:attrs-list @state))
        [:div {:style {:display (when-not (:editing? @state) "none")}}
         [comps/Button {:style :add :text "Add new"
                        :onClick
                        #(do
                           (swap! state update-in [:attrs-list] conj ["" ""])
                           (js/setTimeout
                             (fn [] (common/focus-and-select
                                      (-> (@refs (str "field" (dec (count (:attrs-list @state))))) .getDOMNode)))
                             0))}]]])
     [:div {:style {:display
                    (when (or (or (:editing? @state)
                                (not-empty (:attrs-list @state))) (:saving? @state)) "none")}}
      (style/create-paragraph [:em {} "There are no attributes to display"])]]]
   (common/clear-both)])
