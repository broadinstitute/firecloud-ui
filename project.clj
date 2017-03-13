(defproject org.broadinstitute/firecloud-ui "0.0.1"
  :dependencies
  [
   [dmohs/react "1.0.2+15.0.2"]
   [org.clojure/clojure "1.8.0"]
   [org.clojure/clojurescript "1.9.293"]
   [inflections "0.9.14"]
   ]
  :plugins [[lein-cljsbuild "1.1.4"] [lein-figwheel "0.5.8"] [lein-npm "0.6.2"] [lein-resource "16.9.1"]]
  :npm {:dependencies [[any-resize-event, "~1.0.0"]         ; used in dataset catalog wizard
                       [codemirror "~5.10.0"]               ; used to format WDL
                       [corejs-typeahead "~1.1.1"]          ; maintained fork of bootstrap typeahead
                       [css-loader "~0.26.1"]               ; webpack plugin
                       [extract-text-webpack-plugin "~2.0.0-rc"] ; webpack plugin
                       [file-loader "~0.9.0"]               ; webpack plugin
                       [font-awesome "~4.7.0"]              ; used for icons, also required by IGV
                       [foundation-sites "~6.3.0"]          ; used for tooltips, etc
                       [github-markdown-css "~2.4.1"]       ; used with marked
                       [jquery "^2.0"]                      ; required by IGV
                       [jquery-ui "^1.12"]                  ; required by IGV
                       [marked "~0.3.5"]                    ; formats markdown
                       [node-sass "~4.5.0"]                 ; required by sass-loader
                       [sass-loader "~5.0.0"]               ; webpack plugin (for foundation)
                       [select2 "^4.0"]                     ; adds search to selects
                       [uglify-js "git://github.com/mishoo/UglifyJS2#harmony"] ; override webpack's uglify with es6 compatible version
                       [url-loader "~0.5.7"]                ; webpack plugin
                       [webpack "~2.2.0"]                   ; combines deps into single js/css file
                       ]}
  :profiles {:dev
             {:dependencies [[binaryage/devtools "0.8.3"]]
              :npm {:package {:scripts
                              {:postinstall "webpack"}}}
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
                                          :output-dir "build"}}}}
                      :npm {:package {:scripts
                                      {:postinstall "webpack -p"}}}}}
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
