(ns org.broadinstitute.firecloud-ui.common.components
  (:require
    [clojure.string :refer [blank?]]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.icons :as icons]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    [org.broadinstitute.firecloud-ui.endpoints :as endpoints]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))


(react/defc Spinner
  {:render
   (fn [{:keys [props]}]
     [:span {:style {:margin "1em" :whiteSpace "nowrap"}}
      [:img {:src "assets/spinner.gif"
             :style {:height "1.5em" :verticalAlign "middle" :marginRight "1ex"}}]
      (:text props)])})


(react/defc Button
  {:get-default-props
   (fn []
     {:color (:button-blue style/colors)})
   :render
   (fn [{:keys [props]}]
     (let [disabled? (:disabled? props)]
       [:a {:style {:display "inline-block"
                    :backgroundColor (:color props)
                    :WebkitFilter (when disabled? "grayscale()")
                    :cursor (when disabled? "default")
                    :color "white" :fontWeight 500
                    :borderRadius 2 :padding (if (:icon props) "0.7em" "0.7em 1em")
                    :fontFamily (when (:icon props) "fontIcons")
                    :fontSize (when (:icon props) "80%")
                    :textDecoration "none"}
            :href "javascript:;"
            :onClick (if disabled?
                       #(js/alert (if (string? disabled?) disabled? "This action is disabled"))
                       (fn [e] ((:onClick props) e)))
            :onKeyDown (when-not disabled? (common/create-key-handler [:space :enter] (:onClick props)))}
        (or (:text props) (icons/icon-text (:icon props)))
        (when (= (:style props) :add)
          [:span {:style {:display "inline-block" :height "1em" :width "1em" :marginLeft "1em"
                          :position "relative"}}
           [:span {:style {:position "absolute" :top "-55%" :fontSize "200%" :fontWeight "normal"}}
            "+"]])]))})


(react/defc TabBar
  (let [Tab (react/create-class
              {:get-initial-state
               (fn []
                 {:hovering? false})
               :render
               (fn [{:keys [props state]}]
                 [:div {:style {:float "left" :padding "1em 2em"
                                :borderLeft (when (zero? (:index props))
                                              (str "1px solid " (:line-gray style/colors)))
                                :borderRight (str "1px solid " (:line-gray style/colors))
                                :backgroundColor (when (:active? props) "white")
                                :cursor "pointer"
                                :position "relative"}
                        :onMouseOver (fn [e] (swap! state assoc :hovering? true))
                        :onMouseOut (fn [e] (swap! state assoc :hovering? false))
                        :onClick (fn [e] ((:onClick props) e))}
                  (:text props)
                  (when (or (:active? props) (:hovering? @state))
                    [:div {:style {:position "absolute" :top "-0.5ex" :left 0
                                   :width "100%" :height "0.5ex"
                                   :backgroundColor (:button-blue style/colors)}}])
                  (when (:active? props)
                    [:div {:style {:position "absolute" :bottom -1 :left 0 :width "100%" :height 2
                                   :backgroundColor "white"}}])])})]
    {:set-active-tab
     (fn [{:keys [this state]} index & render-args]
       (set! (.-renderArgs this) render-args)
       (swap! state assoc :active-tab-index index))
     :get-initial-state
     (fn []
       {:active-tab-index 0})
     :render
     (fn [{:keys [this props state]}]
       [:div {}
        [:div {:style {:backgroundColor (:background-gray style/colors)
                       :borderTop (str "1px solid " (:line-gray style/colors))
                       :borderBottom (str "1px solid " (:line-gray style/colors))
                       :padding "0 1.5em"}}
         (map-indexed
           (fn [i tab]
             [Tab {:index i :text (:text tab)
                   :active? (= i (:active-tab-index @state))
                   :onClick (fn [e]
                              (swap! state assoc :active-tab-index i)
                              (when-let [f (:onTabSelected tab)] (f e)))}])
           (:items props))
         (common/clear-both)]
        (let [active-item (nth (:items props) (:active-tab-index @state))
              render (:render active-item)]
          [:div {} (apply render (.-renderArgs this))])])}))


(react/defc Dialog
  {:get-default-props
   (fn []
     {:blocking? true
      :cycle-focus? false})
   :render
   (fn [{:keys [props state]}]
     (let [content (:content props)
           anchored? (not (nil? (:get-anchor-dom-node props)))]
       (assert (react/valid-element? content)
               (subs (str "Not a react element: " content) 0 200))
       (when (or (not anchored?) (:position @state))
         [:div {:style {:backgroundColor (if (:blocking? props)
                                           "rgba(110, 110, 110, 0.4)"
                                           "rgba(210, 210, 210, 0.4)")
                        :position "absolute" :zIndex 8888
                        :top 0 :left 0 :right 0 :height (.. js/document -body -offsetHeight)}
                :onKeyDown (common/create-key-handler [:esc] #((:dismiss-self props)))
                :onClick (when-not (:blocking? props) #((:dismiss-self props)))}
          [:div {:style (if anchored?
                          {:position "absolute" :backgroundColor "#fff"
                           :top (get-in @state [:position :top])
                           :left (get-in @state [:position :left])}
                          {:transform "translate(-50%, 0px)" :backgroundColor "#fff"
                           :position "relative" :marginBottom 60
                           :top 60 :left "50%" :width (:width props)})
                 :onClick (when-not (:blocking? props) #(.stopPropagation %))}
           content]])))
   :component-did-mount
   (fn [{:keys [this props state]}]
     (when-let [get-dom-node (:get-anchor-dom-node props)]
       (swap! state assoc :position {:top (.. (get-dom-node) -offsetTop)
                                     :left (.. (get-dom-node) -offsetLeft)}))
     (when-let [get-first (:get-first-element-dom-node props)]
       (common/focus-and-select (get-first))
       (when-let [get-last (:get-last-element-dom-node props)]
         (.addEventListener (get-first) "keydown" (common/create-key-handler [:tab] #(.-shiftKey %)
                                                    (fn [e] (.preventDefault e)
                                                      (when (:cycle-focus? props)
                                                        (.focus (get-last))))))
         (.addEventListener (get-last) "keydown" (common/create-key-handler [:tab] #(not (.-shiftKey %))
                                                   (fn [e] (.preventDefault e)
                                                     (when (:cycle-focus? props)
                                                       (.focus (get-first))))))))
     (set! (.-onKeyDownHandler this)
           (common/create-key-handler [:esc] #((:dismiss-self props))))
     (.addEventListener js/window "keydown" (.-onKeyDownHandler this)))
   :component-will-unmount
   (fn [{:keys [this]}]
     (.removeEventListener js/window "keydown" (.-onKeyDownHandler this)))})


(react/defc XButton
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:position "absolute" :top 4 :right 4}}
      [Button {:icon :x :onClick #((:dismiss props))}]])})


(react/defc OKCancelForm
  {:get-default-props
   (fn []
     {:show-cancel? true})
   :render
   (fn [{:keys [props]}]
     [:div {}
      [:div {:style {:borderBottom (str "1px solid " (:line-gray style/colors))
                     :padding "20px 48px 18px"
                     :fontSize "137%" :fontWeight 400 :lineHeight 1}}
       (:header props)]
      [:div {:style {:padding "22px 48px 40px" :backgroundColor (:background-gray style/colors)}}
       (:content props)
       [:div {:style {:marginTop 40 :textAlign "center"}}
        (when (:show-cancel? props)
          [:a {:style {:marginRight 27 :marginTop 2 :padding "0.5em"
                       :display "inline-block"
                       :fontSize "106%" :fontWeight 500 :textDecoration "none"
                       :color (:button-blue style/colors)}
               :href "javascript:;"
               :onClick #((:dismiss-self props))
               :onKeyDown (common/create-key-handler [:space :enter] #((:dismiss-self props)))}
           "Cancel"])
        (:ok-button props)]]])})


(react/defc Blocker
  {:render
   (fn [{:keys [props]}]
     (when (:banner props)
       [:div {:style {:backgroundColor "rgba(210, 210, 210, 0.4)"
                      :position "fixed" :top 0 :bottom 0 :right 0 :left 0 :zIndex 9999}}
        [:div {:style {:position "absolute" :top "50%" :left "50%"
                       :transform "translate(-50%, -50%)"
                       :backgroundColor "#fff" :padding "2em"}}
         [Spinner {:text (:banner props)}]]]))})


(react/defc CompleteIcon
  {:get-default-props
   (fn []
     {:color (:success-green style/colors)
      :size 24})
   :render
   (fn [{:keys [props]}]
     [:span {:style {:display "inline-block" :position "relative" :verticalAlign "middle"
                     :width (int (* 1.27 (:size props)))
                     :height (int (* 1.27 (:size props)))
                     :backgroundColor "fff" :borderRadius "100%"}}
      (style/center {}
        (icons/font-icon {:style {:color (:color props) :fontSize (int (* 0.5 (:size props)))}}
          :status-done))])})

(react/defc RunningIcon
  {:get-default-props
   (fn []
     {:color (:success-green style/colors)
      :size 24})
   :render
   (fn [{:keys [props]}]
     (let [hamburger-height (int (/ (:size props) 6))
           spacer-height (int (/ (- (:size props) 4 (* 3 hamburger-height)) 2))
           hamburger (fn [color] [:div {:style {:height hamburger-height
                                                :width (:size props)
                                                :borderRadius hamburger-height
                                                :backgroundColor color}}])
           spacer [:div {:style {:height spacer-height}}]]
       [:span {:style {:display "inline-block" :position "relative" :verticalAlign "middle"
                       :height (:size props) :width (:size props)}}
        (style/center {}
          [:div {}
           (hamburger "white")
           spacer
           (hamburger (:color props))
           spacer
           (hamburger (:color props))])]))})

(react/defc ExceptionIcon
  {:get-default-props
   (fn []
     {:size 24})
   :render
   (fn [{:keys [props]}]
     [:span {:style {:display "inline-block" :position "relative" :verticalAlign "middle"
                     :height (:size props) :width (:size props)}}
      (style/center {}
        (icons/font-icon {:style {:color "#fff" :fontSize (:size props)}} :status-warning))])})

(react/defc StatusLabel
  {:render
   (fn [{:keys [props]}]
     [:div {:style {:background (:color props) :color "#fff"
                    :padding 20 :borderRadius 5 :textAlign "center"}}
      (:icon props)
      [:span {:style {:marginLeft "1.5ex" :fontSize "125%" :fontWeight 400
                      :verticalAlign "middle"}}
       (:text props)]])})

(react/defc SidebarButton
  {:get-default-props
   (fn []
     {:style :heavy})
   :render
   (fn [{:keys [props]}]
     (let [heavy? (= :heavy (:style props))
           disabled? (:disabled? props)
           margin (:margin props)
           color (cond (keyword? (:color props)) (get style/colors (:color props))
                       :else (:color props))]
       [:div {:style {:fontSize "106%"
                      :WebkitFilter (when disabled? "grayscale()")
                      :marginTop (when (= margin :top) "1em")
                      :marginBottom (when (= margin :bottom) "1em")
                      :padding "0.7em 0" :textAlign "center"
                      :cursor (if disabled? "default" "pointer")
                      :backgroundColor (if heavy? color "transparent")
                      :color (if heavy? "#fff" color)
                      :border (when-not heavy? (str "1px solid " (:line-gray style/colors)))
                      :borderRadius (when heavy? 4)}
              :onClick (if disabled?
                         #(js/alert (if (string? disabled?) disabled? "This action is disabled"))
                         (:onClick props))}
        (icons/font-icon {:style {:verticalAlign "middle" :fontSize "135%"}} (:icon props))
        [:span {:style {:verticalAlign "middle" :marginLeft "1em"}} (:text props)]]))})

(react/defc EntityDetails
  {:render
   (fn [{:keys [props state this]}]
     (let [entity (:entity props)
           make-field
           (fn [entity key label & [render]]
             [:div {}
              [:span {:style {:fontWeight 500 :width 100 :display "inline-block" :paddingBottom "0.3em"}} label]
              [:span {} ((or render identity) (entity key))]])
           config? (contains? entity "method")]
       [:div {:style {:backgroundColor (:background-gray style/colors)
                      :borderRadius 8 :border (str "1px solid " (:line-gray style/colors))
                      :padding "1em"}}
        (react/call :render-details this make-field entity)
        [:div {:style {:paddingTop "0.5em"}}
         [:span {:style {:fontWeight 500 :marginRight "1em"}} (if config? "Referenced Method:" "Payload:")]
         (style/create-link
           #(swap! state assoc :payload-expanded (not (:payload-expanded @state)))
           (if (:payload-expanded @state) "Collapse" "Expand"))]
        (when (:payload-expanded @state)
          (if config?
            [:div {:style {:margin "0.5em 0 0 1em"}}
             (react/call :render-details this make-field (entity "method"))
             [:div {:style {:fontWeight 500 :marginTop "1em"}} "Payload:"]
             [:pre {:style {:fontSize "90%"}} (get-in entity ["method" "payload"])]]
            [:pre {:style {:fontSize "90%"}} (entity "payload")]))]))
   :render-details
   (fn [{:keys []} make-field entity]
     [:div {}
      [:div {:style {:float "left"}}
       (make-field entity "namespace" "Namespace: ")
       (make-field entity "name" "Name: ")
       (make-field entity "snapshotId" "Snapshot ID: ")]
      [:div {:style {:float "left" :marginLeft "5em"}}
       (make-field entity "createDate" "Created: " common/format-date)
       (make-field entity "entityType" "Entity Type: ")
       (make-field entity "synopsis" "Synopsis: ")]
      (common/clear-both)
      [:div {:style {:fontWeight 500 :padding "0.5em 0 0.3em 0"}}
       "Documentation:"]
      (if (blank? (entity "documentation"))
        [:div {:style {:fontStyle "italic" :fontSize "90%"}} "No documentation provided"]
        [:div {:style {:fontSize "90%"}} (entity "documentation")])])})

(react/defc GCSFilePreviewLink
  {:render
   (fn [{:keys [props state refs this]}]
     (assert (:bucket-name props) "No bucket name provided")
     (assert (:object props) "No GCS object provided")
     [:div {}
      [:a {:href "javascript:;"
           :onClick #(react/call :show-dialog this)}
       (:gcs-uri props)]
      (when (or (:show-dialog? @state) (:loading? @state))
        (let [{:keys [data error]} (:response @state)
              data-size (when data (data "size"))
              labeled (fn [label & contents]
                        [:div {}
                         [:div {:style {:display "inline-block" :width 120}} (str label ": ")]
                         contents])]
          [:div {:style {:position "fixed" :top 0 :left 0 :right 0 :bottom 0 :zIndex 9999
                         :fontSize "initial" :fontWeight "initial"}}
           [Dialog
            {:dismiss-self #(swap! state dissoc :show-dialog?)
             :width "75%"
             :content
             (react/create-element
               [OKCancelForm
                {:header "File Details"
                 :content (react/create-element
                            [:div {}
                             (labeled "Google Bucket" (:bucket-name props))
                             (labeled "Object" (:object props))
                             (when (:loading? @state)
                               [Spinner {:text "Getting file info..."}])
                             (when data
                               [:div {:style {:marginTop "1em"}}
                                (labeled "File size"
                                  (common/format-filesize data-size)
                                  [:span {:style {:marginLeft "1em"}}
                                   [:a {:href (data "mediaLink")} "Download"]]
                                  (when (> data-size 100000000)
                                    [:span {:style {:color (:exception-red style/colors) :marginLeft "2ex"}}
                                     (icons/font-icon {:style {:fontSize "100%" :verticalAlign "middle" :marginRight "1ex"}}
                                       :status-warning-triangle)
                                     "Warning: Downloading this file may incur a large data egress charge"]))
                                (if (:show-details? @state)
                                  [:div {}
                                   (labeled "Created" (common/format-date (data "timeCreated")))
                                   (labeled "Updated" (common/format-date (data "updated")))
                                   (labeled "MD5" (data "md5Hash"))
                                   (style/create-link
                                     #(swap! state dissoc :show-details?)
                                     "Collapse")]
                                  (style/create-link
                                    #(swap! state assoc :show-details? true)
                                    "More info"))])
                             (when error
                               [:div {:style {:marginTop "1em"}}
                                [:span {:style {:color (:exception-red style/colors)}} "Error! "]
                                "This file was not found."
                                (if (:show-error-details? @state)
                                  [:div {}
                                   [:pre {} error]
                                   (style/create-link
                                     #(swap! state dissoc :show-error-details?)
                                     "Hide detail")]
                                  [:div {}
                                   (style/create-link
                                     #(swap! state assoc :show-error-details? true)
                                     "Show full error response")])])])
                 :dismiss-self #(swap! state dissoc :show-dialog?)
                 :show-cancel? false
                 :ok-button [Button {:text "Done" :onClick #(swap! state dissoc :show-dialog?)}]}])}]]))])
   :show-dialog
   (fn [{:keys [state props]}]
     (if (:response @state)
       (swap! state assoc :show-dialog? true)
       (do
         (swap! state assoc :loading? true)
         (endpoints/call-ajax-orch
           {:endpoint (endpoints/get-gcs-stats (:bucket-name props) (:object props))
            :on-done (fn [{:keys [success? get-parsed-response xhr]}]
                       (swap! state assoc :show-dialog? true :loading? false
                         :response (if success? {:data (get-parsed-response)}
                                                {:error (.-responseText xhr)})))}))))})
