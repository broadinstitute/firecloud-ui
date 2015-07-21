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
    (str "/workspaces/" workspaceNamespace "/" workspaceName "/importEntitiesJSON")
    {:method :post
     :encType "multipart/form-data"
     :on-done #(swap! state assoc :entities-loaded? true :uploadResults (utils/parse-json-string (-> (:xhr %) .-responseText)))
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
                                         state
                                         (-> (@refs "workspaceName") .getDOMNode .-value)
                                         (-> (@refs "workspaceNamespace") .getDOMNode .-value)
                                         (-> (@refs "entities") .getDOMNode .-files (aget 0))))}]]

      (if (:entities-loaded? @state)
        [:div {}
         [:div {:style {:fontSize "125%" :fontWeight 500}} "Import results"]
         [table/Table
          (let [cell-style {:flexBasis "8ex" :flexGrow 1 :whiteSpace "nowrap" :overflow "hidden"
                            :borderLeft (str "1px solid " (:line-gray style/colors))}
                header-label (fn [text & [padding]]
                               [:span {:style {:paddingLeft (or padding "1em")}}
                                [:span {:style {:fontSize "90%"}} text]])]
            {:columns [{:label (header-label "Entity Name")
                        :style cell-style
                        :header-style {:borderLeft 0}}
                       {:label (header-label "Entity Type")
                        :style cell-style
                        :header-style {:borderLeft 0}}
                       {:label (header-label "Success")
                        :style cell-style
                        :header-style {:borderLeft 0}}
                       {:label (header-label "Message")
                        :style cell-style
                        :header-style {:borderLeft 0}}]
             :data (map (fn [entity]
                          [(entity "entityName")
                           (entity "entityType")
                           (str (entity "succeeded"))
                           (entity "message")])
                     (:uploadResults @state))})]])])})
