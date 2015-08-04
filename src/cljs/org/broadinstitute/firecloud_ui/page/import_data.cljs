(ns org.broadinstitute.firecloud-ui.page.import-data
  (:require
    [clojure.string]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.table :as table]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- pretend-submit-entities
  "Insta-populates uploadResults with some sample JSON so you can just hit Upload. For testing purposes."
  [state workspaceName workspaceNamespace entityFile]
  (swap! state assoc
    :entities-loaded? true
    :uploadResults (utils/parse-json-string "[
        { \"entityName\" : \"foo\", \"entityType\" : \"bar\", \"succeeded\" : false, \"message\" : \"ohno\" },
        { \"entityName\" : \"bar\", \"entityType\" : \"baz\", \"succeeded\" : true, \"message\" : \"hooray\" }
      ]")))


(defn- submit-entities [state workspaceName workspaceNamespace entityFile]
  (utils/ajax-orch
    (str "/workspaces/" workspaceNamespace "/" workspaceName "/importEntities")
    {:method :post
     :encType "multipart/form-data"
     :on-done #(swap! state assoc :entities-loaded? true :message (-> (:xhr %) .-responseText))
     :data (utils/generate-form-data {:entities entityFile})}))


(react/defc Page
  {:render
   (fn [{:keys [state refs]}]
     [:div {:style {:padding "22px 48px 40px"}}
      [:div {:style {:boxSizing "inherit" }}
       (style/create-form-label "Workspace Namespace")
       (style/create-text-field {:style {:width "33%"} :name "workspaceNamespace" :ref "workspaceNamespace"})
       (style/create-form-label "Workspace Name")
       (style/create-text-field {:style {:width "33%"} :name "workspaceName" :ref "workspaceName"})[:br]
       [:input {:type "file" :name "entities" :ref "entities"}]
       [comps/Button {:text "Upload"
                      :onClick (fn [e] (submit-entities
                                         state
                                         (-> (@refs "workspaceName") .getDOMNode .-value)
                                         (-> (@refs "workspaceNamespace") .getDOMNode .-value)
                                         (-> (@refs "entities") .getDOMNode .-files (aget 0))))}]]

      (if (:entities-loaded? @state)
        [:div {:style {:paddingTop "22px"}}
         [:div {:style {:fontSize "125%" :fontWeight 500}} "Import Results" [:br]
          [:div {:style {:fontSize "75%" :fontWeight 100}} (:message @state)]]])])})
