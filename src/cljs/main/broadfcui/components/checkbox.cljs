(ns broadfcui.components.checkbox
  (:require
   [dmohs.react :as react]
   [broadfcui.common.style :as style]
   ))


(react/defc Checkbox
  {:checked?
   (fn [{:keys [state this]}]
     (assert (not (this :-controlled?)) "You're asking if a controlled checkbox is checked -- you know this.")
     (:checked? @state))
   :get-default-props
   (fn []
     {:initial-checked? false
      :disabled? false})
   :get-initial-state
   (fn [{:keys [props this]}]
     (if (this :-controlled?)
       {}
       {:checked? (boolean (:initial-checked? props))}))
   :component-will-mount
   (fn [{:keys [locals]}]
     (swap! locals assoc :id (gensym "checkbox-")))
   :render
   (fn [{:keys [props state locals this]}]
     (let [{:keys [data-test-id label disabled? on-change style]} props
           checked? (:checked? (if (this :-controlled?) props @state))
           {:keys [id]} @locals]
       [:div {:style (merge {:color ((if disabled? :text-lightest :text-light) style/colors)} style)}
        [:input {:data-test-id data-test-id
                 :id id :type "checkbox"
                 :style {:cursor (if disabled? "not-allowed" "pointer")}
                 :checked checked? :disabled disabled?
                 :onChange #(let [new-value (not checked?)]
                              (when on-change (on-change new-value))
                              (when-not (this :-controlled?)
                                (swap! state assoc :checked? new-value)))}]
        [:label {:htmlFor id
                 :style {:paddingLeft "0.5rem"
                         :cursor (if disabled? "not-allowed" "pointer")}}
         label]]))
   :-controlled?
   (fn [{:keys [props]}]
     (contains? props :checked?))})
