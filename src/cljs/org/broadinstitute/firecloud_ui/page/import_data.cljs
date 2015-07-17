(ns org.broadinstitute.firecloud-ui.page.import-data
  (:require
    [clojure.string]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- submit-entities [workspaceName workspaceNamespace entityFile]
  (utils/ajax-orch
    (str "/workspaces/" workspaceNamespace "/" workspaceName "/importEntitiesJSON")
    {:method :post
     :encType "multipart/form-data"
     :on-done :default
     :data (utils/generate-form-data {:entities entityFile})}))


(react/defc Page
  {:render
   (fn [{:keys [state refs]}]
     [:div {}
      [:div {:style {:padding "22px 48px 40px" :boxSizing "inherit" }}
       (style/create-form-label "Workspace Name")
       (style/create-text-field {:style {:width "33%"} :name "workspaceName" :ref "workspaceName"})
       (style/create-form-label "Workspace Namespace")
       (style/create-text-field {:style {:width "33%"} :name "workspaceNamespace" :ref "workspaceNamespace"})[:br]
       [:input {:type "file" :name "entities" :ref "entities"}]
       [comps/Button {:text "Upload"
                      :onClick (fn [e] (submit-entities
                                         (-> (@refs "workspaceName") .getDOMNode .-value)
                                         (-> (@refs "workspaceNamespace") .getDOMNode .-value)
                                         (-> (@refs "entities") .getDOMNode .-files (aget 0))))}]]])})
