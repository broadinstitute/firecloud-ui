(defproject org.broadinstitute/firecloud-ui "0.0.1"
  :dependencies
  [
   [inflections "0.13.0"]
   [dmohs/react "1.3.0"]
   [org.broadinstitute/react-cljs-modal "2017.08.28"]
   [org.clojure/clojure "1.9.0"]
   [org.clojure/clojurescript "1.9.946"]
   ]
  :plugins [[lein-cljsbuild "1.1.7"] [lein-figwheel "0.5.14"]]
  :profiles {:dev
             {:dependencies [[binaryage/devtools "0.9.9"]]
              :cljsbuild
              {:builds
               {:client
                {:source-paths ["src/cljs/test"]
                 :figwheel true
                 :compiler
                 {;; Use this namespace (which requires main) so that testing is readily available
                  ;; in all dev builds.
                  :main "broadfcuitest.testrunner"
                  :output-dir "resources/public/build"
                  :optimizations :none
                  :pretty-print true
                  :anon-fn-naming-policy :mapped
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
                  :optimizations :advanced
                  :static-fns true
                  :fn-invoke-direct true
                  :elide-asserts true
                  :language-out :ecmascript5
                  :optimize-constants true}}}}}}
  :clean-targets ^{:protect false} [:target-path]
  :cljsbuild {:builds
              {:client
               {:source-paths ["src/cljs/main"]
                :compiler {:main "broadfcui.main"
                           :output-dir "target/build"
                           :output-to "target/main.js"
                           :asset-path "build"}}}})
