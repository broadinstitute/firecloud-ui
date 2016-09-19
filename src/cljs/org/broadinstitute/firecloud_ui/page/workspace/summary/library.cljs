(ns org.broadinstitute.firecloud-ui.page.workspace.summary.library
  (:require
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.utils :as utils]))


(defn- calculate-display-properties [library-schema]
  (filter (comp not :hidden #(get-in library-schema [:properties %]))
          (:propertyOrder library-schema)))

(defn- count-required-not-hidden [library-schema]
  (filter (comp not :hidden #(get-in library-schema [:properties %]))
          (:required library-schema)))

(defn- split-fold [library-schema display-properties]
  (let [required-count (count-required-not-hidden library-schema)
        above-fold-count (if (< required-count 5) required-count 5)]
    (split-at above-fold-count display-properties)))

(defn- render-property [library-schema property-key]
  [:div {:style {:display "flex" :padding "0.5em 0" :borderBottom (str "2px solid " (:line-gray style/colors))}}
   [:div {:style {:flexBasis "33%" :fontWeight "bold" :paddingRight "2em"}}
    (get-in library-schema [:properties property-key :title])]
   [:div {:style {:flexBasis "67%"}}
    "TODO"]])

(react/defc LibraryAttributeViewer
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [workspace library-schema]} props
           display-properties (calculate-display-properties library-schema)
           [above-fold below-fold] (split-fold library-schema display-properties)
           below-fold (not-empty below-fold)
           library-attributes (->> (workspace "attributes")
                                   (keep (fn [[k v]] (when (.startsWith k "library:") [(keyword (subs k 8)) v])))
                                   (into {})
                                   not-empty)]
       [:div {}
        (style/create-section-header "Dataset Attributes")
        (style/create-paragraph
          (if library-attributes
            [:div {}
             (map (partial render-property library-schema) above-fold)
             (when below-fold
               [:div {}
                (when (:expanded? @state)
                  (map (partial render-property library-schema) below-fold))
                [:div {:style {:marginTop "0.5em"}}
                 (style/create-link {:text (if (:expanded? @state) "Collapse" "See more attributes")
                                     :onClick #(swap! state update :expanded? not)})]])]
            [:div {:style {:fontStyle "italic"}} "No attributes provided"]))]))})
