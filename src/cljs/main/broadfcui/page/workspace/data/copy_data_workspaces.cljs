(ns broadfcui.page.workspace.data.copy-data-workspaces
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.style :as style]
   [broadfcui.endpoints :as endpoints]
   [broadfcui.page.workspace.data.copy-data-entities :as copy-data-entities]
   [broadfcui.page.workspace.workspace-common :as ws-common]
   [broadfcui.utils :as utils]
   ))


(defn- remove-self [workspace-id workspace-list]
  (remove (comp (partial = workspace-id) ws-common/workspace->id) workspace-list))

(defn- filter-workspaces [this-auth-domain workspace-list]
  (filter #(let [src-auth-domain (set (map :membersGroupName (get-in % [:workspace :authorizationDomain])))]
             (and
              (or (empty? src-auth-domain) (clojure.set/subset? src-auth-domain this-auth-domain))
              (not= (:accessLevel %) "NO ACCESS")))
          workspace-list))

(react/defc Page
  {:render
   (fn [{:keys [state props]}]
     (let [selected-workspace (:selected-workspace (first (:crumbs props)))]
       (cond
         selected-workspace
         [copy-data-entities/SelectType
          (merge (select-keys props [:workspace-id :add-crumb :on-data-imported])
                 {:selected-workspace-id (ws-common/workspace->id selected-workspace)
                  :selected-workspace-bucket (get-in selected-workspace [:workspace :bucketName])
                  :crumbs (rest (:crumbs props))})]
         (:workspaces @state)
         [:div {:style {:margin "1em"}}
          (ws-common/workspace-selector
           {:workspaces (:workspaces @state)
            :on-workspace-selected (fn [ws]
                                     ((:add-crumb props)
                                      {:text (str (get-in ws [:workspace :namespace]) "/"
                                                  (get-in ws [:workspace :name]))
                                       :onClick #((:pop-to-depth props) 3)
                                       :selected-workspace ws}))
            :get-toolbar-items (constantly
                                (when-let [num-filtered (:num-filtered @state)]
                                  (when (pos? num-filtered)
                                    [(str num-filtered
                                          " workspace"
                                          (when (> num-filtered 1) "s")
                                          " unavailable because "
                                          (if (= num-filtered 1) "it contains" "they contain")
                                          " data from other authorization domains.")])))})]
         (:error-message @state) (style/create-server-error-message (:error-message @state))
         :else [:div {:style {:textAlign "center"}}
                [comps/Spinner {:text "Loading workspaces..."}]])))
   :component-did-mount
   (fn [{:keys [state props]}]
     (endpoints/call-ajax-orch
      {:endpoint endpoints/list-workspaces
       :on-done (fn [{:keys [success? status-text get-parsed-response]}]
                  (if success?
                    (let [all-workspaces (remove-self (:workspace-id props) (get-parsed-response))
                          filtered-workspaces (filter-workspaces (set (map :membersGroupName (:this-auth-domain props))) all-workspaces)]
                      (swap! state assoc :workspaces filtered-workspaces
                             :num-filtered (- (count all-workspaces) (count filtered-workspaces))))
                    (swap! state assoc :error-message status-text)))}))})
