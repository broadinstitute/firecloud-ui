(defproject org.broadinstitute/firecloud-ui "0.0.1"
  :dependencies
  [
   [dmohs/react "1.0.2+15.0.2"]
   [org.clojure/clojure "1.8.0"]
   [org.clojure/clojurescript "1.9.293"]
   [inflections "0.9.14"]
   ]
  :plugins [[lein-cljsbuild "1.1.4"] [lein-figwheel "0.5.8"] [lein-npm "0.6.2"] [lein-resource "16.9.1"]]
  :npm {:dependencies [[any-resize-event, "~1.0.0"]
                       [codemirror "~5.10.0"]
                       [css-loader "~0.26.1"]
                       [extract-text-webpack-plugin "~2.0.0-rc"]
                       [file-loader "~0.9.0"]
                       [font-awesome "~4.2.0"]
                       [github-markdown-css "~2.4.1"]
                       [jquery "^1.12.2"]
                       [jquery-ui "^1.11.2"]
                       [marked "~0.3.5"]
                       [typeahead.js "~0.11.1"]
                       [url-loader "~0.5.7"]
                       [webpack "~2.2.0"]
                       ]
        :package {:scripts
                  {:postinstall "webpack --optimize-minimize --output-filename webpack-deps.min.js"}}}
  :profiles {:dev
             {:dependencies [[binaryage/devtools "0.8.3"]]
              :target-path "resources/public"
              :clean-targets ^{:protect false} [:target-path]
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
                  :output-dir "resources/public/build"
                  :asset-path "build"
                  :output-to "resources/public/compiled.js"
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
                                          :pretty-print false}}}}}}
  :cljsbuild {:builds {:client {:source-paths ["src/cljs/main"]
                                :compiler {:main "broadfcui.main"
                                           :output-to "target/compiled.js"}}}}
  :resource {:resource-paths ["src/static"]
             :excludes [#".*\.DS_Store"]
             :skip-stencil [#"src/static/assets/.*"]
             :extra-values {:vtag ~(.getTime (java.util.Date.))}})
