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
            (icons/font-icon
              {:style {:paddingLeft "0.5em" :padding "1em 0.7em"
                       :color "red" :cursor "pointer"
                       :display (when-not (:editing? @state) "none")}
               :onClick (fn [e]
                          (when (contains? (:reserved-keys @state) i)
                            ;if it's reserved delete i from the reservation list
                            (swap! state update-in [:reserved-keys] utils/delete i))
                          ;delete the item from the list unconditionally
                          (swap! state update-in [:attrs-list] utils/delete i))}
              :x)]
           (common/clear-both)])
        (:attrs-list @state))
      [:div {:style {:display (when-not (:editing? @state) "none")}}
       [comps/Button {:style :add :text "Add new"
                      :onClick #(do
                                 (swap! state update-in [:attrs-list] conj ["" ""])
                                 (js/setTimeout
                                   (fn []
                                     (common/focus-and-select
                                       (-> (@refs (str "key_"
                                                    (dec (count
                                                           (:attrs-list @state))))).getDOMNode)))0))}]]])
   [:div {:style {:display
                  (when (or (or (:editing? @state)
                              (not-empty (:attrs-list @state))) (:saving? @state)) "none")}}
    (style/create-paragraph [:em {} "There are no attributes to display"])]])

