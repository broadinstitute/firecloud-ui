(ns broadfcui.common.modal
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.utils :as utils]
   ))


(defonce ^:private instance (atom nil))

(defn set-instance! [x]
  (reset! instance x))

;; Deprecated. Use broadfcui.components.modals instead.
(defn push-modal [child]
  ;; Forces create-element so the caller can refer to refs in the dialog.
  (@instance :push-modal (react/create-element child)))

;; Deprecated. Use broadfcui.components.modals instead.
(defn pop-modal []
  (@instance :pop-modal))


(react/defc Component
  {:push-modal
   (fn [{:keys [state]} child]
     (swap! state update :stack conj child)
     (.add (->> js/document .-body .-classList) "modal-open"))
   :pop-modal
   (fn [{:keys [state after-update]}]
     (when-not (empty? (:stack @state))
       (swap! state update :stack pop))
     (after-update
      (fn []
        (when (empty? (:stack @state))
          (.remove (->> js/document .-body .-classList) "modal-open")))))
   :get-initial-state
   (fn []
     {:stack []})
   :render
   (fn [{:keys [state]}]
     [:div {}
      (let [{:keys [stack]} @state]
        (map (fn [child]
               ;; IGV uses zIndex values up to 512; make sure that modals appear on top
               [:div {:style {:position "fixed" :zIndex 513 :overflow "auto"
                              :top 0 :bottom 0 :left 0 :right 0
                              :backgroundColor "rgba(110,110,110,0.4)"}}
                [:div {:style {:padding "2rem 0"
                               :display "flex" :justifyContent "center" :alignItems "flex-start"}}
                 [:div {:style {:backgroundColor "white" :maxWidth "95%" :minWidth 500}}
                  child]]])
             stack))])
   :component-did-mount
   (fn [{:keys [state locals]}]
     (swap! locals assoc
            :keydown-handler (common/create-key-handler [:esc] pop-modal)
            :resize-handler #(swap! state identity))
     (.addEventListener js/window "keydown" (:keydown-handler @locals))
     ;; re-evaluate the height of the window to resize absolute container
     (.addEventListener js/window "resize" (:resize-handler @locals)))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (.removeEventListener js/window "keydown" (:keydown-handler @locals))
     (.removeEventListener js/window "resize" (:resize-handler @locals)))})
