(ns org.broadinstitute.firecloud-ui.page.workspace.method-config-editor
  (:require
    [dmohs.react :as react]
    [clojure.string :refer [trim blank?]]
    [org.broadinstitute.firecloud-ui.common :as common :refer [clear-both]]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.page.workspace.launch-analysis :as launch]
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
  (let [workspace-id (:workspace-id props)
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
    (endpoints/call-ajax-orch
      {:endpoint (endpoints/update-workspace-method-config workspace-id config)
       :payload new-conf
       :headers {"Content-Type" "application/json"} ;; TODO - make endpoint take text/plain
       :on-done (fn [{:keys [success? xhr]}]
                  (if-not success?
                    (js/alert (str "Exception:\n" (.-statusText xhr)))
                    (if (= name (config "name"))
                      (complete state new-conf)
                      (endpoints/call-ajax-orch ;; TODO - make unified call in orchestration
                        {:endpoint (endpoints/rename-workspace-method-config workspace-id config)
                         :payload (select-keys new-conf ["name" "namespace" "workspaceName"])
                         :headers {"Content-Type" "application/json"}
                         :on-done (fn [{:keys [success? xhr]}]
                                    (complete state new-conf)
                                    (when-not success?
                                      (js/alert (str "Exception:\n" (.-statusText xhr)))))}))))})))

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

(react/defc DeleteButton
  {:rm-mc (fn [{:keys [props state]}]
            (endpoints/call-ajax-orch
              {:endpoint (endpoints/delete-workspace-method-config (:workspace-id props) (:config props))
               :on-done (fn [{:keys [success? xhr]}]
                          (swap! state dissoc :deleting?)
                          (if success?
                            ((:on-rm props))
                            (js/alert (str "Error during deletion: " (.-statusText xhr)))))}))
   :render (fn [{:keys [this state]}]
             (when (:deleting? @state)
               [comps/Blocker {:banner (str "Deleting...")}])
             [comps/SidebarButton {:style :light :color :exception-red :margin :top
                                   :text "Delete" :icon :trash-can
                                   :onClick #(when (js/confirm "Are you sure?")
                                              (swap! state assoc :deleting? true)
                                              (react/call :rm-mc this))}])})

(defn- clear-overlay [state refs]
  (swap! state dissoc :publishing?))

(defn- render-modal-publish [state refs props]
  (react/create-element
    [:div {}
     (when (:creating-wf @state)
       [comps/Blocker {:banner "Publishing Method Configuration..."}])
     [:div {:style {:backgroundColor "#fff"
                    :borderBottom (str "1px solid " (:line-gray style/colors))
                    :padding "20px 48px 18px"
                    :fontSize "137%" :fontWeight 400 :lineHeight 1}}
      "Publish Method Configuration"]
     [:div {:style {:padding "22px 48px 40px" :backgroundColor (:background-gray style/colors)}}
      (style/create-form-label "Method Configuration Namespace")
      (style/create-text-field {:style {:width "100%"} :ref "mcNamespace"
                                :defaultValue (get-in (:config props) ["namespace"])})
      (style/create-form-label "Method Configuration Name")
      (style/create-text-field {:style {:width "100%"} :ref "mcName"
                                :defaultValue (get-in (:config props) ["name"])})
      [:div {:style {:marginTop 40 :textAlign "center"}}
       [:a {:style {:marginRight 27 :marginTop 2 :padding "0.5em"
                    :display "inline-block"
                    :fontSize "106%" :fontWeight 500 :textDecoration "none"
                    :color (:button-blue style/colors)}
            :href "javascript:;"
            :onClick #(clear-overlay state refs)
            :onKeyDown (common/create-key-handler [:space :enter] #(clear-overlay state refs))}
        "Cancel"]
       [comps/Button {:text "Publish" :ref "publishButton"
                      :onClick
                            #(let [ns (-> (@refs "mcNamespace") .getDOMNode .-value clojure.string/trim)
                                   n (-> (@refs "mcName") .getDOMNode .-value clojure.string/trim)]
                              (when-not (or (empty? ns) (empty? n))
                                (swap! state assoc :creating-wf true)
                                (endpoints/call-ajax-orch
                                  {:endpoint (endpoints/copy-method-config-to-repo (:workspace-id props) (:config props))
                                   :headers {"Content-Type" "application/json"}
                                   :payload  {:configurationNamespace ns,
                                              :configurationName n,
                                              :sourceNamespace (get-in (:config props) ["namespace"]),
                                              :sourceName (get-in (:config props) ["name"])}
                                   :on-done  (fn [{:keys [success?]}]
                                               (swap! state dissoc :creating-wf)
                                               (if success?
                                                 (do (clear-overlay state refs))
                                                 (js/alert "Method Configuration Publish Failed.")))})))}]]]]))

(react/defc PublishButton
  {:render (fn [{:keys [props state refs]}]
             [:div {}
              (when (:publishing? @state)
                [comps/Dialog
                 {:width 500
                  :dismiss-self #(swap! state dissoc :publishing?)
                  :content (render-modal-publish state refs props)}])
              [comps/SidebarButton {:style   :light :color :button-blue :margin :top
                                    :text    "Publish" :icon :share
                                    :onClick #(swap! state assoc :publishing? true)}]])})


(defn- render-side-bar [state refs config editing? props]
  [:div {:style {:width 290 :float "left"}}
   [:div {:ref "sidebar"}]
   (style/create-unselectable :div {:style {:position (when-not (:sidebar-visible? @state) "fixed")
                                            :top (when-not (:sidebar-visible? @state) 4)
                                            :width 290}}
     [:div {:style {:lineHeight 1}}
      (when-not editing?
        [comps/SidebarButton {:style :light :color :button-blue
                              :text "Edit this page" :icon :pencil
                              :onClick #(swap! state assoc :editing? true
                                         :prereqs-list (vals (config "prerequisites")))}])
      (when-not editing?
        [DeleteButton
         {:workspace-id (:workspace-id props)
          :on-rm (:on-rm props)
          :config config}])

      (when-not editing?
                [PublishButton
                 {:workspace-id (:workspace-id props)
                  :on-publish (:on-publish props)
                  :config config}])

      (when editing?
        [comps/SidebarButton {:color :success-green
                              :text "Save" :icon :status-done
                              :onClick #(do (commit state refs config props) (stop-editing state refs))}])
      (when editing?
        [comps/SidebarButton {:color :exception-red :margin :top
                              :text "Cancel Editing" :icon :x
                              :onClick #(stop-editing state refs)}])])])


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
   [:div {:style {:padding "0em 2em"}}
    (render-top-bar config)
    [:div {:style {:padding "1em 0em"}}
     (render-side-bar state refs config editing? props)
     (when-not editing?
       [:div {:style {:float "right"}}
        (launch/render-button {:workspace-id (:workspace-id props)
                               :config-id {:namespace (config "namespace") :name (config "name")}
                               :root-entity-type (config "rootEntityType")
                               :on-success (:on-submission-success props)})])
     (render-main-display state refs config editing?)
     (clear-both)]]])

(react/defc MethodConfigEditor
  {:get-initial-state
   (fn []
     {:editing? false
      :sidebar-visible? true})
   :render
   (fn [{:keys [state refs props]}]
     (cond (:loaded-config @state)
           (render-display state refs (:loaded-config @state) (:editing? @state) props)
           (:error @state) (style/create-server-error-message (:error @state))
           :else [comps/Spinner {:text "Loading Method Configuration..."}]))
   :component-did-mount
   (fn [{:keys [state props refs this]}]
     (endpoints/call-ajax-orch
       {:endpoint (endpoints/get-workspace-method-config (:workspace-id props) (:config props))
        :on-done (fn [{:keys [success? get-parsed-response status-text]}]
                   (if success?
                     (swap! state assoc :loaded-config (get-parsed-response))
                     (swap! state assoc :error status-text)))})
     (set! (.-onScrollHandler this)
           (fn []
             (when-let [sidebar (@refs "sidebar")]
               (let [visible (common/is-in-view (.getDOMNode sidebar))]
                 (when-not (= visible (:sidebar-visible? @state))
                   (swap! state assoc :sidebar-visible? visible))))))
     (.addEventListener js/window "scroll" (.-onScrollHandler this)))
   :component-will-unmount
   (fn [{:keys [this]}]
     (.removeEventListener js/window "scroll" (.-onScrollHandler this)))})

