(ns broadfcui.page.method-repo.method-repo-page
  (:require
   [dmohs.react :as react]
   [broadfcui.page.method-repo.method-repo-table :refer [MethodRepoTable]]
   [broadfcui.nav :as nav]
   ))


(react/defc Page
  {:render
   (fn []
     [MethodRepoTable])})


(defn add-nav-paths []
  (nav/defpath
   :method-repo
   {:component Page
    :regex #"methods"
    :make-props (fn [_] {})
    :make-path (fn [] "methods")}))
