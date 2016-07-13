(ns org.broadinstitute.firecloud-ui.common.markdown
  (:require
    cljsjs.marked
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.utils :as utils]
    ))

;; Documentation:
;; https://github.com/chjj/marked

(js/marked.setOptions
  #js{:sanitize true})

(defonce ^:private renderer (js/marked.Renderer.))
(set! renderer.link
  (fn [href, title, text]
    (str "<a href='" href "' title='" title "' target='_blank'>"
         text
         "</a>")))

(react/defc MarkdownView
  {:render
   (fn []
     [:div {:ref "ref" :className "markdown-body"}])
   :component-did-mount
   (fn [{:keys [props refs]}]
     (set! (.-innerHTML (@refs "ref"))
           (js/marked (:text props) #js{:renderer renderer})))})
