(ns broadfcui.components.entity-details
  (:require
   [dmohs.react :as react]
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.common.codemirror :refer [CodeMirror]]
   [broadfcui.common.icons :as icons]
   [broadfcui.common.links :as links]
   [broadfcui.common.style :as style]
   [broadfcui.utils :as utils]
   ))

(react/defc EntityDetails
  {:get-fields
   (fn [{:keys [props refs]}]
     (let [repo (:sourceRepo (:entity props))]
       (case repo
         "agora" {"methodVersion" (int (common/get-trimmed-text refs "snapshotId"))}
         "dockstore" {"methodVersion" (common/get-trimmed-text refs "methodVersion")})))
   :clear-redacted-snapshot
   (fn [{:keys [state]}]
     (swap! state dissoc :redacted-snapshot))
   :get-initial-state
   (fn [{:keys [props]}]
     (when (:redacted? props) {:redacted-snapshot (get-in props [:entity :snapshotId])}))
   :render
   (fn [{:keys [props state this]}]
     [:div {} (when-let [wdl-parse-error (:wdl-parse-error props)] (style/create-server-error-message wdl-parse-error))
      (let [{:keys [entity redacted?]} props
            config? (contains? entity :method)]
        [:div {:style {:backgroundColor (:background-light style/colors)
                       :borderRadius 8 :border style/standard-line
                       :padding "1rem"}}
         (this :render-details entity)
         (when-not redacted?
           [:div {:style {:paddingTop "0.5rem"}}
            [:span {:style {:fontWeight 500 :marginRight "1rem"}} (if config? "Referenced Method:" "WDL:")]
            (links/create-internal {:onClick #(swap! state update :payload-expanded not)}
              (if (:payload-expanded @state) "Collapse" "Expand"))
            (when (:payload-expanded @state)
              (if config?
                [:div {:style {:margin "0.5rem 0 0 1rem"}}
                 (this :render-details (:method entity))
                 [:div {:style {:fontWeight 500 :marginTop "1rem"}} "WDL:"]
                 [CodeMirror {:text (get-in entity [:method :payload])}]]
                [CodeMirror {:text (:payload entity)}]))])])])
   :render-details
   (fn [{:keys [props refs state]} entity]
     (let [{:keys [editing? redacted?]} props
           {:keys [redacted-snapshot]} @state
           repo (:sourceRepo entity)
           make-field
           (fn [key label & {:keys [dropdown? wrap? render width]}]
             [:div {:style {:display "flex" :alignItems "baseline" :paddingBottom "0.25rem"}}
              [:div {:style {:paddingRight "0.5rem" :text-align "right" :flex (str "0 0 " (or width "100px")) :fontWeight 500}} (str label ":")]
              [:div {:style {:flex "1 1 auto" :overflow "hidden" :textOverflow "ellipsis"
                             :whiteSpace (when-not wrap? "nowrap")}}
               (if (and editing? dropdown?)
                 (style/create-identity-select-name {:ref key
                                                     :data-test-id "edit-method-config-snapshot-id-select"
                                                     :style {:width 120}
                                                     :defaultValue (if redacted-snapshot -1 (key entity))
                                                     :onChange (when-let [f (:onSnapshotIdChange props)]
                                                                 (case repo
                                                                   ;; this is silly, but I cannot use let because react prohibits accessing dom nodes in render
                                                                   "agora" #(f (int (common/get-trimmed-text refs "snapshotId")))
                                                                   "dockstore" #(f (common/get-trimmed-text refs "methodVersion"))))}
                                                    (:snapshots props)
                                                    redacted-snapshot)
                 (let [rendered ((or render identity) (key entity))]
                   [:span {:title rendered :data-test-id (str "method-label-" label)} rendered]))]])]
       [:div {}
        [:div {:style {:display "flex"}}
         [:div {:style {:flex "1 1 40%" :paddingRight "0.5rem"}}
          (when redacted?
            [:div {:style {:fontWeight 500 :paddingBottom "0.25rem"} :data-test-id "snapshot-redacted-title"}
             (icons/render-icon {:style {:color (:state-warning style/colors)}} :warning) " Snapshot Redacted"])
          (case repo
            "agora" [:div {} (make-field :namespace "Namespace")
                     (make-field :name "Name")
                     (make-field :snapshotId "Snapshot ID" :dropdown? true)]
            "dockstore" [:div {} (make-field :methodPath "Path")
                         (make-field :methodVersion "Version" :dropdown? true)])
          (make-field :entityType "Entity Type")
          (make-field :repoLabel "Source")]
         (when-not (or redacted? (= repo "dockstore"))
           [:div {:style {:flex "1 1 60%" :overflow "hidden"}}
            (make-field :createDate "Created" :render common/format-date :width "150px")
            (make-field :managers "Owners" :render (partial clojure.string/join ", ") :wrap? true :width "150px")
            (make-field :synopsis "Synopsis" :width "150px")
            (make-field :snapshotComment "Snapshot Comment" :wrap? true :width "150px")])]
        (when-not (or redacted? (= repo "dockstore"))
          [:div {:style {:fontWeight 500 :padding "0.5rem 0 0.3rem 0"}}
           "Documentation:"
           (if (string/blank? (:documentation entity))
             [:div {:style {:fontStyle "italic" :fontSize "90%"}} "No documentation provided"]
             [:div {:style {:fontSize "90%"}} (:documentation entity)])])]))})
