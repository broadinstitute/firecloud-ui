(ns broadfcui.page.workspace.summary.catalog.wizard
  (:require
   [dmohs.react :as react]
   [broadfcui.common.components :as comps]
   [broadfcui.common.flex-utils :as flex]
   [broadfcui.common.modal :as modal]
   [broadfcui.common.style :as style]
   [broadfcui.components.buttons :as buttons]
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

(def ^:private ALL_USERS "All users")

(defn- ensure-sequence [input]
  ;;input may or maynot be a sequence, make it a sequence
  (cond (sequential? input) input
        (empty? input) []
        :else [input]))

(react/defc- DiscoverabilityPage
  {:validate (constantly nil)
   :get-initial-state
   (fn [{:keys [props]}]
     {:library:discoverableByGroups (ensure-sequence (:library:discoverableByGroups props))})
   :get-attributes
   (fn [{:keys [state]}]
     (select-keys @state [:library:discoverableByGroups]))
   :set-groups
   (fn [{:keys [state]} new-val]
     (swap! state assoc :library:discoverableByGroups (if (= new-val ALL_USERS) [] [new-val])))
   :render
   (fn [{:keys [state props this]}]
     (let [{:keys [library:discoverableByGroups]} @state
           {:keys [set-discoverable?]} props
           editable? set-discoverable?
           selected (if (empty? library:discoverableByGroups) ALL_USERS (first library:discoverableByGroups))]
       [:div {} "Dataset should be discoverable by:"
        (style/create-identity-select {:value selected
                                       :disabled (not editable?)
                                       :style {:marginTop "0.5rem"}
                                       :onChange #(react/call :set-groups this (.. % -target -value))}
                                      (cons ALL_USERS (:library-groups props)))
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
       "The following additional attributes are required to publish:"]
      [:div {:style {:fontSize 14}}
       (map (fn [attribute]
              [:div {:style {:paddingBottom "0.2rem"}} (get-in library-schema [:properties (keyword attribute) :title])])
            invalid-attributes)]])
   (style/create-paragraph
    [:div {}
     (let [questions (first (library-utils/get-questions-for-page (library-utils/remove-empty-values attributes) library-schema 0))]
       (map (fn [attribute]
              (if (not-empty (str (attributes (keyword attribute))))
                (library-utils/render-property library-schema attributes (keyword attribute))))
            questions))
     (if (= (:library:useLimitationOption attributes) "orsp") ;; TODO: change this so not hardcoded
       (library-utils/render-property library-schema attributes :library:orsp)
       (library-utils/render-consent-codes library-schema attributes))
     (library-utils/render-library-row "Discoverability"
                                       (let [groups (get attributes :library:discoverableByGroups [])]
                                         (if (empty? groups) ALL_USERS (first groups))))])])

(react/defc CatalogWizard
  {:get-initial-state
   (fn [{:keys [props]}]
     (let [{:keys [library-schema]} props
           {:keys [versions]} library-schema]
       {:page-num 0
        :pages-stack []
        :pages-seen #{0}
        :invalid-properties #{}
        :working-attributes (library-utils/get-initial-attributes (:workspace props))
        :published? (get-in props [:workspace :workspace :library-attributes :library:published])
        :version-attributes (->> versions
                                 (map keyword)
                                 (map (fn [version]
                                        [version (get-in library-schema [:properties version :default])]))
                                 (into {}))
        :required-attributes (library-utils/find-required-attributes library-schema)}))
   :render
   (fn [{:keys [props state locals this]}]
     (let [{:keys [library-schema can-share? owner? writer? catalog-with-read?]} props
           {:keys [page-num pages-seen invalid-properties working-attributes published? required-attributes validation-error submit-error]} @state
           editable? (or writer? catalog-with-read?)
           set-discoverable? (or can-share? catalog-with-read? owner?)]
       ;; FIXME: refactor -- this is heavily copy/pasted from OKCancelForm
       [:div {}
        (when (:submitting? @state)
          [comps/Blocker {:banner "Submitting..."}])
        [:div {:style {:borderBottom style/standard-line
                       :padding "20px 48px 18px"
                       :fontSize "137%" :fontWeight 400 :lineHeight 1}}
         "Catalog Dataset"
         (buttons/x-button modal/pop-modal)]
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
                   [questions enumerate] (library-utils/get-questions-for-page working-attributes library-schema page-num)
                   {:keys [invalid]} (library-utils/validate-required (library-utils/remove-empty-values working-attributes)
                                                                      questions required-attributes)]
               (cond
                 (< page-num page-count)
                 [Questions (merge {:ref "wizard-page"
                                    :key page-num
                                    :missing-properties (clojure.set/union invalid invalid-properties)
                                    :attributes working-attributes}
                                   (utils/restructure library-schema enumerate questions required-attributes editable? set-discoverable?))]
                 (= page-num page-count)
                 [DiscoverabilityPage
                  (merge
                   {:ref "wizard-page"
                    :set-discoverable? (or can-share? catalog-with-read? owner?)}
                   (select-keys working-attributes [:library:discoverableByGroups])
                   (select-keys @locals [:library-groups])
                   (select-keys props [:library-schema]))]
                 (> page-num page-count) (render-summary-page working-attributes library-schema invalid-properties))))}]]
         (when validation-error
           [:div {:style {:marginTop "1em" :color (:exception-state style/colors) :textAlign "center"}}
            validation-error])
         [comps/ErrorViewer {:error submit-error}]
         (flex/box
          {:style {:marginTop 40}}
          (flex/strut 80)
          flex/spring
          [buttons/Button {:text "Previous"
                           :onClick (fn [_]
                                      (if-let [prev-page (peek (:pages-stack @state))]
                                        (swap! state #(-> %
                                                          (assoc :page-num prev-page)
                                                          (update :pages-stack pop)
                                                          (dissoc :validation-error)))))
                           :style {:width 80}
                           :disabled? (zero? page-num)}]
          (flex/strut 27)
          [buttons/Button {:text "Next"
                           :onClick #(react/call :next-page this)
                           :disabled? (> page-num (-> library-schema :wizard count))
                           :style {:width 80}}]
          flex/spring
          (let [save-permissions (or editable? set-discoverable?)
                last-page (> page-num (-> library-schema :wizard count))]
            [buttons/Button {:text (if published? "Republish" "Submit")
                             :onClick #(react/call :submit this editable? set-discoverable?)
                             :disabled? (or (and published? (not-empty invalid-properties))
                                            (not (and save-permissions last-page)))
                             :style {:width 80}}]))]]))
   :component-did-mount
   (fn [{:keys [locals]}]
     (endpoints/get-library-groups
      (fn [{:keys [success? get-parsed-response]}]
        (if success?
          (swap! locals assoc :page-attributes {} :library-groups (get-parsed-response))
          (swap! locals assoc :page-attributes {})))))
   :component-did-update
   (fn [{:keys [prev-state state refs]}]
     (when-not (= (:page-num prev-state) (:page-num @state))
       (react/call :scroll-to (@refs "scroller") 0)))
   :next-page
   (fn [{:keys [state refs this locals after-update props]}]
     (swap! state dissoc :validation-error)
     (if-let [error-message (react/call :validate (@refs "wizard-page"))]
       (swap! state assoc :validation-error error-message)
       (let [{:keys [working-attributes pages-stack required-attributes page-num]} @state
             attributes-from-page (react/call :get-attributes (@refs "wizard-page"))
             all-attributes (merge working-attributes attributes-from-page)
             invalid-attributes (atom #{})]
         (swap! state assoc :working-attributes all-attributes)
         (swap! locals update :page-attributes assoc page-num attributes-from-page)
         (doseq [page (conj pages-stack page-num)]
           (let [[questions _] (library-utils/get-questions-for-page all-attributes (:library-schema props) page)
                 {:keys [invalid]} (library-utils/validate-required (library-utils/remove-empty-values all-attributes)
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
       (while (and (not (library-utils/get-questions-for-page working-attributes library-schema @next))
                   (< @next (count (:wizard library-schema))))
         (swap! next inc))
       @next))
   :submit
   (fn [{:keys [props state locals]} editable? set-discoverable?]
     ;; you can submit incomplete metadata unless it is currently published, because we cannot republish with incomplete
     ;; metadata and we automatically republish when we save metadata if it's currently published
     (if (and (:published? @state) (not-empty (:invalid-properties @state)))
       (swap! state assoc :validation-error "You will need to complete all required metadata attributes to be able to re-publish the workspace in the Data Library")
       (let [attributes-seen (apply merge (vals (select-keys (:page-attributes @locals) (:pages-stack @state))))
             discoverable-by (:library:discoverableByGroups attributes-seen)
             invoke-args (if (and set-discoverable? (not editable?))
                           {:name endpoints/save-discoverable-by-groups :data discoverable-by}
                           {:name endpoints/save-library-metadata :data (merge
                                                                         (library-utils/remove-empty-values (merge attributes-seen (:version-attributes @state)))
                                                                         ; ensure discoverable by is being sent. when it is reset to all users, it is the empty list
                                                                         ; and therefore removed by the call above
                                                                         {:library:discoverableByGroups discoverable-by})})]
         (swap! state assoc :submitting? true :submit-error nil)
         (endpoints/call-ajax-orch
          {:endpoint ((:name invoke-args) (:workspace-id props))
           :payload (:data invoke-args)
           :headers utils/content-type=json
           :on-done (fn [{:keys [success? get-parsed-response]}]
                      (swap! state dissoc :submitting?)
                      (if success?
                        (do (modal/pop-modal)
                            ((:request-refresh props)))
                        (swap! state assoc :submit-error (get-parsed-response false))))}))))})
