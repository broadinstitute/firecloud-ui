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
       pages)]]))


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

(defn convert-empty-strings [attributes]
  (utils/map-values
    (fn [val]
      (if (string? val)
        (not-empty val)
        val)) attributes))

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
        :pages-seen `()
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
           {:keys [page-num working-attributes required-attributes published? validation-error submit-error]} @state]
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
                             :enumerate enumerate
                             :questions questions
                             :attributes working-attributes
                             :required-attributes required-attributes}])
                [:div {} "Hello"]))}]]
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
   (fn [{:keys [state refs this locals after-update]}]
     (swap! state dissoc :validation-error)
     (if-let [error-message (react/call :validate (@refs "wizard-page"))]
       (swap! state assoc :validation-error error-message)
       (let [attributes-from-page (react/call :get-attributes (@refs "wizard-page"))]
         (swap! state update :working-attributes merge attributes-from-page)
         (swap! locals update :page-attributes assoc (:page-num @state) attributes-from-page)
         (after-update (fn [_]
                         (let [next-page (react/call :find-next-page this)]
                           (swap! state #(-> %
                                             (update :pages-seen conj (:page-num @state))
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
     ;; this should be calling something in questions itself??
     (let [attributes (convert-empty-strings (apply merge (:version-attributes @state) (:page-attributes @locals)))]
       (utils/log attributes)))})
         ;(endpoints/call-ajax-orch
         ;  {:endpoint (endpoints/save-library-metadata (:workspace-id props))
         ;   :payload attributes
         ;   :headers utils/content-type=json
         ;   :on-done (fn [{:keys [success? get-parsed-response]}]
         ;              (swap! state dissoc :submitting?)
         ;             (if success?
         ;               (do (modal/pop-modal)
         ;                   ((:request-refresh props)))
         ;               (swap! state assoc :submit-error (get-parsed-response false))))}))))})

;; TODO: make a new last window page that does the validation, allows you to submit, changes empty strings to nil
;; TODO: create a third option to let you skip that step (orch part is almost functional, need to create the 3rd page now that next will get you to)

;; the last window is where the publish vs save thing will happen