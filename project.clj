(defproject org.broadinstitute/firecloud-ui "0.0.1"
  :dependencies
  [
   [inflections "0.13.0"]
   [dmohs/react "1.2.4+15.5.4-1"]
   [org.broadinstitute/react-cljs-modal "2017.08.28"]
   [org.clojure/clojure "1.8.0"]
   [org.clojure/clojurescript "1.9.293"]
   [cljsjs/react-autosuggest "9.3.2-0"]
   ]
  :plugins [[lein-cljsbuild "1.1.7"] [lein-figwheel "0.5.14"] [lein-resource "17.06.1"]
            ; [lein-ancient "0.6.12"] ; check for outdated dependencies, uncomment and run `lein ancient` to use
            ]
  :profiles {:dev
             {:dependencies [[binaryage/devtools "0.9.7"]]
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
                  :asset-path "target/build"
                  :source-map true
                  :preloads [devtools.preload]
                  :external-config {:devtools/config {:features-to-install
                                                      [:formatters :hints]}}}}}}}
             :deploy
             {:cljsbuild
              {:builds
               {:client
                {:compiler
                 {;; As of 10/29/15, advanced optimization triggers
                  ;; infinite recursion, which I was not able to figure
                  ;; out.
                  :optimizations :simple
                  :pretty-print false
                  :output-dir "build"}}}}}}
  :target-path "resources/public/target"
  :clean-targets ^{:protect false} [:target-path]
  :cljsbuild {:builds {:client {:source-paths ["src/cljs/main"]
                                :compiler {:main "broadfcui.main"
                                           :output-dir "resources/public/target/build"
                                           :output-to "resources/public/target/compiled.js"}}}}
  :resource {:resource-paths ["src/static"]
             :target-path "resources/public"
             :excludes [#".*\.DS_Store"]
             :skip-stencil [#"src/static/assets/.*"]
             :extra-values {:vtag ~(.getTime (java.util.Date.))}})
