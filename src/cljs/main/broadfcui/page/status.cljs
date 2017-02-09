(ns broadfcui.page.status
  (:require
   [dmohs.react :as react]
   [broadfcui.utils :as utils]
   [broadfcui.common.components :as comps]
   ))


(react/defc StatusLine
  {:render
   (fn [{:keys [props state]}]
     [:div {:style {:marginTop "0.5em"}}
      [:div {}
       (:label props) ": "
       (cond
         (nil? (:response @state))
         [comps/Spinner]
         (not (:success? (:response @state)))
         [:span {} [:span {:style {:color "red"}} "Error"]
          (when-not (:show-error-details? @state)
            [:span {} 
             " ("
             [:a {:href "javascript:;" :style {:textDecoration "none"}
                  :onClick #(swap! state assoc :show-error-details? true)}
              "show details"]
             ")"])] 
         :else
         [:span {:style {:color "green"}} "Okay"])]
      (when (:show-error-details? @state)
        (let [response (:response @state)]
          [:div {:style {:backgroundColor "#eee" :padding 6 :fontSize "smaller"}}
           [:div {} "Status code: " (:status-code response)]
           [:div {} "Status text: " (:status-text response)]
           [:div {} "Response text:" [:br] (-> (:xhr response) .-responseText)]]))])
   :component-did-mount
   (fn [{:keys [props state]}]
     (if (= (:path (:request props)) "/profile")
       (utils/ajax-orch (:path (:request props))
                        {:on-done #(swap! state assoc :response %)}
                        :service-prefix "/register")
       (utils/ajax-orch (:path (:request props))
                        {:on-done #(swap! state assoc :response %)})))})


(defn render []
  (react/create-element
   :div {:style {:padding "1em"}}
   [:h2 {} "Service Status"]
   [:div {}
    [StatusLine {:label "Orchestration" :request {:path "/status/ping"}}]
    [StatusLine {:label "Rawls" :request {:path "/workspaces"}}]
    [StatusLine {:label "Agora" :request {:path "/methods"}}]
    [StatusLine {:label "Thurloe" :request {:path "/profile"}}]]))
