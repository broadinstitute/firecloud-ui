(defproject org.broadinstitute/firecloud-ui "0.0.1"
  :dependencies
  [
   [dmohs/react "1.0.2+15.0.2"]
   [org.clojure/clojure "1.8.0"]
   [org.clojure/clojurescript "1.9.293"]
   [inflections "0.9.14"]
   ]
  :plugins [[lein-cljsbuild "1.1.4"] [lein-figwheel "0.5.8"] [lein-resource "16.9.1"]]
  :profiles {:dev
             {:dependencies [[binaryage/devtools "0.8.3"]]
              :figwheel {:css-dirs ["resources/public"]}
              :cljsbuild
              {:builds
               {:client
                {:source-paths ["src/cljs/test"]
                 :figwheel true
                 :compiler
                 {;; Use this namespace (which requires main) so that testing is readily available
                  ;; in all dev builds.
                  :main "broadfcuitest.testrunner"
                  :optimizations :none
                  :asset-path "build"
                  :source-map true
                  :preloads [devtools.preload]
                  :external-config {:devtools/config {:features-to-install
                                                      [:formatters :hints]}}}}}}}
             :deploy {:cljsbuild
                      {:builds {:client {:compiler
                                         {;; As of 10/29/15, advanced optimization triggers
                                          ;; infinite recursion, which I was not able to figure
                                          ;; out.
                                          :optimizations :simple
                                          :pretty-print false
                                          :output-dir "build"}}}}}}
  :target-path "resources/public"
  :clean-targets ^{:protect false} [:target-path]
  :cljsbuild {:builds {:client {:source-paths ["src/cljs/main"]
                                :compiler {:main "broadfcui.main"
                                           :output-dir "resources/public/build"
                                           :output-to "resources/public/compiled.js"}}}}
  :resource {:resource-paths ["src/static"]
             :excludes [#".*\.DS_Store"]
             :skip-stencil [#"src/static/assets/.*"]
             :extra-values {:vtag ~(.getTime (java.util.Date.))}})
