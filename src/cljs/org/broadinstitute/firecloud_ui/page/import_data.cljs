(ns org.broadinstitute.firecloud-ui.page.import-data
  (:require
    [clojure.string]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
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
  (swap! state assoc :message "")
  (utils/ajax-orch
    (str "/workspaces/" workspaceNamespace "/" workspaceName "/importEntities")
    {:method :post
     :encType "multipart/form-data"
     :on-done #(swap! state assoc :entities-loaded? true :message (-> (:xhr %) .-responseText))
     :data (utils/generate-form-data {:entities entityFile})}))


(react/defc Page
  {:did-load-data?
   (fn [{:keys [state]}]
     (:entities-loaded? @state))
   :render
   (fn [{:keys [props state refs]}]
     [:div {:style {:marginTop "1em"}}
      [:div {:style {:boxSizing "inherit" }}
       [:input {:type "file" :name "entities" :ref "entities"}]
       [comps/Button {:text "Upload"
                      :onClick (fn [e] (submit-entities
                                         state
                                         (get-in props [:workspace-id :name])
                                         (get-in props [:workspace-id :namespace])
                                         (-> (@refs "entities") .getDOMNode .-files (aget 0))))}]]

      (if (:entities-loaded? @state)
        [:div {:style {:paddingTop "22px"}}
         [:div {:style {:fontSize "125%" :fontWeight 500}} "Import Results" [:br]
          [:div {:style {:fontSize "75%" :fontWeight 100}} (:message @state)]]])])})
