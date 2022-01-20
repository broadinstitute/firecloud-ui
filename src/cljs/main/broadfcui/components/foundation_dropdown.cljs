(ns broadfcui.components.foundation-dropdown
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))


(react/defc FoundationDropdown
  {:close
   (fn [{:keys [locals]}]
     (.foundation (js/$ (:dropdown-element @locals)) "close"))
   :component-will-mount
   (fn [{:keys [locals]}]
     (let [dropdown-container (.createElement js/document "div")]
       (swap! locals assoc :dropdown-container dropdown-container)
       (.insertBefore js/document.body dropdown-container (utils/get-app-root-element))
       (swap! locals assoc :dropdown-id (gensym "dropdown-"))))
   :render
   (fn [{:keys [props locals state this]}]
     (let [{:keys [button dropdown]} props
           {:keys [dropdown-container dropdown-id]} @locals
           {:keys [contents className style]} button]
       [:button {:className (str "button-reset " className)
                 :data-toggle dropdown-id
                 :style (merge {:cursor "pointer" :padding "0 0.5rem"
                                :fontSize 16 :lineHeight "1rem"}
                               style)}
        contents
        (let [{:keys [className style contents]} dropdown]
          (js/ReactDOM.createPortal
           (react/create-element
            ;; empty string makes react attach a property with no value
            [:div {:className (str "dropdown-pane " className) :id dropdown-id :data-dropdown ""
                   :ref (this :-create-dropdown-ref-handler)
                   :style (merge
                           {:whiteSpace "normal"}
                           style
                           (when (= (:width style) :auto)
                             {:width (:width @state)
                              :boxSizing "border-box" :minWidth 120}))}
             contents])
           dropdown-container))]))
   :component-did-mount
   (fn [{:keys [locals]}]
     (.foundation (js/$ (:dropdown-element @locals))))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (.remove (:dropdown-container @locals)))
   :-create-dropdown-ref-handler
   (fn [{:keys [this props locals state]}]
     (common/create-element-ref-handler
      {:store locals
       :element-key :dropdown-element
       :did-mount
       (fn [element]
         (let [element$ (js/$ element)
               button$ (js/$ (react/find-dom-node this))]
           (letfn [(clean-up []
                     (.off (js/$ "body") "click.zf.dropdown" close-on-body-click)
                     (.off element$ "click.zf.dropdown" close-on-element-click))
                   (close-on-element-click [_]
                    ;; timeout to allow click handlers to fire
                     (js/setTimeout #(.foundation element$ "close") 0)
                     (clean-up))
                   (close-on-body-click [e]
                     (when-not (or (.is button$ (.-target e))
                                   (pos? (.-length (.find button$ (.-target e))))
                                   (.is element$ (.-target e))
                                   (pos? (.-length (.find element$ (.-target e)))))
                       (.foundation element$ "close")
                       (clean-up)))]
             (.on button$
                  "click"
                  (fn [_]
                    (when (= (get-in props [:dropdown :style :width]) :auto)
                      (swap! state assoc :width (.-clientWidth (react/find-dom-node this))))
                    (when (:close-on-click props)
                      (.on element$ "click.zf.dropdown" close-on-element-click))
                    (.on (js/$ "body") "click.zf.dropdown" close-on-body-click))))))
       :will-unmount
       (fn [element]
         (.off (js/$ (react/find-dom-node this)) "click")
         (.off (js/$ element) "hide.zf.dropdown"))}))})

(defn render-icon-dropdown [{:keys [position contents icon-name icon-color icon-title] :as props}]
  [FoundationDropdown
   (utils/deep-merge {:dropdown {:className position :contents contents}
                      :button {:contents (icons/render-icon
                                          {:title icon-title :style {:color icon-color}} icon-name)}}
                     props)])

(defn render-info-box [{:keys [text] :as props}]
  (render-icon-dropdown
   (merge {:contents text
           :icon-name :information :icon-color (:button-primary style/colors)}
          props)))

(defn render-dropdown-menu [{:keys [label items width button-style]}]
  [FoundationDropdown
   {:button {:contents label
             :style (merge {:fontSize "unset" :lineHeight "unset" :padding 0 :textAlign "center"}
                           button-style)}
    :close-on-click true
    :dropdown {:className "bottom"
               :style {:boxShadow "0 3px 6px 0 rgba(0, 0, 0, 0.15)"
                       :backgroundColor "#fff"
                       :padding 0 :width width
                       :border style/standard-line}
               :contents (let [DropdownItem
                               (react/create-class
                                {:render
                                 (fn [{:keys [props state]}]
                                   (let [{:keys [data-test-id dismiss href target text]} props]
                                     [:a {:style {:display "block"
                                                  :color "#000" :textDecoration "none" :fontSize 14
                                                  :padding "0.5rem"
                                                  :backgroundColor (when (:hovering? @state) "#e8f5ff")}
                                          :href href
                                          :target target
                                          :onMouseOver #(swap! state assoc :hovering? true)
                                          :onMouseOut #(swap! state assoc :hovering? false)
                                          :onClick dismiss
                                          :data-test-id data-test-id}
                                      text]))})]
                           [:div {}
                            (map (fn [item]
                                   [DropdownItem (merge {:href "javascript:;" :target "_self"}
                                                        item)])
                                 items)])}}])
