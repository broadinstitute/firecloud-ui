(ns broadfcui.common.codemirror
  (:require
   [dmohs.react :as react]
   [broadfcui.common.style :as style]
   [broadfcui.components.script-loader :refer [ScriptLoader]]
   [broadfcui.components.split-pane :refer [SplitPane]]
   [broadfcui.utils :as utils]
   ))

(defonce ^:private codemirror-constructor (atom false))

(defonce ^:private pipeline-constructor (atom false))

(defn- regex-escape [s]
  (clojure.string/replace s #"[\\\*\+\|\^]" #(str "\\" %)))

(def ^:private assignment-ops ["=" "+=" "-=" "*=" "/=" "//=" "%=" "&=" "|=" "^=" ">>=" "<<=" "**="])
(def ^:private comparison-ops ["<=" ">=" "==" "<" ">" "!="])
(def ^:private arithmetic-ops ["+" "-" "*" "**" "/" "//" "%" "<<" ">>" "&" "|" "^" "~"])
(def ^:private all-ops-regex (->> [assignment-ops comparison-ops arithmetic-ops]
                                  (apply concat)
                                  (map regex-escape)
                                  (interpose "|")
                                  (apply str)))

(defn- define-wdl []
  (js-invoke
   @codemirror-constructor "defineMode" "wdl"
   (fn []
     #js{:token (fn [stream]
                  (.eatSpace stream)
                  (cond
                    (.match stream #"#.*")
                    "comment"
                    (or (.match stream #"\"(?:[^\"\\]|\\.)*\"") (.match stream "'(?:[^'\\]|\\.)*'"))
                    "string"
                    (.match stream (re-pattern (str "(?:" all-ops-regex ")")))
                    "operator"
                    (.match stream #"(?:import|as|true|false|input|output|call|command|output|runtime|task|workflow)\b")
                    "keyword"
                    (.match stream #"(?:Array|Boolean|File|Float|Int|Map|Object|String|Uri)\b")
                    "builtin"
                    (.match stream #"[A-Za-z_][A-Za-z0-9_]*")
                    "variable"
                    (.match stream #"\$\{.*?\}")
                    "variable-3"
                    (.match stream #"[\{\}]")
                    "bracket"
                    (.match stream #"[0-9]*\.?[0-9]+")
                    "number"
                    :else
                    (do (.next stream) nil)))})))

(react/defc CodeMirror
  {:add-listener
   (fn [{:keys [this]} event-type listener]
     (this :call-method "on" event-type listener))
   :remove-listener
   (fn [{:keys [this]} event-type listener]
     (this :call-method "off" event-type listener))
   :call-method
   (fn [{:keys [locals]} method & args]
     (apply js-invoke (aget (:codemirror-instance @locals) "doc") method args))
   :get-default-props
   (fn []
     {:line-numbers? true
      :read-only? true
      :mode "wdl"})
   :render
   (fn [{:keys [props state this]}]
     (let [{:keys [loaded? error?]} @state
           {:keys [read-only? text]} props]
       [:div {}
        (cond
          error? [:textarea {:readonly read-only? :wrap "off"
                             :style {:width "100%" :height 300 :backgroundColor "white"
                                     :fontFamily "Menlo, monospace"}}
                  text]
          (not loaded?) [ScriptLoader
                         {:on-error #(swap! state assoc :error? true)
                          :on-load (fn []
                                     (when-not @codemirror-constructor
                                       (reset! codemirror-constructor (aget js/window "webpackDeps" "CodeMirror"))
                                       (define-wdl))
                                     (this :-display-code)
                                     (when-let [init (:initialize props)]
                                       (init this))
                                     (swap! state assoc :loaded? true))
                          :path "codemirror-deps.bundle.js"}])
        [:div {:data-test-id (:data-test-id props)
               :ref "container"
               :style (when loaded? {:border style/standard-line})}]]))
   :-display-code
   (fn [{:keys [refs props locals]}]
     (let [{:keys [mode line-numbers? read-only? text]} props]
       (swap! locals assoc :codemirror-instance
              (@codemirror-constructor (react/find-dom-node (@refs "container"))
               (clj->js
                (merge
                 (when text {:value text})
                 {:mode mode :lineNumbers line-numbers? :readOnly read-only?
                  :viewportMargin js/Infinity}))))))
   :component-will-receive-props
   (fn [{:keys [props next-props locals]}]
     (when (:read-only? props)
       (-> (@locals :codemirror-instance)
           (js-invoke "getDoc")
           (js-invoke "setValue" (:text next-props)))))})

(react/defc PipelineBuilder
  {:component-will-receive-props
   (fn [{:keys [this next-props]}]
     (this :-render-pipeline (:wdl next-props)))
   :render
   (fn [{:keys [props state locals this]}]
     (let [{:keys [loaded? error?]} @state]
       [:div {:ref #(swap! locals assoc :wrapper %)}
        (cond
          error? [:h2 {} "Something went wrong..."]
          (not loaded?) [ScriptLoader
                         {:on-error #(swap! state assoc :error? true)
                          :on-load (fn []
                                     (when-not @pipeline-constructor
                                       (reset! pipeline-constructor (aget js/window "webpackDeps" "PipelineBuilder")))
                                     (this :-render-pipeline (:wdl props))
                                     (swap! state assoc :loaded? true))
                          :path "codemirror-deps.bundle.js"}])
        [:div {:data-test-id (:data-test-id props)
               :ref #(swap! locals assoc :container %)
               :style (when loaded? {:border style/standard-line :min-height 500})}]]))
   :-render-pipeline
   (fn [{:keys [locals props]} wdl]
     (let [{:keys [read-only?]} props
           {:keys [container wrapper]} @locals
           Visualizer (.-Visualizer @pipeline-constructor)
           diagram (Visualizer. container read-only?)
           recalculate-size #(.setDimensions (.-paper diagram)
                                             (- (.-clientWidth wrapper) 2) (- (.-clientHeight wrapper) 2))]
       (.then (.parse @pipeline-constructor wdl)
              (fn [res]
                (.attachTo diagram
                           (aget (.-model res) 0))))
       (.addEventListener wrapper "onresize" recalculate-size)
       (recalculate-size)
       (when read-only?
         (.disableSelection diagram))))})

(react/defc PipelineAndWDL
  {:render
   (fn [{:keys [props state]}]
     (let [{:keys [mode error? loaded?]} @state
           {:keys [wdl read-only?]} props
           mode (or mode :code)
           tab (fn [mode-key label]
                 (let [selected? (= mode-key mode)]
                   [:div {:style {:display "inline-block"
                                  :border style/standard-line :padding "3px 8px" :cursor "pointer"
                                  :color (when selected? "white")
                                  :backgroundColor ((if selected? :button-primary :background-light) style/colors)}
                          :onClick #(swap! state assoc :mode mode-key)}
                    label]))
           pipeline-view [PipelineBuilder {:wdl wdl :read-only? read-only?}]
           code-mirror [CodeMirror {:text wdl :read-only? read-only?}]]
       [:div {}
        (cond
          error? [:h2 {} "Something went wrong..."]
          (not loaded?) [ScriptLoader
                         {:on-error #(swap! state assoc :error? true)
                          :on-load #(swap! state assoc :loaded? true)
                          :path "codemirror-deps.bundle.js"}]
          :else
          [:div {}
           [:div {}
            (tab :code "Code")
            (tab :preview "Preview")
            (tab :side-by-side "Side-by-side")]
           (case mode
             :code code-mirror
             :preview pipeline-view
             :side-by-side [SplitPane
                            {:left code-mirror :right pipeline-view
                             ;; initial position is the center of the screen, taking into consideration side padding in the WDL tab
                             :initial-slider-position (str "calc(50vw - 1.5rem - 25px)")}])])]))})
