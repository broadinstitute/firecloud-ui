(ns broadfcui.tooltip
  (:require
   [broadfcui.config :as config]
   [broadfcui.utils :as u]
   [dmohs.react :as r]
   ))

(defonce instance nil)

(defn show [id trigger content]
  (instance :show id trigger content))

(defn remove-tooltip [id]
  (instance :deref id :trigger))

(r/defc ToolTip
  {:component-will-mount
   (fn [{:keys [locals]}]
     (swap! locals assoc :id (gensym "tooltip-")))
   :render
   (fn [{:keys [this props state]}]
     [:span {:onMouseEnter #(swap! state assoc :show? true)
             :onMouseLeave #(swap! state dissoc :show?)}
      (:trigger-element props)])
   :component-did-update
   (fn [{:keys [this props state locals]}]
     (if (:show? @state)
       (show (:id @locals) (r/find-dom-node this) (:hover-element props))
       (remove-tooltip (:id @locals))))})

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

(defn- remove-derefed [tooltips]
  (filterv #(not (empty? (:references %))) tooltips))

(r/defc Container
  {:show
   (fn [{:keys [state]} id trigger content]
     (swap! state update :tooltips update-tooltip id
            assoc :references #{:trigger} :trigger trigger :content content))
   :addref
   (fn [{:keys [state]} id reference]
     (swap! state update :tooltips update-tooltip id update :references conj reference))
   :deref
   (fn [{:keys [state]} id reference]
     (swap! state update :tooltips
            (fn [tooltips]
              (-> tooltips
                  (update-tooltip id update :references disj reference)
                  remove-derefed))))
   :get-initial-state
   (fn []
     {:tooltips []})
   :render
   (fn [{:keys [this state]}]
     (let [{:keys [tooltips]} @state]
       [:div {}
        (map (fn [tooltip]
               (let [rect (.getBoundingClientRect (:trigger tooltip))]
                 [:div {:style {:position "absolute"
                                :top (+ (aget rect "top") (aget rect "height"))
                                :left (str "calc(" (aget rect "left") "px - 1rem)")
                                :opacity (if (:showing? tooltip) 1 0)
                                :transition "opacity 0.05s ease"}
                        :onMouseEnter #(this :addref (:id tooltip) :body)
                        :onMouseLeave (fn [e]
                                        ;; mouseleave occurs before mouseenter, so this delay avoids
                                        ;; a flash if the mouse moves from this to the trigger
                                        (js/setTimeout
                                         #(this :deref (:id tooltip) :body)
                                         0))}
                  (:content tooltip)]))
             tooltips)]))
   :component-did-mount
   (fn [{:keys [this]}]
     (set! instance this))
   :component-did-update
   (fn [{:keys [state]}]
     (when (some #(not (:showing? %)) (:tooltips @state))
       (swap! state update :tooltips #(mapv (fn [tt] (assoc tt :showing? true)) %))))})

(defn with-tooltip [trigger-element hover-element]
  [ToolTip {:trigger-element trigger-element :hover-element hover-element}])

(defn- with-default-hover-container [element]
  [:div {}
   [:div {:style {:position "absolute" :top 0 :left "1rem"
                  :width "2rem" :height "0.5rem"
                  :overflow "hidden"}}
    [:div {:style {:backgroundColor "rgba(50, 50, 50, 0.9)"
                   :width "2rem" :height "2rem"
                   :marginTop "0.5rem" :marginLeft 0
                   :transform "rotate(45deg)"}}]]
   [:div {:style {:backgroundColor "rgba(50, 50, 50, 0.9)"
                  :borderRadius 4
                  :margin "0.5rem 2rem 2rem 1rem"
                  :padding "0.4rem 0.8rem"
                  :color "#eee"}}
    element]])

(defn with-default [trigger-element hover-element]
  (with-tooltip trigger-element (with-default-hover-container hover-element)))
