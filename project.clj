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
                                      :figwheel {:websocket-host :js-client-host}
                                      :compiler
                                      {:main ~(with-ns "dev")
                                       :optimizations :none
                                       :source-map true
                                       :source-map-timestamp true}}}}
                   :figwheel {:http-server-root ""
                              :server-port 80}}
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
