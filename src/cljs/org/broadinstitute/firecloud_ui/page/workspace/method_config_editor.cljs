(ns org.broadinstitute.firecloud-ui.page.workspace.method-config-editor
  (:require
    [dmohs.react :as react]
    [clojure.string :refer [trim blank?]]
    [org.broadinstitute.firecloud-ui.common :as common :refer [clear-both]]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.page.workspace.launch-analysis :as launch]
    [org.broadinstitute.firecloud-ui.paths :as paths]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


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

(defn- complete [state new-config]
  (swap! state assoc :loaded-config new-config :blocker nil))

;; Rawls is screwed up right now: Prerequisites should simply be a list of strings, not a map.
;; Delete this when the backend is fixed
(defn- fix-prereqs [prereqs]
  (zipmap (map #(str "unused" %) (range)) prereqs))

(defn- commit [state refs config props]
  (let [workspace (:workspace props)
        name (-> (@refs "confname") .getDOMNode .-value)
        inputs (into {} (map (juxt identity #(-> (@refs (str "in_" %)) .getDOMNode .-value)) (keys (config "inputs"))))
        outputs (into {} (map (juxt identity #(-> (@refs (str "out_" %)) .getDOMNode .-value)) (keys (config "outputs"))))
        prereqs (fix-prereqs (filter-empty (capture-prerequisites state refs)))
        new-conf (assoc config
                   "name" name
                   "inputs" inputs
                   "outputs" outputs
                   "prerequisites" prereqs)]
    (swap! state assoc :blocker "Updating...")
    (utils/ajax-orch (paths/update-method-config-path workspace config)
      {:method :PUT
       :data (utils/->json-string new-conf)
       :headers {"Content-Type" "application/json"} ;; TODO - make endpoint take text/plain
       :on-done (fn [{:keys [success? xhr]}]
                  (if-not success?
                    (js/alert (str "Exception:\n" (.-statusText xhr)))
                    (if (= name (config "name"))
                      (complete state new-conf)
                      (utils/ajax-orch (paths/rename-method-config-path workspace config)
                        {:method :post
                         :data (utils/->json-string (select-keys new-conf ["name" "namespace" "workspaceName"]))
                         :headers {"Content-Type" "application/json"} ;; TODO - make unified call in orchestration
                         :on-done (fn [{:keys [success? xhr]}]
                                    (complete state new-conf)
                                    (when-not success?
                                      (js/alert (str "Exception:\n" (.-statusText xhr)))))
                         :canned-response {:status 200 :delay-ms (rand-int 2000)}}))))
       :canned-response {:status 200 :delay-ms (rand-int 2000)}})))

(defn- render-top-bar [config]
  [:div {:style {:backgroundColor (:background-gray style/colors)
                 :borderRadius 8 :border (str "1px solid " (:line-gray style/colors))
                 :padding "1em"}}
   [:div {:style {:float "left" :width "33.33%" :textAlign "left"}}
    [:span {:style {:fontWeight 500 :padding "0 0.5em"}} "Method Namespace:"]
    [:span {} (get-in config ["methodRepoMethod" "methodNamespace"])]]
   [:div {:style {:float "left" :width "33.33%" :textAlign "center"}}
    [:span {:style {:fontWeight 500 :padding "0 0.5em"}} "Method Name:"]
    [:span {} (get-in config ["methodRepoMethod" "methodName"])]]
   [:div {:style {:float "left" :width "33.33%" :textAlign "right"}}
    [:span {:style {:fontWeight 500 :padding "0 0.5em"}} "Method Version:"]
    [:span {} (get-in config ["methodRepoMethod" "methodVersion"])]]
   (clear-both)])

(defn- render-side-bar [state refs config editing? props]
  [:div {:style {:width 290 :float "left"}}
   [:div {:ref "sidebar"}]
   (style/create-unselectable :div {:style {:position (when-not (:sidebar-visible? @state) "fixed")
                                            :top (when-not (:sidebar-visible? @state) 4)
                                            :width 290}}
     [:div {:style {:fontSize "106%" :lineHeight 1 :textAlign "center"}}

      [:div {:style {:display (when editing? "none") :padding "0.7em 0" :cursor "pointer"
                     :backgroundColor "transparent" :color (:button-blue style/colors)
                     :border (str "1px solid " (:line-gray style/colors))}
             :onClick #(swap! state assoc :editing? true :prereqs-list (vals (config "prerequisites")))}
       [:span {:style {:display "inline-block" :verticalAlign "middle"}}
        (icons/font-icon {:style {:fontSize "135%"}} :pencil)]
       [:span {:style {:marginLeft "1em"}} "Edit this page"]]

      [:div {:style {:display (when-not editing? "none") :padding "0.7em 0" :cursor "pointer"
                     :backgroundColor (:success-green style/colors) :color "#fff" :borderRadius 4}
             :onClick #(do (commit state refs config props) (stop-editing state refs))}
       [:span {:style {:display "inline-block" :verticalAlign "middle"}}
        (icons/font-icon {:style {:fontSize "135%"}} :status-done)]
       [:span {:style {:marginLeft "1em"}} "Save"]]

      [:div {:style {:display (when-not editing? "none") :padding "0.7em 0" :marginTop "0.5em" :cursor "pointer"
                     :backgroundColor (:exception-red style/colors) :color "#fff" :borderRadius 4}
             :onClick #(stop-editing state refs)}
       [:span {:style {:display "inline-block" :verticalAlign "middle"}}
        (icons/font-icon {:style {:fontSize "135%"}} :x)]
       [:span {:style {:marginLeft "1em"}} "Cancel Editing"]]])])

(defn- render-launch-analysis [state workspace editing?]
  [:div {:style {:width 200 :float "right" :display (when editing? "none")}}
   (style/create-unselectable :div {:style {:position (when-not (:sidebar-visible? @state) "fixed")
                                            :top (when-not (:sidebar-visible? @state) 4)
                                            :width 200}}
     [:div {:style {:fontSize "106%" :lineHeight 1 :textAlign "center"}}
      [:div {:style {:padding "0.7em 0" :cursor "pointer"
                     :backgroundColor (:button-blue style/colors) :color "#fff" :borderRadius 4
                     :border (str "1px solid " (:line-gray style/colors))}
             :onClick #(if (:entity-types @state)
                        (swap! state assoc :submitting? true)
                        (do (swap! state assoc :blocker "Loading Entities...")
                            (utils/call-ajax-orch
                              (paths/get-entities-by-type-path workspace)
                              {:on-success (fn [{:keys [parsed-response]}]
                                             (let [emap (group-by (fn [e] (e "entityType")) parsed-response)
                                                   first-entities (get emap (first (keys emap)))
                                                   first-entity (first first-entities)]
                                               (swap! state assoc :blocker nil :submitting? true
                                                 :entity-map emap
                                                 :entities first-entities
                                                 :selected-entity first-entity)))
                               :on-failure (fn [{:keys [status-text]}]
                                             (swap! state assoc :blocker nil)
                                             (js/alert (str "Error: " status-text)))
                               :mock-data [{"name" "Mock Sample" "entityType" "Sample"}
                                           {"name" "Mock Participant" "entityType" "Participant"}]})))}
       "Launch Analysis"]])])


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
              (style/create-text-field {:ref (str "pre_" i) :defaultValue p :key (name (gensym))})
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
          (vals (config "prerequisites"))))
      [:div {:style {:display (when-not editing? "none")}}
       [comps/Button {:style :add :text "Add new"
                      :onClick #(let [list (capture-prerequisites state refs)]
                                 (swap! state assoc :prereqs-list (conj list "")))}]]])])

(defn- render-display [state refs config editing? props]
  [:div {}
   [comps/Blocker {:banner (:blocker @state)}]
   (launch/render-launch-overlay state refs  (:workspace props) config)
   [:div {:style {:padding "0em 2em"}}
    (render-top-bar config)
    [:div {:style {:padding "1em 0em"}}
     (render-side-bar state refs config editing? props)
     (render-launch-analysis state (:workspace props) editing?)
     (render-main-display state refs config editing?)
     (clear-both)]]])

(defn- build-mock-config [conf]
  (assoc conf "methodRepoMethod" (conf "methodStoreMethod")
              "methodRepoConfig" (conf "methodStoreConfig")))

(react/defc MethodConfigEditor
  {:get-initial-state
   (fn []
     {:editing? false
      :sidebar-visible? true})
   :render
   (fn [{:keys [state refs props]}]
     (cond (:loaded-config @state) (render-display state refs (:loaded-config @state) (:editing? @state) props)
           (:error @state) (style/create-server-error-message (:error @state))
           :else [comps/Spinner {:text "Loading Method Configuration..."}]))
   :component-did-mount
   (fn [{:keys [state props refs this]}]
     (utils/ajax-orch
       (paths/get-method-config-path (:workspace props) (:config props))
       {:on-done (fn [{:keys [success? xhr]}]
                   (if success?
                     (swap! state assoc :loaded-config (utils/parse-json-string (.-responseText xhr)))
                     (swap! state assoc :error (.-statusText xhr))))
        :canned-response {:responseText (utils/->json-string (build-mock-config (:config props)))
                          :status 200 :delay-ms (rand-int 2000)}})
     (set! (.-onScrollHandler this)
       (fn [] (let [visible (common/is-in-view (.getDOMNode (@refs "sidebar")))]
                (when-not (= visible (:sidebar-visible? @state))
                  (swap! state assoc :sidebar-visible? visible)))))
     (.addEventListener js/window "scroll" (.-onScrollHandler this)))
   :component-will-unmount
   (fn [{:keys [this]}]
     (.removeEventListener js/window "scroll" (.-onScrollHandler this)))})
