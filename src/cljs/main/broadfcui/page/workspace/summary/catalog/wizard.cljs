(ns broadfcui.page.workspace.summary.catalog.wizard
  (:require
    [clojure.string :refer [join split trim]]
    [dmohs.react :as react]
    [broadfcui.common.components :as comps]
    [broadfcui.common.flex-utils :as flex]
    [broadfcui.common.modal :as modal]
    [broadfcui.common.style :as style]
    [broadfcui.endpoints :as endpoints]
    [broadfcui.page.workspace.summary.catalog.questions :refer [Questions]]
    [broadfcui.page.workspace.summary.library-utils :as library-utils]
    [broadfcui.utils :as utils]
    ))

(defn- render-wizard-breadcrumbs [{:keys [library-schema page-num pages-seen]}]
  (let [pages (:wizard library-schema)]
    [:div {:style {:flex "0 0 250px" :backgroundColor "white"
                   :border style/standard-line :padding "1rem"}}
     [:div {:style {:paddingBottom "0.5rem" :fontWeight (when (< page-num (count pages)) "bold")}} "Catalog "]
     [:ul {:style {:WebkitMarginBefore 0 :WebkitMarginAfter 0}}
      (map-indexed
       (fn [index {:keys [title]}]
         (let [this (= index page-num)]
           [:li {:style {:paddingBottom "0.5rem"
                         :fontWeight (when this "bold")
                         :color (when-not (or this (contains? pages-seen index)) (:text-lighter style/colors))}}
            title])) pages)]
      [:div {:style {:paddingBottom "0.5rem"
                     :color (when-not (contains? pages-seen (count pages)) (:text-lighter style/colors))
                     :fontWeight (when (= page-num (count pages)) "bold")}}
       "Discoverability"]
      [:div {:style {:color (when-not (contains? pages-seen (+ 1 (count pages))) (:text-lighter style/colors))
                     :fontWeight (when (> page-num (count pages)) "bold")}}
       "Summary"]]))

(defn- find-required-attributes [library-schema]
  (->> (map :required (:oneOf library-schema))
       (concat (:required library-schema))
       flatten
       (map keyword)
       set))

(defn- get-questions-for-page [working-attributes library-schema page-num]
  (when (< page-num (count (:wizard library-schema)))
    (let [page-props (get-in library-schema [:wizard page-num])
          {:keys [questions enumerate optionSource options]} page-props]
      (if optionSource
        (let [option-value (get working-attributes (keyword optionSource))]
          (when-let [option-match (some->> option-value keyword (get options))]
            (map option-match [:questions :enumerate])))
        [questions enumerate]))))

(defn- convert-empty-strings [attributes]
  (utils/map-values
   (fn [val]
     (if (or (coll? val) (string? val))
       (not-empty val)
       val)) attributes))

(react/defc DiscoverabilityPage
  {:validate (constantly nil)
   :get-initial-state
   (fn [{:keys [props]}]
     (select-keys props [:library:discoverableByGroups]))
   :get-attributes
   (fn [{:keys [state]}]
     (select-keys @state [:library:discoverableByGroups]))
   :set-groups
   (fn [{:keys [state]} new-val]
     (swap! state assoc :library:discoverableByGroups new-val))
   :render
   (fn [{:keys [state props this]}]
     (let [{:keys [library:discoverableByGroups]} @state
           selected (if (empty? library:discoverableByGroups) 0 1)]
       [:div {} "Dataset should be discoverable by:"
        (map-indexed (fn [index wording]
                       [:div {:style {:display "flex" :alignItems "center"
                                      :margin "0.5rem 0" :padding "1em"
                                      :border style/standard-line :borderRadius 8
                                      :backgroundColor (when (= index selected) (:button-primary style/colors))
                                      :cursor "pointer"}
                              :onClick #(react/call :set-groups this (if (= index 0) '() '("all_broad_users")))}
                        [:input {:type "radio" :readOnly true :checked (= index selected)
                                 :style {:cursor "pointer"}}]
                        (if (= index 1)
                           (style/create-identity-select {:value "<select an option>" ;;(or current-value ENUM_EMPTY_CHOICE)
                                                          }
                                                         ;:style (colorize {}) }
                                                         ;:onChange update-property}
                                                         (cons "Limit to Group" (list (:library-groups props))))
                           [:div {:style {:marginLeft "0.75rem" :color (when (= index selected) "white")}}
                           wording])])
                     '("All users" "Limit to Group"))
        [:div {:style {:fontSize "small" :paddingTop "0.5rem" :fontStyle "italic"}}
         "N.B. The Dataset will be visible to these users in the library, but users will still
         need to acquire Read permission for the Workspace in order to view its contents."]]))})


(defn- render-summary-page [attributes library-schema invalid-attributes]
  [:div {}
   (if (not-empty invalid-attributes)
     [:div {:style {:color (:exception-state style/colors) :border (str "1px solid " (:exception-state style/colors))
                    :padding "1rem"}}
      [:div {:style {:paddingBottom "0.5rem" :marginBottom "0.5rem"
                     :borderBottom (str "1px solid " (:exception-state style/colors))}}
       "The following additional attributes are required to publish:" ]
      [:div {:style {:fontSize 14}}
       (map (fn [attribute]
              [:div {:style {:paddingBottom "0.2rem"}} (get-in library-schema [:properties (keyword attribute) :title])])
            invalid-attributes)]])

   (style/create-paragraph
    [:div {}
     (let [questions (first (get-questions-for-page (convert-empty-strings attributes) library-schema 0))]
       (map (fn [attribute]
              (if (not-empty (str (attributes (keyword attribute))))
                (library-utils/render-property library-schema attributes (keyword attribute))))
            questions))
     (if (= (:library:useLimitationOption attributes) "orsp") ;; TODO: change this so not hardcoded
       (library-utils/render-property library-schema attributes :library:orsp)
       (library-utils/render-consent-codes library-schema attributes))
     (library-utils/render-library-row "Discoverability" (if (empty? (:library:discoverableByGroups attributes)) "All users" "Broad users only"))])])



(defn- get-initial-attributes [workspace]
  (utils/map-values
   library-utils/unpack-attribute-list
   (dissoc (get-in workspace [:workspace :library-attributes]) :library:published)))


(react/defc CatalogWizard
  {:get-initial-state
   (fn [{:keys [props]}]
     (let [{:keys [library-schema]} props
           {:keys [versions]} library-schema]
       {:page-num 0
        :pages-stack []
        :pages-seen #{0}
        :invalid-properties #{}
        :working-attributes (get-initial-attributes (:workspace props))
        :published? (get-in props [:workspace :workspace :library-attributes :library:published])
        :version-attributes (->> versions
                                 (map keyword)
                                 (map (fn [version]
                                        [version (get-in library-schema [:properties version :default])]))
                                 (into {}))
        :required-attributes (find-required-attributes library-schema)}))
   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [library-schema library-groups]} props
           {:keys [page-num pages-seen invalid-properties working-attributes published? required-attributes validation-error submit-error]} @state]
       [:div {}
        (when (:submitting? @state)
          [comps/Blocker {:banner "Submitting..."}])
        [:div {:style {:borderBottom style/standard-line
                       :padding "20px 48px 18px"
                       :fontSize "137%" :fontWeight 400 :lineHeight 1}}
         "Catalog Dataset"
         [comps/XButton {:dismiss modal/pop-modal}]]
        [:div {:style {:padding "22px 24px 40px" :backgroundColor (:background-light style/colors)}}
         [:div {:style {:display "flex" :width 850 :height 400}}
          (render-wizard-breadcrumbs {:library-schema library-schema :page-num page-num :pages-seen pages-seen})
          [comps/ScrollFader
           {:ref "scroller"
            :outer-style {:flex "1 1 auto"
                          :border style/standard-line :boxSizing "border-box"
                          :backgroundColor "white"}
            :inner-style {:padding "1rem" :boxSizing "border-box" :height "100%"}
            :content
            (react/create-element
             (let [page-count (count (:wizard library-schema))
                   [questions enumerate] (get-questions-for-page working-attributes library-schema page-num)]
               (cond
                 (< page-num page-count)
                 [Questions {:ref "wizard-page" :key page-num
                             :library-schema library-schema
                             :missing-properties invalid-properties
                             :enumerate enumerate
                             :questions questions
                             :attributes working-attributes
                             :required-attributes required-attributes}]
                 (= page-num page-count)
                 [DiscoverabilityPage {:ref "wizard-page"
                                       :library:discoverableByGroups (:library:discoverableByGroups working-attributes)
                                       :library-groups library-groups}]
                 (> page-num page-count) (render-summary-page working-attributes library-schema invalid-properties))))}]]
         (when validation-error
           [:div {:style {:marginTop "1em" :color (:exception-state style/colors) :textAlign "center"}}
            validation-error])
         [comps/ErrorViewer {:error submit-error}]
         (flex/flex-box {:style {:marginTop 40}}
                        (flex/flex-strut 80)
                        flex/flex-spacer
                        [comps/Button {:text "Previous"
                                       :onClick (fn [_]
                                                  (if-let [prev-page (peek (:pages-stack @state))]
                                                    (swap! state #(-> %
                                                                      (assoc :page-num prev-page)
                                                                      (update :pages-stack pop)
                                                                      (dissoc :validation-error)))))
                                       :style {:width 80}
                                       :disabled? (zero? page-num)}]
                        (flex/flex-strut 27)
                        [comps/Button {:text "Next"
                                       :onClick #(react/call :next-page this)
                                       :disabled? (> page-num (-> library-schema :wizard count))
                                       :style {:width 80}}]
                        flex/flex-spacer
                        [comps/Button {:text (if published? "Republish" "Submit")
                                       :onClick #(react/call :submit this)
                                       :disabled? (not (> page-num (-> library-schema :wizard count)))
                                       :style {:width 80}}])]]))
   :component-did-mount
   (fn [{:keys [locals]}]
     (swap! locals assoc :page-attributes []))
   :component-did-update
   (fn [{:keys [prev-state state refs]}]
     (when (not= (:page-num prev-state) (:page-num @state))
       (react/call :scroll-to (@refs "scroller") 0)))
   :next-page
   (fn [{:keys [state refs this locals after-update props]}]
     (swap! state dissoc :validation-error)
     (if-let [error-message (react/call :validate (@refs "wizard-page"))]
       (swap! state assoc :validation-error error-message)
       (let [{:keys [working-attributes pages-seen pages-stack required-attributes page-num]} @state
             attributes-from-page (react/call :get-attributes (@refs "wizard-page"))
             all-attributes (merge working-attributes attributes-from-page)
             invalid-attributes (atom #{})]
         (swap! state assoc :working-attributes all-attributes)
         (swap! locals update :page-attributes assoc page-num attributes-from-page)
         (doseq [page (conj pages-stack page-num)]
           (let [[questions _] (get-questions-for-page all-attributes (:library-schema props) page)
                 {:keys [invalid]} (library-utils/validate-required (convert-empty-strings all-attributes)
                                                                    questions required-attributes)]
             (reset! invalid-attributes (clojure.set/union invalid @invalid-attributes))))
         (swap! state assoc :invalid-properties @invalid-attributes)
         (after-update (fn [_]
                         (let [next-page (react/call :find-next-page this)]
                           (swap! state #(-> %
                                             (update :pages-seen conj next-page)
                                             (update :pages-stack conj page-num)
                                             (assoc :page-num next-page)))))))))
   :find-next-page
   (fn [{:keys [props state]}]
     (let [{:keys [library-schema]} props
           {:keys [page-num working-attributes]} @state
           next (atom (inc page-num))]
       (while (and (not (get-questions-for-page working-attributes library-schema @next))
                   (< @next (count (:wizard library-schema))))
         (swap! next inc))
       @next))
   :submit
   (fn [{:keys [props state locals]}]
     (if (not-empty (:invalid-properties @state))
       (swap! state assoc :validation-error "You will need to complete all required metadata attributes to be able to publish the workspace in the Data Library")
       (let [attributes-seen (apply merge (replace (:page-attributes @locals) (:pages-seen @state)))]
         (swap! state assoc :submitting? true :submit-error nil)
         (endpoints/call-ajax-orch
          {:endpoint (endpoints/save-library-metadata (:workspace-id props))
           :payload  (convert-empty-strings (merge attributes-seen (:version-attributes @state)))
           :headers utils/content-type=json
           :on-done (fn [{:keys [success? get-parsed-response]}]
                      (swap! state dissoc :submitting?)
                      (if success?
                        (do (modal/pop-modal)
                            ((:request-refresh props)))
                        (swap! state assoc :submit-error (get-parsed-response false))))}))))})
