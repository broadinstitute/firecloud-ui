(ns broadfcui.components.top-banner
  (:require
   [broadfcui.common.icons :as icons]
   [broadfcui.common.style :as style]
   [dmohs.react :as r]
   [linked.core :as linked]
   ))

(defonce ^:private instance nil)

(r/defc Container
  {:add
   (fn [{:keys [props state]} id content]
     (swap! state update :stack assoc id content))
   :remove
   (fn [{:keys [state after-update]} id]
     (swap! state update :stack dissoc id))
   :get-initial-state (constantly {:stack {}})
   :render
   (fn [{:keys [state]}]
     (let [{:keys [stack]} @state
           stack (filter (complement nil?) (vals stack))]
       [:div {}
        (interpose [:div {:style {:height 1}}] stack)]))
   :component-did-mount
   (fn [{:keys [this]}]
     (set! instance this))
   :component-will-unmount
   (fn [{:keys [this]}]
     (set! instance nil))})

(r/defc Banner
  {:render
   (fn [{:keys [props]}]
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
  (let [element (if (r/valid-element? content) content (r/create-element content))]
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
