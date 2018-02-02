(ns broadfcui.common.style
  (:require
   [clojure.string :as string]
   [broadfcui.common :as common]
   [broadfcui.utils :as utils]
   ))

(def colors {:background-dark "#4d4d4d"
             :background-light "#f4f4f4"
             :border-light "#cacaca"
             :button-primary "#457fd2"
             :line-default "#e6e6e6"
             :state-disabled "#dadada"
             :state-exception "#e85c46"
             :state-running "#67688a"
             :state-success "#7aac20"
             :state-warning "#ffa726"
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
    "Complete" (:state-success colors)
    "Running" (:state-running colors)
    "Exception" (:state-exception colors)))

(def secondary-icon-style
  {:color (:text-light colors)
   :fontSize "1.2rem" :lineHeight "0.6rem"
   :padding "0.4rem"})

(def thin-page-style
  {:maxWidth 1000 :margin "auto" :minHeight 300 :paddingTop "1.5rem"})

(defn create-section-header [text]
  [:div {:style {:fontSize "125%" :fontWeight 500}} text])

(defn create-subsection-header [text]
  [:div {:style {:fontSize "112.5%" :fontWeight 500}} text])

(defn create-subsection-label [text]
  [:div {:style {:fontWeight 500 :paddingTop "0.5rem"}} text])

(defn create-subsection-contents [text]
  [:div {:style {:fontSize "90%" :lineHeight 1.5}} text])

(defn create-summary-block [title body]
  [:div {:style {:flexBasis "50%" :paddingRight "2rem" :marginBottom "2rem"}}
   [:div {:style {:paddingBottom "0.5rem"}}
    (create-subsection-header title)]
   (create-subsection-contents body)])

(defn create-paragraph [& children]
  [:div {:style {:margin "1rem 0 2rem"
                 :fontSize "90%" :lineHeight 1.5}}
   children])

(defn create-textfield-hint [text]
  [:div {:style {:fontSize "80%" :fontStyle "italic" :margin "-1.3ex 0 1ex 0"}} text])


(def input-text-style
  {:backgroundColor "#fff"
   ;; Split out border properties so they can be individually overridden
   :borderWidth 1 :borderStyle "solid" :borderColor (:border-light colors)
   :borderTopLeftRadius 3 :borderBottomLeftRadius 3 :borderBottomRightRadius 3 :borderTopRightRadius 3
   :boxSizing "border-box"
   :fontSize "88%"
   :marginLeft 0 :marginTop 0 :marginBottom "0.75em" :marginRight 0
   :padding "0.5em"})

(def select-style
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

(defn create-identity-select-name [props options & [placeholder]]
  (let [option-elements (map (fn [opt] [:option {:value opt} opt]) options)]
    [:select (utils/deep-merge {:style select-style} props)
     (if placeholder
       (cons [:option {:value -1 :disabled true} placeholder] option-elements)
       option-elements)]))

(defn create-identity-select [props options]
  [:select (utils/deep-merge {:style select-style} props)
   (map (fn [opt] [:option {:value opt} opt]) options)])

(defn create-server-error-message [message]
  [:div {:style {:textAlign "center" :color (:state-exception colors)}}
   message])

(defn create-validation-error-message [fails]
  [:div {:style {:color (:exception-state colors)}}
   (common/mapwrap :div fails)])

(defn create-message-well [message]
  [:div {:style {:textAlign "center" :backgroundColor (:background-light colors)
                 :padding "1em 0" :borderRadius 8}
         :data-test-id "message-well"}
   message])

(defn create-code-sample [text]
  [:code {:style {:backgroundColor (:background-dark colors) :color "white"
                  :fontWeight "bold" :fontFamily "Menlo, monospace" :fontSize 12
                  :padding "0.2rem" :borderRadius "0.2rem" :margin "0 0.1rem"}}
   text])

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
  [:span {:style {:minWidth "10px" :padding "3px 7px" :borderRadius "3px"
                  :fontSize "80%" :fontWeight "bold"
                  :color "white" :backgroundColor "#aaa"
                  :textAlign "center" :whiteSpace "nowrap" :verticalAlign "middle"}}
   count])

(defn render-tag
  ([tag] (render-tag {} tag))
  ([props tag]
   [:div (utils/deep-merge {:data-test-id (str (string/lower-case tag) "-tag")
                            :style {:display "inline-block" :background (:tag-background colors)
                                    :color (:tag-foreground colors) :margin "0.1rem"
                                    :borderRadius 3 :padding "0.2rem 0.5rem"}}
                           props)
    tag]))

(defn render-broad-logo []
  [:img {:src "assets/broad_logo.png" :style {:height 38}}])

(defn render-text-logo []
  [:div {:style {:display "inline-block"}}
   [:a {:href "/#" :style {:fontSize 32 :color (:button-primary colors)
                           :fontWeight "bold" :textDecoration "none" :height 38}}
    "FireCloud"]])
