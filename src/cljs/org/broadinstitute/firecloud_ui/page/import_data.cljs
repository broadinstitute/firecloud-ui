(ns org.broadinstitute.firecloud-ui.page.import-data
  (:require
    [clojure.string]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.components :as comps]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(defn- submit-entities [state workspace-id entityFile]
  (swap! state assoc :message "")
  (endpoints/call-ajax-orch
    {:endpoint (endpoints/import-entities workspace-id)
     :payload (utils/generate-form-data {:entities entityFile})
     :encType "multipart/form-data"
     :on-done (fn [{:keys [xhr]}]
                (swap! state assoc :entities-loaded? true :message (.-responseText xhr)))}))


(react/defc Page
  {:did-load-data?
   (fn [{:keys [state]}]
     (:entities-loaded? @state))
   :render
   (fn [{:keys [props state refs]}]
     [:div {:style {:marginTop "1em"}}
      [:div {:style {:boxSizing "inherit"}}
       [:input {:type "file" :name "entities" :ref "entities"}]
       [comps/Button {:text "Upload"
                      :onClick #(submit-entities
                                 state
                                 (:workspace-id props)
                                 (-> (@refs "entities") .getDOMNode .-files (aget 0)))}]]

      (if (:entities-loaded? @state)
        [:div {:style {:paddingTop "22px"}}
         [:div {:style {:fontSize "125%" :fontWeight 500}} "Import Results" [:br]
          [:div {:style {:fontSize "75%" :fontWeight 100}} (:message @state)]]])])})
