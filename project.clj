(defn- inside-container? []
  (.exists (clojure.java.io/file "/.dockerenv")))


(defn- get-figwheel-opts []
  (when (inside-container?)
    (let [specified-host (if-let [x (System/getenv "FIGWHEEL_HOST")]
                           (if (clojure.string/blank? (clojure.string/trim x)) nil x))
          host (or specified-host "192.168.99.100")]
      (when (nil? specified-host)
        (println (str "***\n"
                      "*** You did not specify a FIGWHEEL_HOST environment variable.\n"
                      "*** Using the default: " host "\n"
                      "***")))
      {:websocket-url (str "ws://" host ":80/figwheel-ws")})))


(defn- get-figwheel-server-opts []
  (when (inside-container?)
    {:http-server-root ""
    :server-hostname "local.broadinstitute.org"
    :server-port 80
    :hawk-options {:watcher :polling}}))


(defn- with-ns [n] (str "org.broadinstitute.firecloud-ui." n))


(defproject org.broadinstitute/firecloud-ui "0.0.1"
  :dependencies
  [
   [dmohs/react "0.2.11"]
   [org.clojure/clojure "1.7.0"]
   [org.clojure/clojurescript "1.7.228"]
   [inflections "0.9.14"]
   [cljsjs/moment "2.9.0-3"]
   [cljsjs/codemirror "5.10.0-0"]
   [cljsjs/marked "0.3.5-0"]
   ]
  :plugins [[lein-cljsbuild "1.1.2"] [lein-figwheel "0.5.0-5"] [lein-resource "15.10.2"]]
  :profiles {:dev {:dependencies [[binaryage/devtools "0.4.1"]]
                   :cljsbuild
                   {:builds {:client {:source-paths ["src/cljsdev"]
                                      :figwheel ~(merge {} (get-figwheel-opts))
                                      :compiler
                                      {:main ~(with-ns "dev")
                                       :optimizations :none
                                       :source-map true
                                       :source-map-timestamp true}}}}
                   :figwheel ~(get-figwheel-server-opts)}
             :deploy {:cljsbuild
                      {:builds {:client {:source-paths ["src/cljsprod"]
                                         :compiler
                                         {:main ~(with-ns "render")
                                          ;; As of 10/29/15, advanced optimization triggers
                                          ;; infinite recursion, which I was not able to figure
                                          ;; out.
                                          :optimizations :simple
                                          :pretty-print false}}}}}}
  :cljsbuild {:builds {:client {:source-paths ["src/cljs"]
                                :compiler {:output-dir "target/build"
                                           :asset-path "build"
                                           :output-to "target/compiled.js"}}}}
  :resource {:resource-paths ["src/static"]
             :excludes [#".*\.DS_Store"]
             :skip-stencil [#"src/static/assets/.*"]
             :extra-values {:vtag ~(.getTime (java.util.Date.))}})
