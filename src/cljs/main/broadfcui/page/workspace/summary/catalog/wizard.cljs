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

(defn- render-wizard-breadcrumbs [{:keys [library-schema page-num]}]
  (let [pages (:wizard library-schema)]
    [:div {:style {:flex "0 0 250px" :backgroundColor "white" :border style/standard-line}}
     [:ul {}
      (map-indexed
       (fn [index {:keys [title]}]
         (let [this (= index page-num)]
           [:li {:style {:margin "0.5em 0.5em 0.5em 0"
                         :fontWeight (when this "bold")
                         :color (when-not this (:text-lighter style/colors))}}
            title]))
       (conj pages {:title "Summary"}))]]))

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
      (if (string? val)
        (not-empty val)
        val)) attributes))

(defn- render-summary-page [attributes library-schema invalid-attributes]
  [:div {}
   ;; TODO (as part of 1321?) don't let people publish if you have chosen skip
   ;(if (= (:library:useLimitationOption attributes) "skip") ;; do this is in a non-hardcoded way
   ;  "Note, you cannot publish this dataset until you define the Data Use Limitations.")

   (if (not-empty invalid-attributes)
     [:div {:style {:fontSize "14px" :color (:exception-state style/colors)}}
      "Please fill in missing attributes before saving."
      [:ul {:style {:fontSize "13px"}}
       (map (fn [attribute]
              [:li {} (get-in library-schema [:properties (keyword attribute) :title])])
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
        (library-utils/render-consent-codes library-schema attributes))])])



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
        :pages-seen []
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
     (let [{:keys [library-schema]} props
           {:keys [page-num invalid-properties working-attributes published? required-attributes validation-error submit-error]} @state]
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
          (render-wizard-breadcrumbs {:library-schema library-schema :page-num page-num})
          [comps/ScrollFader
           {:ref "scroller"
            :outer-style {:flex "1 1 auto"
                          :border style/standard-line :boxSizing "border-box"
                          :backgroundColor "white"}
            :inner-style {:padding "1rem" :boxSizing "border-box" :height "100%"}
            :content
            (react/create-element
              (if (< page-num (count (:wizard library-schema)))
                (let [[questions enumerate] (get-questions-for-page working-attributes library-schema page-num)]


                 [Questions {:ref "wizard-page" :key page-num
                             :library-schema library-schema
                             :missing-properties invalid-properties
                             :enumerate enumerate
                             :questions questions
                             :attributes working-attributes
                             :required-attributes required-attributes}])
                (render-summary-page working-attributes library-schema invalid-properties)))}]]
         (when validation-error
           [:div {:style {:marginTop "1em" :color (:exception-state style/colors) :textAlign "center"}}
            validation-error])
         [comps/ErrorViewer {:error submit-error}]
         (flex/flex-box {:style {:marginTop 40}}
          (flex/flex-strut 80)
          flex/flex-spacer
          [comps/Button {:text "Previous"
                         :onClick (fn [_]
                                    (if-let [prev-page (peek (:pages-seen @state))]
                                      (swap! state #(-> %
                                                        (assoc :page-num prev-page)
                                                        (update :pages-seen pop)
                                                        (dissoc :validation-error)))))
                         :style {:width 80}
                         :disabled? (zero? page-num)}]
          (flex/flex-strut 27)
          [comps/Button {:text "Next"
                         :onClick #(react/call :next-page this)
                         :disabled? (= page-num (-> library-schema :wizard count))
                         :style {:width 80}}]
          flex/flex-spacer
          [comps/Button {:text (if published? "Republish" "Submit")
                         :onClick #(react/call :submit this)
                         :disabled? (< page-num (-> library-schema :wizard count))
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
       (let [{:keys [working-attributes pages-seen required-attributes page-num]} @state
             attributes-from-page (react/call :get-attributes (@refs "wizard-page"))
             all-attributes (merge working-attributes attributes-from-page)
             invalid-attributes (atom #{})]
         (swap! state assoc :working-attributes all-attributes)
         (swap! locals update :page-attributes assoc page-num attributes-from-page)
         (doseq [page (conj pages-seen page-num)]
              (let [questions (first (get-questions-for-page all-attributes (:library-schema props) page))
                    {:keys [invalid]} (library-utils/validate-required
                                              (convert-empty-strings all-attributes)
                                              questions
                                              required-attributes)]
                (reset! invalid-attributes (clojure.set/union invalid @invalid-attributes))))
         (swap! state assoc :invalid-properties @invalid-attributes)
       (after-update (fn [_]
                       (let [next-page (react/call :find-next-page this)]
                         (swap! state #(-> %
                                           (update :pages-seen conj page-num)
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
       (swap! state assoc :validation-error "You cannot save attributes without filling out required properties")
       (let [attributes-seen (atom {})]
         (doseq [page (:pages-seen @state)]
           (let [attrs (nth (:page-attributes @locals) page)]
             (swap! attributes-seen merge attrs)))
         (endpoints/call-ajax-orch
           {:endpoint (endpoints/save-library-metadata (:workspace-id props))
            :payload (convert-empty-strings (apply merge @attributes-seen (:version-attributes @state))) ;; still need the apply?
            :headers utils/content-type=json
            :on-done (fn [{:keys [success? get-parsed-response]}]
                       (swap! state dissoc :submitting?)
                       (if success?
                         (do (modal/pop-modal)
                             ((:request-refresh props)))
                         (swap! state assoc :submit-error (get-parsed-response false))))}))))})
