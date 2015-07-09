(ns org.broadinstitute.firecloud-ui.common
  (:require
   [dmohs.react :as react]
   [clojure.string]
   [org.broadinstitute.firecloud-ui.utils :as utils :refer [rlog jslog cljslog]]
   ))


(def colors {:link-blue "#6690c5"
             :line-gray "#e6e6e6"
             :button-blue "#457fd2"
             :background-gray "#f4f4f4"
             :text-light "#7f7f7f"
             :text-gray "#666"})


(def input-text-style
  {:backgroundColor "#fff"
   :border "1px solid #cacaca" :borderRadius 3
   :boxShadow "0px 1px 3px rgba(0,0,0,0.06)" :boxSizing "border-box"
   :fontSize "88%"
   :marginBottom "0.75em" :padding "0.5em"})


(react/defc Spinner
  {:render
   (fn [{:keys [props]}]
     [:span {:style {:margin "1em"}}
      [:img {:src "assets/spinner.gif"
             :style {:height "1.5em" :verticalAlign "middle" :marginRight "1ex"}}]
      (:text props)])})


(react/defc Button
  {:get-default-props
   (fn []
     {:color (:button-blue colors)})
   :render
   (fn [{:keys [props]}]
     [:a {:style {:display "inline-block"
                  :backgroundColor (:color props)
                  :color "white" :fontWeight 500
                  :borderRadius 2 :padding "0.7em 1em"
                  :textDecoration "none"}
          :href "javascript:;"
          :onClick (fn [e] ((:onClick props) e))
          :onKeyDown (fn [e]
                       (let [k (.-keyCode e)]
                         (when (or (= 32 k) (= 13 k)) ((:onClick props) e))))}
      (:text props)
      (when (= (:style props) :add)
        [:span {:style {:display "inline-block" :height "1em" :width "1em" :marginLeft "1em"
                        :position "relative"}}
         [:span {:style {:position "absolute" :top "-50%" :fontSize "200%" :fontWeight "normal"}}
          "+"]])])})


(react/defc TabBar
  (let [Tab (react/create-class
             {:get-initial-state
              (fn []
                {:hovering? false})
              :render
              (fn [{:keys [props state]}]
                [:div {:style {:float "left" :padding "1em 2em"
                               :borderLeft (when (zero? (:index props))
                                             (str "1px solid " (:line-gray colors)))
                               :borderRight (str "1px solid " (:line-gray colors))
                               :backgroundColor (when (:active? props) "white")
                               :cursor "pointer"
                               :position "relative"}
                       :onMouseOver (fn [e] (swap! state assoc :hovering? true))
                       :onMouseOut (fn [e] (swap! state assoc :hovering? false))}
                 (:text props)
                 (when (or (:active? props) (:hovering? @state))
                   [:div {:style {:position "absolute" :top "-0.5ex" :left 0
                                  :width "100%" :height "0.5ex"
                                  :backgroundColor (:button-blue colors)}}])
                 (when (:active? props)
                   [:div {:style {:position "absolute" :bottom -1 :left 0 :width "100%" :height 2
                                  :backgroundColor "white"}}])])})]
    {:get-default-props
     (fn []
       {:active-tab 0})
     :render
     (fn [{:keys [props]}]
       [:div {}
        (map-indexed
         (fn [i text]
           [Tab {:index i :text text :active? (= i (:active-tab props))}])
         (:items props))
        [:div {:style {:clear "both"}}]])}))
