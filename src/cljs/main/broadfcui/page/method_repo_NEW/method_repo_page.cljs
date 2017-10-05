(ns broadfcui.page.method-repo-NEW.method-repo-page
  (:require
   [dmohs.react :as react]
   [broadfcui.page.method-repo-NEW.method-repo-table :refer [MethodRepoTable]]
   [broadfcui.nav :as nav]
   ))


(react/defc Page
  {:render
   (fn []
     [MethodRepoTable])})


(defn add-nav-paths []
  (nav/defpath
   :method-repo2
   {:component Page
    :regex #"methods2"
    :make-props (fn [_] {})
    :make-path (fn [] "methods2")}))
