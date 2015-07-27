(ns org.broadinstitute.firecloud-ui.page.workspace.method-config-editor
  (:require
    [dmohs.react :as react]
    [clojure.string :refer [join trim blank?]]
    [org.broadinstitute.firecloud-ui.common :refer [clear-both]]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
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
         (fn [i] (-> (@refs (str "pre_" i)) .getDOMNode .-value))
         (range (count (:prereqs-list @state))))))

(defn- filter-empty [list]
  (vec (filter #(not (blank? %)) (map #(trim %) list))))


(defn- create-section-header [text]
  [:div {:style {:fontSize "125%" :fontWeight 500}} text])

(defn- create-section [& children]
  [:div {:style {:padding "1em 0 2em 0"}} children])

(defn- stop-editing [state refs]
  (swap! state assoc :editing? false :prereqs-list (filter-empty (capture-prerequisites state refs))))

(defn- commit [config state refs]
  (let [name (-> (@refs "confname") .getDOMNode .-value)
        inputs (zipmap
                 (keys (config "inputs"))
                 (map (fn [key] (-> (@refs (str "in_" key)) .getDOMNode .-value)) (keys (config "inputs"))))
        outputs (zipmap
                  (keys (config "outputs"))
                  (map (fn [key] (-> (@refs (str "out_" key)) .getDOMNode .-value)) (keys (config "outputs"))))
        prereqs (filter-empty (capture-prerequisites state refs))]
    (js/console.log (str "TODO: make ajax call to set name = " name "\ninputs = " (utils/->json-string inputs)
                      "\noutputs = " (utils/->json-string outputs) "\nprereqs = " (join ", " prereqs)))))

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

(defn- render-side-bar [state refs config editing?]
  (style/create-unselectable :div {}
    [:div {:style {:float "left" :display "inline-block" :width 290 :marginRight 40
                   :fontSize "106%" :lineHeight 1 :textAlign "center" :cursor "pointer"}}

     [:div {:style {:display (when editing? "none") :padding "0.7em 0em"
                    :backgroundColor "transparent" :color (:button-blue style/colors)
                    :border (str "1px solid " (:line-gray style/colors))}
            :onClick #(swap! state assoc :editing? true :prereqs-list (config "prerequisites"))}
      [:span {:style {:display "inline-block" :verticalAlign "middle"}}
       (icons/font-icon {:style {:fontSize "135%"}} :pencil)]
      [:span {:style {:marginLeft "1em"}} "Edit this page"]]

     [:div {:style {:display (when-not editing? "none") :padding "0.7em 0em"
                    :backgroundColor (:success-green style/colors) :color "#fff" :borderRadius 4}
            :onClick #(do (commit config state refs) (stop-editing state refs))}
      [:span {:style {:display "inline-block" :verticalAlign "middle"}}
       (icons/font-icon {:style {:fontSize "135%"}} :status-done)]
      [:span {:style {:marginLeft "1em"}} "Save"]]

     [:div {:style {:display (when-not editing? "none") :padding "0.7em 0em" :marginTop "0.5em"
                    :backgroundColor (:exception-red style/colors) :color "#fff" :borderRadius 4}
            :onClick #(stop-editing state refs)}
      [:span {:style {:display "inline-block" :verticalAlign "middle"}}
       (icons/font-icon {:style {:fontSize "135%"}} :x)]
      [:span {:style {:marginLeft "1em"}} "Cancel Editing"]]]))

(defn- render-main-display [state refs config editing?]
  [:div {:style {:float "left" :display "inline-block"}}
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

(defn- render-display [state refs]
  (let [config (:config @state)
        editing? (:editing? @state)]
    [:div {:style {:padding "0em 2em"}}
     (render-top-bar config)
     [:div {:style {:padding "1em 0em"}}
      (render-side-bar state refs config editing?)
      (render-main-display state refs config editing?)
      (clear-both)]]))

(react/defc MethodConfigEditor
  {:get-initial-state
   (fn [{:keys [props]}]
     {:config (:config props)
      :editing? false})
   :render
   (fn [{:keys [state refs]}]
     (render-display state refs))})
