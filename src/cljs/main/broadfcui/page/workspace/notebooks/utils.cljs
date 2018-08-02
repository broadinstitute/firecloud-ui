(ns broadfcui.page.workspace.notebooks.utils
  (:require
   [dmohs.react :as react]
   [broadfcui.utils :as utils]
   [broadfcui.config :as config]
   [broadfcui.utils.ajax :as ajax]
   ))

(defn create-inline-form-label [text]
  [:span {:style {:marginBottom "0.16667em" :fontSize "88%"}} text])

(defn contains-statuses [clusters statuses]
  (seq (clojure.set/intersection (set statuses) (set (map :status clusters)))))

(defn notebook-name [notebook]
  (clojure.string/replace (last (clojure.string/split (:name notebook) #"/")) ".ipynb" ""))

(defn leo-notebook-url-base [cluster]
  (str (config/leonardo-url-root) "/notebooks/" (:googleProject cluster) "/" (:clusterName cluster)))

(defn leo-notebook-url [cluster workspace-name notebook]
  (str (leo-notebook-url-base cluster) "/notebooks/" workspace-name "/" (last (clojure.string/split (:name notebook) #"/"))))

(defn get-notebooks-in-bucket [bucket-name token on-done]
  (ajax/call {:url (str "https://www.googleapis.com/storage/v1/b/" bucket-name "/o?prefix=notebooks")
              :method :get
              :headers {"Authorization" (str "Bearer " token)}
              :on-done on-done}))

(defn copy-notebook [bucket-name token notebook-to-copy new-name on-done]
  (ajax/call {:url (str "https://www.googleapis.com/storage/v1/b/" bucket-name "/o/" (js/encodeURIComponent (:name notebook-to-copy)) "/rewriteTo/b/" bucket-name "/o/" (js/encodeURIComponent (str "notebooks/" new-name ".ipynb")))
              :method :post
              :headers {"Authorization" (str "Bearer " token)}
              :on-done on-done}))

(defn delete-notebook [bucket-name token notebook-to-delete on-done]
  (ajax/call {:url (str "https://www.googleapis.com/storage/v1/b/" bucket-name "/o/" (js/encodeURIComponent (:name notebook-to-delete)))
              :method :delete
              :headers {"Authorization" (str "Bearer " token)}
              :on-done on-done}))

(defn create-notebook [bucket-name token notebook-name-to-create data on-done]
  (ajax/call {:url (str "https://www.googleapis.com/upload/storage/v1/b/" bucket-name "/o?uploadType=media&name=" (js/encodeURIComponent (str "notebooks/" notebook-name-to-create ".ipynb")))
              :method :post
              :headers {"Authorization" (str "Bearer " token)
                        "Content-Type" "application/x-ipynb+json"}
              :data (utils/->json-string data)
              :on-done on-done}))