(ns broadfcui.page.workspace.summary.catalog.options
  (:require
    [dmohs.react :as react]
    [broadfcui.common.components :as comps]
    [broadfcui.common.style :as style]
    [broadfcui.page.workspace.summary.catalog.questions :refer [Questions]]
    ))


(react/defc Options
  {:validate
   (fn [{:keys [state refs]}]
     (if-not (:selected-index @state)
       "Please select an option"
       (react/call :validate (@refs "questions"))))
   :get-attributes
   (fn [{:keys [refs]}]
     (react/call :get-attributes (@refs "questions")))
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [switch]} props
           {:keys [selected-index]} @state]
       (if selected-index
         [:div {}
          (style/create-link {:text "Back" :onClick #(swap! state dissoc :selected-index)})
          [Questions (merge (select-keys props [:library-schema :attributes :required-attributes])
                            (select-keys (get-in switch [:options selected-index]) [:enumerate :questions])
                            {:ref "questions"})]]
         [:div {}
          [:div {} (:title switch)]
          (map-indexed
           (fn [index {:keys [title]}]
             [comps/Button {:text title :onClick #(swap! state assoc :selected-index index)
                            :style {:display "flex" :marginTop "2rem"}}])
           (:options switch))])))})
