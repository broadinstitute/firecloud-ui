(ns org.broadinstitute.firecloud-ui.page.method-repo
  (:require
   [dmohs.react :as react]
   [org.broadinstitute.firecloud-ui.page.method-config-importer :refer [MethodConfigImporter]]
   ))


(react/defc Page
  {:render
   (fn []
     [:div {:style {:padding "1em"}}
      [:h2 {} "Method Repository"]
      [MethodConfigImporter {}]])})
