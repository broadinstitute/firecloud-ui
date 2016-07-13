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

(react/defc MarkdownView
  {:render
   (fn []
     [:div {:ref "ref" :className "markdown-body"}])
   :component-did-mount
   (fn [{:keys [props refs]}]
     (set! (.-innerHTML (@refs "ref"))
           (js/marked (:text props))))})
