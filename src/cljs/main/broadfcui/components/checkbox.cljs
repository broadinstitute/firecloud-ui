(ns broadfcui.components.checkbox
  (:require
   [dmohs.react :as react]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   ))


(react/defc Checkbox
  {:checked?
   (fn [{:keys [state]}]
     (:checked? @state))
   :get-default-props
   (fn []
     {:initial-checked? false
      :disabled? false})
   :get-initial-state
   (fn [{:keys [props]}]
     {:checked? (:initial-checked? props)})
   :component-will-mount
   (fn [{:keys [locals]}]
     (swap! locals assoc :id (gensym "checkbox-")))
   :render
   (fn [{:keys [props state locals]}]
     (let [{:keys [data-test-id label disabled? on-change style]} props
           {:keys [checked?]} @state
           {:keys [id]} @locals]
       [:div {:data-test-id data-test-id
              :className "custom-checkbox"
              :style (merge {:color ((if disabled? :text-lightest :text-light) style/colors)} style)}
        [:input {:id id :type "checkbox"
                 :style {:cursor (if disabled? "not-allowed" "pointer")}
                 :checked checked? :disabled disabled?
                 :onChange #(let [new-value (not checked?)]
                              (when on-change (on-change new-value))
                              (swap! state assoc :checked? new-value))}]
        [:label {:htmlFor id
                 :style {:cursor (if disabled? "not-allowed" "pointer")}}
         label]]))})
