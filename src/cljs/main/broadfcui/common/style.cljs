(ns broadfcui.common.style
  (:require
    [clojure.string :as string]
    [broadfcui.utils :as utils]
    ))

(def colors {:background-light "#f4f4f4"
             :background-dark "#4d4d4d"
             :border-light "#cacaca"
             :button-primary "#457fd2"
             :line-default "#e6e6e6"
             :running-state "#67688a"
             :success-state "#7aac20"
             :warning-state "#ebaf3b"
             :exception-state "#e85c46"
             :disabled-state "#dadada"
             :tag-background "#d4ecff"
             :tag-foreground "#2c3c4d"
             :text-light "#666"
             :text-lighter "#7f7f7f"
             :text-lightest "#989898"})

(def igv-z-index 512)
(def modals-z-index (inc igv-z-index))

(def standard-line (str "1px solid " (:line-default colors)))

(defn color-for-status [status]
  (case status
    "Complete" (:success-state colors)
    "Running" (:running-state colors)
    "Exception" (:exception-state colors)))

(def secondary-icon-style
  {:color (:text-light colors)
   :fontSize "1.2rem" :lineHeight "0.6rem"
   :padding "0.4rem"})

(def thin-page-style
  {:maxWidth 1000 :margin "auto" :minHeight 300 :paddingTop "1.5rem"})

(defn create-section-header [text]
  [:div {:style {:fontSize "125%" :fontWeight 500}} text])

(defn create-paragraph [& children]
  [:div {:style {:margin "1rem 0 2rem"
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
   :height 33 :width "100%" :fontSize "88%"
   :marginBottom "0.75em" :padding "0.33em 0.5em"})


(defn create-form-label [text]
  [:div {:style {:marginBottom "0.16667em" :fontSize "88%"}} text])

;; jQuery3+React warns about nil :value elements. Replace with empty string.
(defn- replace-nil-value-key [props]
  (reduce-kv (fn [m k v]
               (if (and (= k :value) (nil? v))
                 (assoc m k "")
                 (assoc m k v)))
             {} props))

(defn create-text-field [props]
  [:input (utils/deep-merge {:type "text" :style input-text-style} (replace-nil-value-key props))])

(defn create-search-field [props]
  [:input (utils/deep-merge {:type "search" :style (assoc input-text-style :WebkitAppearance "none")}
                            (replace-nil-value-key props))])

(defn create-text-area [props]
  [:textarea (utils/deep-merge {:style input-text-style} props)])

(defn create-select [props options & placeholder]
  (let [option-elements (map-indexed (fn [i opt] [:option {:value i} opt]) options)]
    [:select (utils/deep-merge {:style select-style} props)
     (if-not (empty? placeholder)
       (cons [:option {:value -1 :disabled true} (first placeholder)] option-elements)
       option-elements)]))

(defn create-identity-select [props options]
  [:select (utils/deep-merge {:style select-style} props)
   (map (fn [opt] [:option {:value opt} opt]) options)])

(defn create-server-error-message [message]
  [:div {:style {:textAlign "center" :color (:exception-state colors)}}
   message])

(defn create-validation-error-message [fails]
  [:div {:style {:color (:exception-state colors)}}
   (map (fn [fail] [:div {} fail]) fails)])

(defn create-message-well [message]
  [:div {:style {:textAlign "center" :backgroundColor (:background-light colors)
                 :padding "1em 0" :borderRadius 8}
         :data-test-id "message-well"}
   message])

(defn center [props & children]
  [:div (utils/deep-merge props {:style {:position "absolute" :top "50%" :left "50%"
                                         :transform "translate(-50%, -50%)"}})
   children])

(defn create-flexbox [attributes & children]
  [:div (utils/deep-merge attributes {:style {:display "flex" :alignItems "center"}})
   children])

(defn left-ellipses [props & children]
  [:div (utils/deep-merge props {:style {:overflow "hidden" :textOverflow "ellipsis"
                                         :whiteSpace "nowrap" :direction "rtl" :textAlign "left"}})
   children])

(defn right-ellipses [props & children]
  [:div (utils/deep-merge props {:style {:overflow "hidden" :textOverflow "ellipsis"
                                         :whiteSpace "nowrap"}})
   children])

(defn create-unselectable [type props & children]
  [type (utils/deep-merge {:style {:userSelect "none" :MozUserSelect "none"
                                   :WebkitTouchCallout "none" :WebkitUserSelect "none"
                                   :KhtmlUserSelect "none" :MsUserSelect "none"}} props)
   children])

(defn create-link [{:keys [text] :as attributes}]
  [:a (utils/deep-merge {:href "javascript:;"
                         :style {:textDecoration "none" :color (:button-primary colors)}}
                        (dissoc attributes :text))
   text])

;; An obnoxious amount of effort due to "PROJECT_OWNER" vs. "NO ACCESS"
(defn prettify-access-level [s]
  (as-> s $
        (string/replace $ "_" " ")
        (string/split $ #"\b")
        (map string/capitalize $)
        (string/join $)))

(defn render-name-id [name snapshot-id]
  [:div {}
   [:span {:style {:fontWeight 500}} name]
   [:span {:style {:fontWeight 200 :paddingLeft "1em"}} "Snapshot ID: "]
   [:span {:style {:fontWeight 500}} snapshot-id]])

(defn render-entity [namespace name snapshot-id]
  (render-name-id (str namespace "/" name) snapshot-id))

(defn render-count [count]
  [:div {:style {:fontSize "80%" :fontWeight "normal" :float "right"}}
   [:span {:style {:display "inline-block"
                   :minWidth "10px"
                   :padding "3px 7px"
                   :color "#fff"
                   :fontWeight "bold"
                   :textAlign "center"
                   :whiteSpace "nowrap"
                   :verticalAlign "middle"
                   :backgroundColor "#aaa"
                   :borderRadius "3px"}}
    count]])

(defn render-logo []
  [:img {:src "assets/broad_logo.png" :style {:height 38}}])

; Temporary replacement for the Broad Logo.
(defn render-text-logo []
  [:div {:style {:display "inline-block"}}
   [:a {:href "/#" :style {:fontSize 32 :color (:button-primary colors)
                           :fontWeight "bold" :textDecoration "none" :height 38}}
    "FireCloud"]])
