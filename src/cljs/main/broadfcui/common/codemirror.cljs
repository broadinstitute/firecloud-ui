(ns broadfcui.common.codemirror
  (:require
   [dmohs.react :as react]
   [broadfcui.common.style :as style]
   [broadfcui.components.script-loader :refer [ScriptLoader]]
   [broadfcui.utils :as utils]
   ))

(defonce ^:private wdl-defined? (atom false))

(defn- get-codemirror []
  (aget js/window "webpackDeps" "CodeMirror"))

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
   (get-codemirror) "defineMode" "wdl"
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
                    (do (.next stream) nil)))}))
  (reset! wdl-defined? true))

(react/defc CodeMirror
  {:add-listener
   (fn [{:keys [this]} event-type listener]
     (this :call-method "on" event-type listener))
   :remove-listener
   (fn [{:keys [this]} event-type listener]
     (this :call-method "off" event-type listener))
   :call-method
   (fn [{:keys [locals]} method & args]
     (apply js-invoke (aget (:code-mirror-instance @locals) "doc") method args))
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
                                     (when-not @wdl-defined?
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
       (swap! locals assoc :code-mirror-instance
              ((get-codemirror) (react/find-dom-node (@refs "container"))
               (clj->js
                (merge
                 (when text {:value text})
                 {:mode mode :lineNumbers line-numbers? :readOnly read-only?
                  :viewportMargin js/Infinity}))))))
   :component-will-receive-props
   (fn [{:keys [props next-props locals]}]
     (when (:read-only? props)
       (-> (@locals :code-mirror-instance)
           (js-invoke "getDoc")
           (js-invoke "setValue" (:text next-props)))))})
