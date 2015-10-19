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


(react/defc AttributeViewer
  {:get-initial-state
   (fn [{:keys [props]}]
     {:editing? false :attrs-list (:attrs-list props)})
   :render
   (fn [{:keys [this props state]}]
     [:div {:style {:margin "45px 25px"}}
      (react/call :render-sidebar this)
      [:div {:style {:display "inline-block"}}
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
                   {:value (first a)
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
           ;TODO: new textfields should gain typing focus when adding new rows
           [:div {:style {:display (when-not (:editing? @state) "none")}}
            [comps/Button {:style :add :text "Add new"
                           :onClick #(swap! state update-in [:attrs-list] conj ["" ""])}]]])
        [:div {:style {:display
                       (when (or (or (:editing? @state)
                                   (not-empty (:attrs-list @state))) (:saving? @state)) "none")}}
         (style/create-paragraph [:em {} "There are no attributes to display"])]]]
      (common/clear-both)])
   :render-sidebar
   (fn [{:keys [props state]}]
     [:div {:style {:float "left" :width 290 :marginRight 40}}
      (if-not (:editing? @state)
        [:div {}
         [comps/SidebarButton
          {:style :light :margin :bottom :color :button-blue
           :text "View summary" :icon :document
           :onClick (:on-done props)}]
         (when (:writer? props)
           [comps/SidebarButton
            {:style :light :color :button-blue
             :text "Edit attributes" :icon :pencil
             :onClick #(swap! state assoc
                        :editing? true
                        :reserved-keys (vec (range 0 (count (:attrs-list @state)))))}])]
        [:div {:style {:fontSize "106%" :lineHeight 1 :textAlign "center"}}
         [comps/SidebarButton
          {:color :success-green
           :text "Done" :icon :status-done
           :onClick (fn [e]
                      (swap! state assoc
                        :attrs-list (filterv
                                      (fn [pair] (not (clojure.string/blank? (clojure.string/trim (first pair)))))
                                      (:attrs-list @state))
                        :saving? true)
                      ;; TODO: rawls will soon return attributes after update- use that intead of reload
                      (add-update-attributes
                        props state (:attrs-list @state)
                        (fn [e] (reload-attributes props state
                                  #(swap! state assoc :editing? false :saving? false)))))}]])])})
