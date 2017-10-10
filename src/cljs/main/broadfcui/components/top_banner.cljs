(ns broadfcui.components.top-banner
  (:require
   [dmohs.react :as react]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   ))

(defonce ^:private instance nil)

(react/defc Container
  {:add
   (fn [{:keys [state]} id content]
     (swap! state update :stack assoc id content))
   :remove
   (fn [{:keys [state]} id]
     (swap! state update :stack dissoc id))
   :get-initial-state (constantly {:stack {}})
   :render
   (fn [{:keys [state]}]
     (let [{:keys [stack]} @state
           stack (remove nil? (vals stack))]
       [:div {}
        (interpose [:div {:style {:height 1}}] stack)]))
   :component-did-mount
   (fn [{:keys [this]}]
     (set! instance this))
   :component-will-unmount
   (fn []
     (set! instance nil))})

(react/defc- Banner
  {:render
   (fn []
     nil)
   :component-did-mount
   (fn [{:keys [props locals]}]
     (swap! locals assoc :id (gensym "banner-"))
     (instance :add (:id @locals) (:content props)))
   :component-will-receive-props
   (fn [{:keys [next-props locals]}]
     (instance :add (:id @locals) (:content next-props)))
   :component-will-unmount
   (fn [{:keys [locals]}]
     (instance :remove (:id @locals)))})

(defn render [content]
  (let [element (if (react/valid-element? content) content (react/create-element content))]
    [Banner {:content element}]))

(defn render-warning [{:keys [title message link]}]
  (let [text-color "#eee"]
    (render
     [:div {:style {:color text-color :background-color (:exception-state style/colors)
                    :padding "1rem"}}
      [:div {:style {:display "flex" :align-items "baseline"}}
       [icons/ExceptionIcon {:size 18 :color text-color}]
       [:span {:style {:margin-left "0.5rem" :font-weight "bold" :vertical-align "middle"}}
        title]
       [:span {:style {:color text-color :fontSize "90%" :marginLeft "1rem"}}
        (str message " ") link]]])))
