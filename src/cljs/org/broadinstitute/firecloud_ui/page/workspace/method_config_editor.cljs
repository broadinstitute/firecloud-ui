(ns org.broadinstitute.firecloud-ui.page.workspace.method-config-editor
  (:require
    [dmohs.react :as react]
    [clojure.string :refer [join trim blank?]]
    [org.broadinstitute.firecloud-ui.common :as common :refer [clear-both]]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

;; This is a unique id for the prerequisite text fields.  Without this, React can't tell them apart
;; and mishandles deleting of prerequisites from the middle.
(defonce uid (atom 0))
(defn- get-id []
  (let [result @uid]
    (swap! uid inc)
    result))


(defn- capture-prerequisites [state refs]
  (vec (map
         #(-> (@refs (str "pre_" %)) .getDOMNode .-value)
         (range (count (:prereqs-list @state))))))

(defn- filter-empty [list]
  (vec (remove blank? (map trim list))))


(defn- create-section-header [text]
  [:div {:style {:fontSize "125%" :fontWeight 500}} text])

(defn- create-section [& children]
  [:div {:style {:padding "1em 0 2em 0"}} children])

(defn- stop-editing [state refs]
  (swap! state assoc :editing? false :prereqs-list (filter-empty (capture-prerequisites state refs))))

(defn- update-config [state props new-config]
  (swap! state assoc :config new-config)
  ((:onCommit props) new-config))

;; Rawls is screwed up right now: Prerequisites should simply be a list of strings, not a map.
;; In addition, on this endpoint, the methodStore[Config|Method] has been renamed to methodRepo[Config|Method]
;; Delete this when the backend is fixed
(defn- rebreak-config [config]
  (if utils/use-live-data?
    (dissoc (assoc config "prerequisites" (zipmap (repeat "unused") (config "prerequisites"))
                  "methodRepoConfig" (config "methodStoreConfig")
                  "methodRepoMethod" (config "methodStoreMethod"))
      "methodStoreConfig" "methodStoreMethod")
    config))

(defn- commit [state refs config props]
  (let [workspace (:workspace props)
        name (-> (@refs "confname") .getDOMNode .-value)
        inputs (into {} (map (juxt identity #(-> (@refs (str "in_" %)) .getDOMNode .-value)) (keys (config "inputs"))))
        outputs (into {} (map (juxt identity #(-> (@refs (str "out_" %)) .getDOMNode .-value)) (keys (config "outputs"))))
        prereqs (filter-empty (capture-prerequisites state refs))
        new-conf (assoc config
                   "name" name
                   "inputs" inputs
                   "outputs" outputs
                   "prerequisites" prereqs)]
    (swap! state assoc :updating? true)
    (utils/ajax-orch
      (str "/workspaces/" (workspace "namespace") "/" (workspace "name") "/method_configs/"
        (config "namespace") "/" (config "name"))
      {:method :PUT
       :data (utils/->json-string (rebreak-config new-conf))
       :headers {"Content-Type" "application/json"} ;; TODO - make endpoint take text/plain
       :on-done (fn [{:keys [success? xhr]}]
                  (swap! state assoc :updating? false)
                  (if success?
                    (update-config state props new-conf)
                    (js/alert (str "Exception:\n" (.-statusText xhr)))))
       :canned-response {:status 200 :delay-ms (rand-int 2000)}})))

(defn- render-top-bar [config]
  [:div {:style {:backgroundColor (:background-gray style/colors)
                 :borderRadius 8 :border (str "1px solid " (:line-gray style/colors))
                 :padding "1em"}}
   [:div {:style {:float "left" :width "33.33%" :textAlign "left"}}
    [:span {:style {:fontWeight 500 :padding "0 0.5em"}} "Method Namespace:"]
    [:span {} (get-in config ["methodStoreMethod" "methodNamespace"])]]
   [:div {:style {:float "left" :width "33.33%" :textAlign "center"}}
    [:span {:style {:fontWeight 500 :padding "0 0.5em"}} "Method Name:"]
    [:span {} (get-in config ["methodStoreMethod" "methodName"])]]
   [:div {:style {:float "left" :width "33.33%" :textAlign "right"}}
    [:span {:style {:fontWeight 500 :padding "0 0.5em"}} "Method Version:"]
    [:span {} (get-in config ["methodStoreMethod" "methodVersion"])]]
   (clear-both)])

(defn- render-side-bar [state refs config editing? props]
  [:div {:style {:width 290 :float "left"}}
   [:div {:ref "sidebar"}]
   (style/create-unselectable :div {:style {:position (when-not (:sidebar-visible? @state) "fixed")
                                            :top (when-not (:sidebar-visible? @state) 4)
                                            :width 290}}
     [:div {:style {:fontSize "106%" :lineHeight 1 :textAlign "center"}}

      [:div {:style {:display (when editing? "none") :padding "0.7em 0em" :cursor "pointer"
                     :backgroundColor "transparent" :color (:button-blue style/colors)
                     :border (str "1px solid " (:line-gray style/colors))}
             :onClick #(swap! state assoc :editing? true :prereqs-list (config "prerequisites"))}
       [:span {:style {:display "inline-block" :verticalAlign "middle"}}
        (icons/font-icon {:style {:fontSize "135%"}} :pencil)]
       [:span {:style {:marginLeft "1em"}} "Edit this page"]]

      [:div {:style {:display (when-not editing? "none") :padding "0.7em 0em" :cursor "pointer"
                     :backgroundColor (:success-green style/colors) :color "#fff" :borderRadius 4}
             :onClick #(do (commit state refs config props) (stop-editing state refs))}
       [:span {:style {:display "inline-block" :verticalAlign "middle"}}
        (icons/font-icon {:style {:fontSize "135%"}} :status-done)]
       [:span {:style {:marginLeft "1em"}} "Save"]]

      [:div {:style {:display (when-not editing? "none") :padding "0.7em 0em" :marginTop "0.5em" :cursor "pointer"
                     :backgroundColor (:exception-red style/colors) :color "#fff" :borderRadius 4}
             :onClick #(stop-editing state refs)}
       [:span {:style {:display "inline-block" :verticalAlign "middle"}}
        (icons/font-icon {:style {:fontSize "135%"}} :x)]
       [:span {:style {:marginLeft "1em"}} "Cancel Editing"]]])])

(defn- render-main-display [state refs config editing?]
  [:div {:style {:marginLeft 330}}
   (create-section-header "Method Configuration Name")
   (create-section
     [:div {:style {:display (when editing? "none") :padding "0.5em 0 1em 0"}}
      (config "name")]
     [:div {:style {:display (when-not editing? "none")}}
      (style/create-text-field {:ref "confname" :defaultValue (config "name")})])
   (create-section-header "Inputs")
   (create-section
     [:div {}
      (for [key (keys (config "inputs"))]
        [:div {:style {:verticalAlign "middle"}}
         [:div {:style {:float "left" :marginRight "1em" :padding "0.5em" :marginBottom "0.5em"
                        :backgroundColor (:background-gray style/colors)
                        :border (str "1px solid " (:line-gray style/colors)) :borderRadius 2}}
          (str key ":")]
         [:div {:style {:float "left" :display (when editing? "none") :padding "0.5em 0 1em 0"}}
          (get (config "inputs") key)]
         [:div {:style {:float "left" :display (when-not editing? "none")}}
          (style/create-text-field {:ref (str "in_" key) :defaultValue (get (config "inputs") key)})]
         (clear-both)])])
   (create-section-header "Outputs")
   (create-section
     [:div {}
      (for [key (keys (config "outputs"))]
        [:div {:style {:verticalAlign "middle"}}
         [:div {:style {:float "left" :marginRight "1em" :padding "0.5em" :marginBottom "0.5em"
                        :backgroundColor (:background-gray style/colors)
                        :border (str "1px solid " (:line-gray style/colors)) :borderRadius 2}}
          (str key ":")]
         [:div {:style {:float "left" :display (when editing? "none") :padding "0.5em 0 1em 0"}}
          (get (config "outputs") key)]
         [:div {:style {:float "left" :display (when-not editing? "none")}}
          (style/create-text-field {:ref (str "out_" key) :defaultValue (get (config "outputs") key)})]
         (clear-both)])])
   (create-section-header "Prerequisites")
   (create-section
     [:div {}
      (if editing?
        (map-indexed
          (fn [i p]
            [:div {}
             [:div {:style {:float "left"}}
              (style/create-text-field {:ref (str "pre_" i) :defaultValue p :key (get-id)})
              (icons/font-icon {:style {:paddingLeft "0.5em" :padding "1em 0.7em" :color "red" :cursor "pointer"}
                                :onClick #(let [l (capture-prerequisites state refs)
                                                new-l (vec (concat (subvec l 0 i) (subvec l (inc i))))]
                                            (swap! state assoc :prereqs-list new-l))}
                :x)]
             (clear-both)])
          (:prereqs-list @state))
        (map
          (fn [p]
            [:div {}
             [:div {:style {:float "left" :display (when editing? "none") :padding "0.5em 0" :marginBottom "0.5em"}}
              p]
             (clear-both)])
          (config "prerequisites")))
      [:div {:style {:display (when-not editing? "none")}}
       [comps/Button {:style :add :text "Add new"
                      :onClick #(let [list (capture-prerequisites state refs)]
                                 (swap! state assoc :prereqs-list (conj list "")))}]]])])

(defn- render-updating-overlay [state]
  [:div {:style {:position "fixed" :top 0 :bottom 0 :right 0 :left 0
                 :backgroundColor "rgba(127, 127, 127, 0.5)"
                 :zIndex 9999
                 :display (when-not (:updating? @state) "none")}}
   [:div {:style {:position "absolute" :top "50%" :left "50%"
                  :transform "translate(-50%, -50%)"
                  :backgroundColor "#fff" :padding "2em"}}
    [comps/Spinner {:text "Updating..."}]]])

(defn- render-display [state refs config editing? props]
  [:div {}
   (render-updating-overlay state)
   [:div {:style {:padding "0em 2em"}}
    (render-top-bar config)
    [:div {:style {:padding "1em 0em"}}
     (render-side-bar state refs config editing? props)
     (render-main-display state refs config editing?)
     (clear-both)]]])

(react/defc MethodConfigEditor
  {:get-initial-state
   (fn [{:keys [props]}]
     {:config (:config props)
      :editing? false
      :sidebar-visible? true})
   :render
   (fn [{:keys [state refs props]}]
     (render-display state refs (:config @state) (:editing? @state) props))
   :component-did-mount
   (fn [{:keys [state refs this]}]
     (set! (.-onScrollHandler this)
       (fn [] (let [visible (common/is-in-view (.getDOMNode (@refs "sidebar")))]
                (when-not (= visible (:sidebar-visible? @state))
                  (swap! state assoc :sidebar-visible? visible)))))
     (.addEventListener js/window "scroll" (.-onScrollHandler this)))
   :component-will-unmount
   (fn [{:keys [this]}]
     (.removeEventListener js/window "scroll" (.-onScrollHandler this)))})
