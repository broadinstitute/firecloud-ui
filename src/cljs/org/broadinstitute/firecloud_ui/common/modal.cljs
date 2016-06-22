(ns org.broadinstitute.firecloud-ui.common.modal
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.utils :as u]
    ))


(def ^:private instance)


(defn set-instance! [x]
  (set! instance x))


(defn push-modal [child]
  (react/call :push-modal instance child))


(defn pop-modal []
  (react/call :pop-modal instance))


(react/defc Component
  {:push-modal
   (fn [{:keys [state]} child]
     (swap! state update-in [:stack] conj child))
   :pop-modal
   (fn [{:keys [state]}]
     (swap! state update-in [:stack] pop))
   :get-initial-state
   (fn []
     {:stack (list)})
   :render
   (fn [{:keys [state]}]
     [:div {}
      (let [{:keys [stack]} @state]
        (map-indexed (fn [i child]
                       [:div {}
                        [:div {:style {:position "fixed"
                                       :top 0 :bottom 0 :left 0 :right 0
                                       :backgroundColor "rgba(110,110,110,0.4)"}}]
                        [:div {:style {:position "absolute"
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
