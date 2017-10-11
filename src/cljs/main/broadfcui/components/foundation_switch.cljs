(ns broadfcui.components.foundation-switch)


(defn render-foundation-switch [{:keys [checked? on-change size]}]
  (let [id (gensym "switch-")]
    [:div {:className (str "switch " (or size "tiny")) :style {:marginBottom 0}}
     [:input {:className "switch-input" :type "checkbox"
              :id id
              :checked checked?
              :onChange #(on-change (aget % "target" "checked"))}]
     [:label {:className "switch-paddle"
              :htmlFor id}]]))
