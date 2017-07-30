(ns broadfcui.components.modals
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.uicomps.modal :as modal]
   [broadfcui.common :as common]
   [broadfcui.common.components :as comps]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))

(react/defc OKCancelForm
  {:get-default-props
   (fn []
     {:cancel-text "Cancel"
      :show-cancel? true
      :show-close? true})
   :render
   (fn [{:keys [this props]}]
     (let [{:keys [header content dismiss ok-button show-cancel? cancel-text show-close? data-test-id]} props]
       (modal/render
        {:content
         [:div {}
          [:div (merge {:style {:borderBottom style/standard-line
                         :padding "1rem 3rem 1rem"
                         :fontSize "140%" :fontWeight 400 :lineHeight 1}}
                       (when (some? data-test-id) {:data-test-id data-test-id}))
           header
           (when show-close? [comps/XButton {:dismiss dismiss}])]
          [:div {:style {:padding "2rem 3rem"
                         :backgroundColor (:background-light style/colors)}}
           content
           (when (or show-cancel? ok-button)
             [:div {:style {:marginTop (if ok-button "2rem" "1rem") :textAlign "center"}}
              (when show-cancel?
                [:a {:className "cancel"
                     :style {:marginRight (when ok-button "2rem") :marginTop 2
                             :display "inline-block"
                             :fontWeight 500 :textDecoration "none"
                             :color (:button-primary style/colors)}
                     :href "javascript:;"
                     :data-test-id "cancel-button"
                     :onClick dismiss
                     :onKeyDown (common/create-key-handler [:space :enter] dismiss)}
                 cancel-text])
              (when ok-button
                (cond
                  (string? ok-button)
                  [comps/Button {:text ok-button :ref "ok-button" :data-test-id "ok-button" :onClick dismiss}]
                  (fn? ok-button) [comps/Button {:text "OK" :ref "ok-button" :data-test-id "ok-button" :onClick ok-button}]
                  (map? ok-button) [comps/Button (merge {:ref "ok-button" :data-test-id "ok-button"} ok-button)]
                  :else ok-button))])]]
         :did-mount #(this :-modal-did-mount)
         :dismiss dismiss})))
   :-modal-did-mount
   (fn [{:keys [props refs]}]
     (when-let [get-first (:get-first-element-dom-node props)]
       (common/focus-and-select (get-first))
       (when-let [get-last (or (:get-last-element-dom-node props)
                               #(react/find-dom-node (@refs "ok-button")))]
         (.addEventListener
          (get-first) "keydown"
          (common/create-key-handler [:tab] #(.-shiftKey %)
                                     (fn [e] (.preventDefault e)
                                       (when (:cycle-focus? props)
                                         (.focus (get-last))))))
         (.addEventListener
          (get-last)
          "keydown"
          (common/create-key-handler [:tab] #(not (.-shiftKey %))
                                     (fn [e] (.preventDefault e)
                                       (when (:cycle-focus? props)
                                         (.focus (get-first)))))))))})

(defn render-error [{:keys [header text on-dismiss]}]
  [OKCancelForm
   {:header [:div {:style {:display "inline-flex" :align-items "center"}}
             (icons/icon {:style {:color (:exception-state style/colors)
                                  :marginRight "0.5rem"}} :error)
             (or header "Error")]
    :content [:div {:style {:width 500}} text]
    :dismiss on-dismiss
    :show-cancel? false :ok-button "OK"}])

(defn render-message [{:keys [header text on-confirm on-dismiss]}]
  [OKCancelForm
   {:header (or header "Confirm")
    :content [:div {:style {:width 500}} text]
    :ok-button on-confirm
    :dismiss on-dismiss}])
