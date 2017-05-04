(ns broadfcui.common.modal
  (:require
    [dmohs.react :as react]
    [broadfcui.common :as common]
    [broadfcui.common.icons :as icons]
    [broadfcui.common.style :as style]
    [broadfcui.utils :as utils]
    ))


(defonce ^:private instance (atom nil))

(defn set-instance! [x]
  (reset! instance x))

(defn push-modal [child]
  ;; Forces create-element so the caller can refer to refs in the dialog.
  (react/call :push-modal @instance (react/create-element child)))

(defn pop-modal []
  (react/call :pop-modal @instance))


(react/defc Component
  {:push-modal
   (fn [{:keys [state]} child]
     (swap! state update :stack conj child))
   :pop-modal
   (fn [{:keys [state]}]
     (when-not (empty? (:stack @state))
       (swap! state update :stack pop)))
   :get-initial-state
   (fn []
     {:stack []})
   :render
   (fn [{:keys [state]}]
     [:div {}
      (let [{:keys [stack]} @state]
        (map (fn [child]
               ;; IGV uses zIndex values up to 512; make sure that modals appear on top
               [:div {:style {:position "absolute" :zIndex 513 :overflow "auto"
                              :top 0 :bottom 0 :left 0 :right 0
                              :backgroundColor "rgba(110,110,110,0.4)"}}
                [:div {:style {:position "absolute" :zIndex 513
                               :top (+ (aget js/document "body" "scrollTop") 30)
                               :left 0
                               :width "100%"}}
                 [:div {:style {:display "flex" :justifyContent "center" :paddingBottom "2rem"}}
                  [:div {:style {:backgroundColor "white" :maxWidth "95%" :minWidth 500}}
                   child]]]])
             stack))])
   :component-did-mount
   (fn [{:keys [locals]}]
     (swap! locals assoc :keydown-handler (common/create-key-handler [:esc] pop-modal))
     (.addEventListener js/window "keydown" (:keydown-handler @locals)))
   :component-did-update
   (fn [{:keys [state]}]
     (if (empty? (:stack @state))
       (.remove (->> js/document .-body .-classList) "modal-open")
       (.add (->> js/document .-body .-classList) "modal-open")))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (.removeEventListener js/window "keydown" (:keydown-handler @locals)))})
