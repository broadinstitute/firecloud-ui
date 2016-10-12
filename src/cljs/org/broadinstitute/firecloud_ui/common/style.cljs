(ns org.broadinstitute.firecloud-ui.common.style
  (:require [org.broadinstitute.firecloud-ui.utils :as utils :refer [deep-merge]]))

(def colors {:background-gray "#f4f4f4"
             :border-gray "#cacaca"
             :button-blue "#457fd2"
             :exception-red "#e85c46"
             :footer-text "#989898"
             :header-darkgray "#4d4d4d"
             :link-blue "#6690c5"
             :line-gray "#e6e6e6"
             :running-blue "#67688a"
             :success-green "#7aac20"
             :tag-background "#d4ecff"
             :tag-foreground "#2c3c4d"
             :text-gray "#666"
             :text-light "#7f7f7f"})

(def standard-line (str "1px solid " (:line-gray colors)))

(defn color-for-status [status]
  (case status
    "Complete" (:success-green colors)
    "Running" (:running-blue colors)
    "Exception" (:exception-red colors)))


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
   :borderWidth 1 :borderStyle "solid" :borderColor (:border-gray colors) :borderRadius 3
   :boxSizing "border-box"
   :fontSize "88%"
   :marginBottom "0.75em" :padding "0.5em"})

(def ^:private select-style
  {:backgroundColor "#fff"
   ;; Split out border properties so they can be individually overridden
   :borderWidth 1 :borderStyle "solid" :borderColor (:border-gray colors) :borderRadius 2
   :color "#000" :height 33 :width "100%" :fontSize "88%"
   :marginBottom "0.75em" :padding "0.33em 0.5em"})


(defn create-form-label [text]
  [:div {:style {:marginBottom "0.16667em" :fontSize "88%"}} text])

(defn create-text-field [props]
  [:input (deep-merge {:type "text" :style input-text-style} props)])

(defn create-text-area [props]
  [:textarea (deep-merge {:style input-text-style} props)])

(defn create-select [props options]
  [:select (deep-merge {:style select-style} props)
   (map-indexed (fn [i opt] [:option {:value i} opt]) options)])

(defn create-identity-select [props options]
  [:select (deep-merge {:style select-style} props)
   (map (fn [opt] [:option {:value opt} opt]) options)])

(defn create-server-error-message [message]
  [:div {:style {:textAlign "center" :color (:exception-red colors)}}
   message])

(defn create-validation-error-message [fails]
  [:div {:style {:color (:exception-red colors)}}
   (map (fn [fail] [:div {} fail]) fails)])

(defn create-message-well [message]
  [:div {:style {:textAlign "center" :backgroundColor (:background-gray colors)
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
                   :style {:textDecoration "none" :color (:button-blue colors)}}
                  (dissoc attributes :text))
   text])

(defn render-entity [namespace name snapshot-id]
  [:div {}
   [:span {:style {:fontWeight 500}} namespace "/" name]
   [:span {:style {:fontWeight 200 :paddingLeft "1em"}} "Snapshot ID: "]
   [:span {:style {:fontWeight 500}} snapshot-id]])



(defn render-name-id [name snapshot-id]
  [:div {}
   [:span {:style {:fontWeight 500}} name]
   [:span {:style {:fontWeight 200 :paddingLeft "1em"}} "Snapshot ID: "]
   [:span {:style {:fontWeight 500}} snapshot-id]])


