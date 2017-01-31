(ns broadfcui.page.workspace.summary.catalog.options
  (:require
    [dmohs.react :as react]
    [broadfcui.common.components :as comps]
    [broadfcui.common.style :as style]
    [broadfcui.page.workspace.summary.catalog.questions :refer [Questions]]
    [broadfcui.utils :as utils]
    ))


(defn- render-option [{:keys [title selected? shown? on-click]}]
  [:div {:style {:display (if shown? "flex" "none") :alignItems "center"
                 :margin "0.5rem 0" :padding "1em"
                 :border style/standard-line :borderRadius 8
                 :backgroundColor (when selected? (:button-primary style/colors))
                 :cursor "pointer"}
         :onClick on-click}
   [:input (merge
            {:type "checkbox" :readOnly true
             :style {:cursor "pointer"}}
            (when selected? {:checked true}))]
   [:div {:style {:marginLeft "0.75rem"
                  :color (when selected? "white")}}
    title]])


(react/defc Options
  {:validate
   (fn [{:keys [state refs]}]
     (if-not (:selected-index @state)
       "Please select an option"
       (react/call :validate (@refs "questions"))))
   :get-attributes
   (fn [{:keys [props state refs]}]
     (let [{:keys [switch]} props
           {:keys [property options]} switch
           {:keys [selected-index]} @state]
       (assoc (react/call :get-attributes (@refs "questions"))
         (keyword property) (get-in options [selected-index :propertyValue]))))
   :get-initial-state
   (fn [{:keys [props]}]
     (utils/cljslog (:attributes props))
     {:selected-index nil})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [switch library-schema]} props
           {:keys [property options]} switch
           {:keys [title]} (get-in library-schema [:properties (keyword property)])
           {:keys [selected-index]} @state]
       [:div {}
        (when-not selected-index
          [:div {} title])
        (map-indexed
         (fn [index {:keys [title]}]
           (let [selected? (= index selected-index)
                 shown? (or selected? (not selected-index))]
             (render-option {:title title :selected? selected? :shown? shown?
                             :on-click #(if selected?
                                          (swap! state dissoc :selected-index)
                                          (swap! state assoc :selected-index index))})))
         options)
        (when selected-index
          [Questions (merge (select-keys props [:library-schema :attributes :required-attributes])
                            (select-keys (get options selected-index) [:enumerate :questions])
                            {:ref "questions"})])]))})
