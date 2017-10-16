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
   :get-default-props
   (fn []
     {:icon-name :information})
   :component-will-mount
   (fn [{:keys [locals]}]
     (swap! locals assoc :dropdown-id (gensym "dropdown-")))
   :render
   (fn [{:keys [props locals]}]
     [:button {:className (str "button-reset " (:button-class props))
               :data-toggle (:dropdown-id @locals)
               :style (merge {:cursor "pointer" :padding "0 0.5rem"
                              :fontSize 16 :lineHeight "1rem"}
                             (:button-style props))}
      (:button-contents props)])
   :component-did-mount
   (fn [{:keys [this locals]}]
     (let [dropdown-container (.createElement js/document "div")]
       (swap! locals assoc :dropdown-container dropdown-container)
       (.insertBefore js/document.body dropdown-container (utils/get-app-root-element))
       (this :-render-dropdown)
       (.foundation (js/$ (:dropdown-element @locals)))))
   :component-will-receive-props
   (fn [{:keys [this]}]
     (this :-render-dropdown))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (react/unmount-component-at-node (:dropdown-container @locals))
     (.remove (:dropdown-container @locals)))
   :-render-dropdown
   (fn [{:keys [this props state locals]}]
     (let [{:keys [contents dropdown-class]} props
           {:keys [dropdown-container dropdown-id]} @locals]
       (react/render
        (react/create-element
         ;; empty string makes react attach a property with no value
         [:div {:className (str "dropdown-pane " dropdown-class) :id dropdown-id :data-dropdown ""
                :ref (this :-create-dropdown-ref-handler)
                :style (merge
                        {:whiteSpace "normal"}
                        (:style props)
                        (when (= (get-in props [:style :width]) :auto)
                          {:width (.-clientWidth (react/find-dom-node this))
                           :boxSizing "border-box" :minWidth 120}))}
          (when (:render-contents? @state)
            contents)])
        dropdown-container)))
   :-create-dropdown-ref-handler
   (fn [{:keys [this props state after-update locals]}]
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
             (.on element$ "hide.zf.dropdown"
                  (fn [_]
                    (swap! state dissoc :render-contents?)
                    (after-update #(this :-render-dropdown))))
             (.on button$
                  "click"
                  (fn [_]
                    (swap! state assoc :render-contents? true)
                    (after-update #(this :-render-dropdown))
                    (when (:close-on-click props)
                      (.on element$ "click.zf.dropdown" close-on-element-click))
                    (.on (js/$ "body") "click.zf.dropdown" close-on-body-click))))))
       :will-unmount
       (fn [element]
         (.off (js/$ (react/find-dom-node this)) "click")
         (.off (js/$ element) "hide.zf.dropdown"))}))})

(defn render-icon-dropdown [{:keys [position icon-name icon-color icon-title] :as props}]
  [FoundationDropdown
   (merge {:dropdown-class position
           :button-contents (icons/icon
                             {:title icon-title :style {:color icon-color}} icon-name)}
          props)])

(defn render-info-box [{:keys [text] :as props}]
  (render-icon-dropdown
   (merge {:contents text
           :icon-name :information :icon-color (:button-primary style/colors)}
          props)))

(defn render-dropdown-menu [{:keys [label items width button-style]}]
  [FoundationDropdown
   {:button-contents label
    :button-style (merge {:fontSize "unset" :lineHeight "unset" :padding 0 :textAlign "center"}
                         button-style)
    :close-on-click true
    :dropdown-class "bottom"
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
                      items)])}])
