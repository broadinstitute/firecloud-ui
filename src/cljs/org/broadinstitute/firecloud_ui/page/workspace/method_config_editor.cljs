(ns org.broadinstitute.firecloud-ui.page.workspace.method-config-editor
  (:require
    [dmohs.react :as react]
    [clojure.set :refer [union]]
    [clojure.string :refer [join trim blank?]]
    [org.broadinstitute.firecloud-ui.common :as common :refer [clear-both]]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.paths :as paths]
    [org.broadinstitute.firecloud-ui.common.table :as table]
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

(defn- complete [state props new-config]
  (swap! state assoc :loaded-config new-config :blocker nil)
  ((:onCommit props) new-config))

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
    (utils/call-ajax-orch (paths/update-method-config-path workspace config)
      {:method :PUT
       :data (utils/->json-string new-conf)
       :headers {"Content-Type" "application/json"} ;; TODO - make endpoint take text/plain
       :on-success #(if (= name (config "name"))
                     (complete state props new-conf)
                     (utils/call-ajax-orch (paths/rename-method-config-path workspace config)
                       {:method :post
                        :data (utils/->json-string (select-keys new-conf ["name" "namespace" "workspaceName"]))
                        :headers {"Content-Type" "application/json"} ;; TODO - make unified call in orchestration
                        :on-success (fn [] (complete state props new-conf))
                        :on-failure (fn [{:keys [status-text]}] (js/alert (str "Exception:\n" status-text)))}))
       :on-failure (fn [{:keys [status-text]}] (js/alert (str "Exception:\n" status-text)))})))

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
                                             (let [emap (group-by (fn [e] (e "entityType")) parsed-response)]
                                               (swap! state assoc :blocker nil :submitting? true
                                                 :entity-map emap :entities (get emap (first (keys emap))))))
                               :on-failure (fn [{:keys [status-text]}]
                                             (swap! state assoc :blocker nil)
                                             (js/alert (str "Error: " status-text)))
                               :mock-data [{"name" "Mock Sample" "entityType" "Sample"}
                                           {"name" "Mock Participant" "entityType" "Participant"}]})))}
       "Launch Analysis"]])])

(defn- render-launch-overlay [state refs workspace config]
  [comps/ModalDialog
   {:show-when (:submitting? @state)
    :dismiss-self #(swap! state assoc :submitting? false)
    :width "80%"
    :content
    (react/create-element
      [:div {}
       [:div {:style {:backgroundColor "#fff"
                      :borderBottom (str "1px solid " (:line-gray style/colors))
                      :padding "20px 48px 18px"
                      :fontSize "137%" :fontWeight 400 :lineHeight 1}}
        "Select Entity"]
       [:div {:style {:position "absolute" :top 4 :right 4}}
        [comps/Button {:icon :x :onClick #(swap! state assoc :submitting? false)}]]
       [:div {:ref "launchContainer" :style {:padding "22px 48px 40px" :backgroundColor (:background-gray style/colors)}}
        (style/create-form-label "Select Entity Type")
        (style/create-select
          {:style {:width "50%" :minWidth 50 :maxWidth 200} :ref "filter"
           :onChange #(let [value (-> (@refs "filter") .getDOMNode .-value)]
                       (swap! state assoc :entities (get-in @state [:entity-map value])))}
          (keys (:entity-map @state)))
        (style/create-form-label "Select Entity")
        (if (zero? (count (:entities @state)))
          (style/create-message-well "No entities to display.")
          [:div {:style {:backgroundColor "#fff" :border (str "1px solid " (:line-gray style/colors))
                         :padding "1em" :marginBottom "0.5em"}}
           (let [attribute-keys (apply union (map (fn [e] (set (keys (e "attributes")))) (:entities @state)))]
             [table/Table
              {:key (get-id)
               :columns (concat
                          [{:header "" :starting-width 40 :resizable? false
                            :content-renderer (fn [i data]
                                                [:input {:type "radio"
                                                         :checked (identical? data (:selected-entity @state))
                                                         :onChange #(swap! state assoc :selected-entity data)}])}
                           {:header "Entity Type" :starting-width 100 :sort-by :value}
                           {:header "Entity Name" :starting-width 100 :sort-by :value}]
                          (map (fn [k] {:header k :starting-width 100 :sort-by :value}) attribute-keys))
               :data (map (fn [m]
                            (concat
                              [m
                               (m "entityType")
                               (m "name")]
                              (map (fn [k] (get-in m ["attributes" k])) attribute-keys)))
                       (:entities @state))}])])
        (style/create-form-label "Define Expression")
        (style/create-text-field {:ref "expressionname" :defaultValue "" :placeholder "leave blank for default"})]
       [:div {:style {:fontSize "106%" :lineHeight 1 :textAlign "center"}}
        [:div {:style {:padding "0.7em 0" :cursor "pointer"
                       :backgroundColor (:button-blue style/colors)
                       :color "#fff" :borderRadius 4
                       :border (str "1px solid " (:line-gray style/colors))}

               ;; TODO: what should we show in the UI after submitting?
               ;; TODO: don't enable submit button until an entity has been selected
               ;; TODO: diable submit button after submitting
               :onClick (fn [e]
                            (let [expression (clojure.string/trim (-> (@refs "expressionname") .getDOMNode .-value))
                                  payload (merge {:methodConfigurationNamespace (config "namespace")
                                                  :methodConfigurationName (config "name")
                                                  :entityType ((:selected-entity @state) "entityType")
                                                  :entityName ((:selected-entity @state) "name")}
                                                 (when-not (blank? expression) {:expression expression}))]
                                 (utils/ajax-orch
                                   (paths/submit-method-path workspace)
                                   {:method :post
                                    :data (utils/->json-string payload)
                                    :headers{"Content-Type" "application/json"}
                                    :on-done (fn [{:keys [success? xhr]}]
                                                 ;; TODO total hack below for UI ...
                                                 (if success?
                                                   (set! (-> (@refs "launchContainer") .getDOMNode .-innerHTML) (.-responseText xhr))
                                                   (do
                                                     (set! (-> (@refs "launchContainer") .getDOMNode .-style .-backgroundColor) (:exception-red style/colors))
                                                     (set! (-> (@refs "launchContainer") .getDOMNode .-innerHTML) (.-responseText xhr)))))
                                    :canned-response {:responseText (utils/->json-string
                                                                      [{"workspaceName" {"namespace" "broad-dsde-dev",
                                                                                         "name" "alexb_test_submission"},
                                                                        "methodConfigurationNamespace" "my_test_configs",
                                                                        "submissionDate" "2015-08-18T150715.393Z",
                                                                        "methodConfigurationName" "test_config2",
                                                                        "submissionId" "62363984-7b85-4f27-b9c6-7577561f1326",
                                                                        "notstarted" [],
                                                                        "workflows" [{"messages" [],
                                                                                      "workspaceName" {"namespace" "broad-dsde-dev",
                                                                                                       "name" "alexb_test_submission"},
                                                                                      "statusLastChangedDate" "2015-08-18T150715.393Z",
                                                                                      "workflowEntity" {"entityType" "sample",
                                                                                                        "entityName" "sample_01"},
                                                                                      "status" "Submitted",
                                                                                      "workflowId" "70521329-88fe-4288-9325-2e6183e0a9dc"}],
                                                                        "status" "Submitted",
                                                                        "submissionEntity" {"entityType" "sample",
                                                                                            "entityName" "sample_01"},
                                                                        "submitter" "davidan@broadinstitute.org"}])
                                                      :status 200 :delay-ms (rand-int 1000)}})
                                 ))} "Launch"]]
       ])}])

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
          (vals (config "prerequisites"))))
      [:div {:style {:display (when-not editing? "none")}}
       [comps/Button {:style :add :text "Add new"
                      :onClick #(let [list (capture-prerequisites state refs)]
                                 (swap! state assoc :prereqs-list (conj list "")))}]]])])

(defn- render-display [state refs config editing? props]
  [:div {}
   [comps/Blocker {:banner (:blocker @state)}]
   (render-launch-overlay state refs  (:workspace props) config)
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
   (fn [{:keys [props]}]
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
