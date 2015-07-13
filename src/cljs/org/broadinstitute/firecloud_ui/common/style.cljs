(ns org.broadinstitute.firecloud-ui.common.style
  (:require [org.broadinstitute.firecloud-ui.utils :as utils]))

(def colors {:link-blue "#6690c5"
             :line-gray "#e6e6e6"
             :button-blue "#457fd2"
             :background-gray "#f4f4f4"
             :border-gray "#cacaca"
             :text-light "#7f7f7f"
             :text-gray "#666"
             :footer-text "#989898"})


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
  [:input (utils/deep-merge {:type "text" :style input-text-style} props)])

(defn create-text-area [props]
  [:textarea (utils/deep-merge {:style input-text-style} props)])

(defn create-select [props & options]
  [:select (utils/deep-merge {:style select-style} props)
   (map (fn [opt] [:option {} opt]) options)])
