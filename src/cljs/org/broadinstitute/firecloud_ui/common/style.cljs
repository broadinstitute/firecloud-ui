(ns org.broadinstitute.firecloud-ui.common.style
  (:require [org.broadinstitute.firecloud-ui.utils :as utils :refer [deep-merge]]))

(def colors {:background-light "#f4f4f4"
             :background-dark "#4d4d4d"
             :border-light "#cacaca"
             :button-primary "#457fd2"
             :link-active "#6690c5"
             :line-default "#e6e6e6"
             :running-state "#67688a"
             :success-state "#7aac20"
             :exception-state "#e85c46"
             :tag-background "#d4ecff"
             :tag-foreground "#2c3c4d"
             :text-light "#666"
             :text-lighter "#7f7f7f"
             :text-lightest "#989898"})

(def standard-line (str "1px solid " (:line-default colors)))

(defn color-for-status [status]
  (case status
    "Complete" (:success-state colors)
    "Running" (:running-state colors)
    "Exception" (:exception-state colors)))


(defn create-section-header [text]
  [:div {:style {:fontSize "125%" :fontWeight 500}} text])

(defn create-paragraph [& children]
  [:div {:style {:margin "17px 0 0.33333em 0" :paddingBottom "2.5em"
                 :fontSize "90%" :lineHeight 1.5}}
   children])

(defn create-textfield-hint [text]
  [:div {:style {:fontSize "80%" :fontStyle "italic" :margin "-1.3ex 0 1ex 0"}} text])


(def ^:private input-text-style
  {:backgroundColor "#fff"
   ;; Split out border properties so they can be individually overridden
   :borderWidth 1 :borderStyle "solid" :borderColor (:border-light colors) :borderRadius 3
   :boxSizing "border-box"
   :fontSize "88%"
   :marginBottom "0.75em" :padding "0.5em"})

(def ^:private select-style
  {:backgroundColor "#fff"
   ;; Split out border properties so they can be individually overridden
   :borderWidth 1 :borderStyle "solid" :borderColor (:border-light colors) :borderRadius 2
   :color "#000" :height 33 :width "100%" :fontSize "88%"
   :marginBottom "0.75em" :padding "0.33em 0.5em"})


(defn create-form-label [text]
  [:div {:style {:marginBottom "0.16667em" :fontSize "88%"}} text])

(defn create-text-field [props]
  [:input (deep-merge {:type "text" :style input-text-style} props)])

(defn create-search-field [props]
  [:input (deep-merge {:type "search" :style (assoc input-text-style :WebkitAppearance "none")} props)])

(defn create-text-area [props]
  [:textarea (deep-merge {:style input-text-style} props)])

(defn create-select [props options]
  [:select (deep-merge {:style select-style} props)
   (map-indexed (fn [i opt] [:option {:value i} opt]) options)])

(defn create-identity-select [props options]
  [:select (deep-merge {:style select-style} props)
   (map (fn [opt] [:option {:value opt} opt]) options)])

(defn create-server-error-message [message]
  [:div {:style {:textAlign "center" :color (:exception-state colors)}}
   message])

(defn create-validation-error-message [fails]
  [:div {:style {:color (:exception-state colors)}}
   (map (fn [fail] [:div {} fail]) fails)])

(defn create-message-well [message]
  [:div {:style {:textAlign "center" :backgroundColor (:background-light colors)
                 :padding "1em 0" :borderRadius 8}}
   message])

(defn center [props & children]
  [:div (deep-merge props {:style {:position "absolute" :top "50%" :left "50%"
                                   :transform "translate(-50%, -50%)"}})
   children])

(defn create-flexbox [attributes & children]
  [:div (deep-merge attributes {:style {:display "flex" :alignItems "center"}})
   children])

(defn left-ellipses [props & children]
  [:div (deep-merge props {:style {:overflow "hidden" :textOverflow "ellipsis" :whiteSpace "nowrap"
                                   :direction "rtl" :textAlign "left"}})
   children])

(defn right-ellipses [props & children]
  [:div (deep-merge props {:style {:overflow "hidden" :textOverflow "ellipsis" :whiteSpace "nowrap"}})
   children])

(defn create-unselectable [type props & children]
  [type (deep-merge {:style {:userSelect "none" :MozUserSelect "none"
                             :WebkitTouchCallout "none" :WebkitUserSelect "none"
                             :KhtmlUserSelect "none" :MsUserSelect "none"}} props)
   children])

(defn create-link [{:keys [text] :as attributes}]
  [:a (deep-merge {:href "javascript:;"
                   :style {:textDecoration "none" :color (:button-primary colors)}}
                  (dissoc attributes :text))
   text])

(defn render-name-id [name snapshot-id]
  [:div {}
   [:span {:style {:fontWeight 500}} name]
   [:span {:style {:fontWeight 200 :paddingLeft "1em"}} "Snapshot ID: "]
   [:span {:style {:fontWeight 500}} snapshot-id]])

(defn render-entity [namespace name snapshot-id]
  (render-name-id (str namespace "/" name) snapshot-id))


