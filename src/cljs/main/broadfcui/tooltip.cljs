(ns broadfcui.tooltip
  (:require
   [broadfcui.config :as config]
   [broadfcui.utils :as u]
   [dmohs.react :as r]
   ))

(defonce instance nil)

(defn show [id anchor content]
  (instance :show id anchor content))

(defn remove-tooltip [id]
  (instance :remove id))

(r/defc ToolTip
  {:component-will-mount
   (fn [{:keys [locals]}]
     (swap! locals assoc :id (gensym "tooltip-")))
   :render
   (fn [{:keys [this props state]}]
     [:span {:onMouseEnter #(swap! state assoc :show? true)
             :onMouseLeave #(swap! state dissoc :show?)}
      (:element props)])
   :component-did-update
   (fn [{:keys [this props state locals]}]
     (if (:show? @state)
       (show (:id @locals) (r/find-dom-node this) (:text props))
       (remove-tooltip (:id @locals))))})

(defn with-tooltip [text element]
  [ToolTip {:text text :element element}])

(defn- find-tooltip [tooltips id]
  (first (filter #(= id (:id %)) tooltips)))

(defn- update-tooltip [tooltips id f & args]
  (let [tip (find-tooltip tooltips id)
        new? (if tip false true)
        tip (if new? {:id id} tip)
        tooltips (if new? (conj tooltips tip) tooltips)
        updated (apply f tip args)]
    (if updated
      (mapv #(if (= id (:id %)) updated %) tooltips)
      (filterv #(not= id (:id %)) tooltips))))

(r/defc Container
  {:show
   (fn [{:keys [state]} id anchor content]
     (swap! state update :tooltips update-tooltip id
           (fn [x]
              (assoc (dissoc x :remove?) :anchor anchor :content content))))
   :remove
   (fn [{:keys [state]} id]
     (swap! state update :tooltips update-tooltip id
            (fn [x]
              (if (:protected? x)
                (assoc x :remove? true)
                nil))))
   :get-initial-state
   (fn []
     {:tooltips []})
   :render
   (fn [{:keys [this state]}]
     (let [{:keys [tooltips]} @state]
       [:div {}
        (map (fn [tooltip]
               (let [rect (.getBoundingClientRect (:anchor tooltip))]
                 [:div {:style {:position "absolute"
                                :top (+ (aget rect "top") (aget rect "height"))
                                :left (str "calc(" (aget rect "left") "px - 1rem)")
                                :opacity (if (:showing? tooltip) 1 0)
                                :transition "opacity 0.05s ease"}
                        :onMouseEnter #(this :-set-protected? (:id tooltip) true)
                        :onMouseLeave (fn [e]
                                        ;; mouseleave occurs before mouseenter, so this delay avoids
                                        ;; a flash if the mouse moves from this to the anchor
                                        (js/setTimeout
                                         #(this :-set-protected? (:id tooltip) false)
                                         0))}
                  [:div {:style {:position "absolute" :left "1rem"
                                 :width "2rem" :height "0.5rem"
                                 :overflow "hidden"}}
                   [:div {:style {:backgroundColor "rgba(50, 50, 50, 0.9)"
                                  :width "2rem" :height "2rem"
                                  :marginTop "0.5rem" :marginLeft "0"
                                  :transform "rotate(45deg)"}}]]
                  [:div {:style {:backgroundColor "rgba(50, 50, 50, 0.9)"
                                 :borderRadius 4
                                 :margin "0.5rem 2rem 2rem 1rem"
                                 :padding "0.4rem 0.8rem"
                                 :color "#eee"}}
                   (:content tooltip)]]))
             tooltips)]))
   :component-did-update
   (fn [{:keys [state]}]
     (when (some #(not (:showing? %)) (:tooltips @state))
       (swap! state update :tooltips #(mapv (fn [tt] (assoc tt :showing? true)) %))))
   :-set-protected?
   (fn [{:keys [state]} id protected?]
     (swap! state update :tooltips update-tooltip id
            (fn [x]
              (if protected?
                (assoc x :protected? true)
                (if (:remove? x)
                  nil
                  (dissoc x :protected?))))))})
