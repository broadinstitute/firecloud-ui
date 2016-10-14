(ns org.broadinstitute.firecloud-ui.common.modal
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.utils :as u]
    ))


(defonce ^:private instance (atom nil))


(defn set-instance! [x]
  (reset! instance x))

(defn push-modal [child]
  ;; Forces create-element so the caller can refer to refs in the dialog.
  (react/call :push-modal @instance (react/create-element child)))

(declare OKCancelForm)
(defn push-ok-cancel-modal [props]
  (react/call :push-modal @instance (react/create-element OKCancelForm props)))


(defn push-message [{:keys [header message]}]
  (push-ok-cancel-modal
    {:header (or header "Message")
     :content [:div {:style {:maxWidth 500}} message]
     :show-cancel? false :ok-button "OK"}))

(defn- push-error [content]
  (push-ok-cancel-modal
    {:header [:div {:style {:display "flex" :alignItems "center"}}
              (icons/icon {:style {:color (:exception-red style/colors)
                                   :marginRight "0.5em"}} :error)
              "Error"]
     :content [:div {:style {:maxWidth "50vw"}} content]
     :show-cancel? false :ok-button "OK"}))

(defn push-error-text [error-text]
  (push-error error-text))

(defn push-error-response [error-response]
  (push-error [comps/ErrorViewer {:error error-response}]))

(defn push-confirm [{:keys [header text on-confirm]}]
  (push-ok-cancel-modal
    {:header (or header "Confirm")
     :content [:div {:style {:maxWidth 500}} text]
     :ok-button on-confirm}))

(defn pop-modal []
  (react/call :pop-modal @instance))


(react/defc Component
  {:push-modal
   (fn [{:keys [state]} child]
     (swap! state update-in [:stack] conj child))
   :pop-modal
   (fn [{:keys [state]}]
     (when-not (empty? (:stack @state))
       (swap! state update-in [:stack] pop)))
   :get-initial-state
   (fn []
     {:stack []})
   :render
   (fn [{:keys [state]}]
     [:div {}
      (let [{:keys [stack]} @state]
        (map (fn [child]
               [:div {}
                ; IGV uses zIndex values up to 512; make sure that modals appear on top
                [:div {:style {:position "fixed" :zIndex 513
                               :top 0 :bottom 0 :left 0 :right 0
                               :backgroundColor "rgba(110,110,110,0.4)"}}]
                [:div {:style {:position "absolute" :zIndex 513
                               :top (+ (aget js/document "body" "scrollTop") 30)
                               :left 0
                               :width "100%"}}
                 [:div {:style {:display "flex" :justifyContent "center"}}
                  [:div {:style {:backgroundColor "white"}}
                   child]]]])
             stack))])
   :component-did-mount
   (fn [{:keys [locals]}]
     (swap! locals assoc :keydown-handler (common/create-key-handler [:esc] pop-modal))
     (.addEventListener js/window "keydown" (:keydown-handler @locals)))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (.removeEventListener js/window "keydown" (:keydown-handler @locals)))})


(react/defc OKCancelForm
  {:get-default-props
   (fn []
     {:show-cancel? true})
   :render
   (fn [{:keys [props]}]
     (let [{:keys [header content ok-button show-cancel? cancel-text]} props
           cancel-text (or cancel-text "Cancel")]
       [:div {}
        [:div {:style {:borderBottom style/standard-line
                       :padding "20px 48px 18px"
                       :fontSize "137%" :fontWeight 400 :lineHeight 1}}
         header]
        [:div {:style {:padding "22px 48px 40px" :backgroundColor (:background-gray style/colors)}}
         content
         (when (or show-cancel? ok-button)
           [:div {:style {:marginTop 40 :textAlign "center"}}
            (when show-cancel?
              [:a {:className "cancel"
                   :style {:marginRight (when ok-button 27) :marginTop 2 :padding "0.5em"
                           :display "inline-block"
                           :fontSize "106%" :fontWeight 500 :textDecoration "none"
                           :color (:button-blue style/colors)}
                   :href "javascript:;"
                   :onClick pop-modal
                   :onKeyDown (common/create-key-handler [:space :enter] pop-modal)}
               cancel-text])
            (cond (string? ok-button) [comps/Button {:text ok-button :ref "ok-button" :class-name "ok-button" :onClick pop-modal}]
                  (fn? ok-button) [comps/Button {:text "OK" :ref "ok-button" :class-name "ok-button" :onClick ok-button}]
                  (map? ok-button) [comps/Button (merge {:ref "ok-button" :class-name "ok-button"} ok-button)]
                  :else ok-button)])]]))
   :component-did-mount
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
