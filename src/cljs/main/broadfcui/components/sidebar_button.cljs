(ns broadfcui.components.sidebar-button
  (:require
   [dmohs.react :as react]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.components.modals :as modals]
   ))


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
          (let [on-dismiss #(swap! state dissoc :show-message?)]
            (cond (true? disabled?) (modals/render-message {:header "Disabled" :text "This action is disabled" :on-dismiss on-dismiss})
                  (string? disabled?) (modals/render-message {:header "Disabled" :text disabled? :on-dismiss on-dismiss})
                  (map? disabled?) ((if (= (:type disabled?) :error) modals/render-error modals/render-message)
                                    (assoc disabled? :on-dismiss on-dismiss)))))
        (icons/icon {:style {:padding "0 20px" :borderRight style/standard-line} :className "fa-fw"} icon)
        [:div {:style {:textAlign "center" :margin "auto"}}
         text]]))})
