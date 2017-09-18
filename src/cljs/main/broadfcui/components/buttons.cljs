(ns broadfcui.components.buttons
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   ))
;; Can't import broadfcui.components.modals, because that creates a circular dependency.
;; Just fully qualify modals to solve this


(defn- show-message [disabled? on-dismiss]
  (cond (true? disabled?) (broadfcui.components.modals/render-message {:header "Disabled" :text "This action is disabled" :on-dismiss on-dismiss})
        (string? disabled?) (broadfcui.components.modals/render-message {:header "Disabled" :text disabled? :on-dismiss on-dismiss})
        (map? disabled?) ((if (= (:type disabled?) :error) broadfcui.components.modals/render-error broadfcui.components.modals/render-message)
                          (assoc disabled? :on-dismiss on-dismiss))))


(react/defc Button
  {:get-default-props
   (fn []
     {:type :primary
      :color (:button-primary style/colors)})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [type color icon href disabled? onClick text style class-name data-test-id]} props
           color (if disabled? (:disabled-state style/colors) color)]
       [:a {:className (or class-name "button")
            :style (merge
                    (case type
                      :primary {:backgroundColor color :color "white"}
                      :secondary {:backgroundColor "white" :color color
                                  :border (str "1px solid " color)})
                    {:display "inline-flex" :alignItems "center" :justifyContent "center"
                     :flexShrink 0
                     :cursor (when disabled? "default")
                     :fontWeight 500
                     :minHeight 19 :minWidth 19
                     :borderRadius 2 :padding (if text "0.7em 1em" "0.4em")
                     :textDecoration "none"}
                    (if (map? style) style {}))
            :data-test-id data-test-id
            :href (or href "javascript:;")
            :onClick (if disabled? #(swap! state assoc :show-message? true) onClick)
            :onKeyDown (when (and onClick (not disabled?))
                         (common/create-key-handler [:space] onClick))}
        (when (:show-message? @state)
          (show-message disabled? #(swap! state dissoc :show-message?)))
        text
        (some->> icon (icons/icon {:style (if text
                                            {:fontSize 20 :margin "-0.5em -0.3em -0.5em 0.5em"}
                                            {:fontSize 18})}))]))})


(react/defc SidebarButton
  {:get-default-props
   (fn []
     {:style :heavy})
   :render
   (fn [{:keys [props state]}]
     (let [{:keys [text icon onClick style disabled? margin color data-test-id]} props
           heavy? (= :heavy style)
           color (if (keyword? color) (get style/colors color) color)]
       [:div {:style {:display "flex" :flexWrap "nowrap" :alignItems "center"
                      :marginTop (when (= margin :top) "1rem")
                      :marginBottom (when (= margin :bottom) "1rem")
                      :border (when-not heavy? style/standard-line)
                      :borderRadius 5
                      :padding "0.75rem 0"
                      :cursor (if disabled? "default" "pointer")
                      :backgroundColor (if disabled? (:disabled-state style/colors) (if heavy? color "transparent"))
                      :color (if heavy? "#fff" color)
                      :fontSize "106%"}
              :data-test-id data-test-id
              :data-test-state (if disabled? "disabled" "enabled")
              :onClick (if disabled?
                         #(swap! state assoc :show-message? true)
                         onClick)}
        (when (:show-message? @state)
          (show-message disabled? #(swap! state dissoc :show-message?)))
        (icons/icon {:style {:padding "0 20px" :borderRight style/standard-line} :className "fa-fw"} icon)
        [:div {:style {:textAlign "center" :margin "auto"}}
         text]]))})


(react/defc XButton
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:float "right" :marginRight "-28px" :marginTop "-1px"}}
      [:a {:style {:color (:text-light style/colors)}
           :href "javascript:;"
           :onClick (:dismiss props)
           :id (:id props) :data-test-id "x-button"}
       (icons/icon {:style {:fontSize "80%"}} :close)]])})
