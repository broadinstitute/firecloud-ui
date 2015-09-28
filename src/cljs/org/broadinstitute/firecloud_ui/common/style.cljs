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


(def ^:private input-text-style
  {:backgroundColor "#fff"
   :border (str "1px solid " (:border-gray colors)) :borderRadius 3
   :boxShadow "0px 1px 3px rgba(0,0,0,0.06)" :boxSizing "border-box"
   :fontSize "88%"
   :marginBottom "0.75em" :padding "0.5em"})

(def ^:private select-style
  {:backgroundColor "#fff" ;;TODO background image?
   ;;:backgroundPosition "right center" :backgroundRepeat "no-repeat"
   :borderColor (:border-gray colors) :borderRadius 2 :borderWidth 1 :borderStyle "solid"
   :color "#000"
   :marginBottom "0.75em" :padding "0.33em 0.5em"
   :width "100%" :fontSize "88%"})


(defn create-form-label [text]
  [:div {:style {:marginBottom "0.16667em" :fontSize "88%"}} text])

(defn create-hint [text]
  [:em {:style {:fontSize "69%"}} text])

(defn create-text-field [props]
  [:input (deep-merge {:type "text" :style input-text-style} props)])

(defn create-text-area [props]
  [:textarea (deep-merge {:style input-text-style} props)])

(defn create-select [props options]
  [:select (deep-merge {:style select-style} props)
   (map (fn [opt] [:option {} opt]) options)])

(defn create-server-error-message [message]
  [:div {:style {:textAlign "center" :color (:exception-red colors)}}
   "FireCloud service returned error: " (or message "(no message provided)")])

(defn create-message-well [message]
  [:div {:style {:textAlign "center" :backgroundColor (:background-gray colors)
                 :padding "1em 0" :borderRadius 8}}
   message])

(defn center [props & children]
  [:div (deep-merge props {:style {:position "absolute" :top "50%" :left "50%"
                                   :transform "translate(-50%, -50%)"}})
   children])

(defn create-unselectable [type props & children]
  [type (deep-merge {:style {:userSelect "none" :MozUserSelect "none"
                             :WebkitTouchCallout "none" :WebkitUserSelect "none"
                             :KhtmlUserSelect "none" :MsUserSelect "none"}} props)
   children])

(defn create-link [onClick & children]
  [:a {:href "javascript:;"
       :style {:textDecoration "none" :color (:button-blue colors)}
       :onClick onClick}
   children])

(defn render-email [email]
  (let [tokens (clojure.string/split email #"@")]
    (assert (= 2 (count tokens)) "Not exactly 1 '@' in email address")
    [:div {:style {:display "inline-block"}}
     [:span {:style {:fontWeight 600}} (nth tokens 0)]
     [:span {:style {:fontweight 300}} (str "@" (nth tokens 1))]]))
