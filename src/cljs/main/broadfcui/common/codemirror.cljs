(ns broadfcui.common.codemirror
  (:require
    cljsjs.codemirror
    [dmohs.react :as react]
    [broadfcui.common.style :as style]
    [broadfcui.utils :as utils]
    ))


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

(js/CodeMirror.defineMode "wdl"
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

(react/defc CodeMirror
  {:add-listener
   (fn [{:keys [this]} event-type listener]
     (react/call :call-method this "on" event-type listener))
   :remove-listener
   (fn [{:keys [this]} event-type listener]
     (react/call :call-method this "off" event-type listener))
   :call-method
   (fn [{:keys [locals]} method & args]
     (apply utils/call-external-object-method (aget (:code-mirror-component @locals) "doc") method args))
   :get-default-props
   (fn []
     {:line-numbers? true
      :read-only? true})
   :render
   (fn [{:keys [props]}]
     [:div {:style {:border style/standard-line}}
      [:textarea {:ref "ref" :defaultValue (:text props)}]])
   :component-did-mount
   (fn [{:keys [refs props locals]}]
     (swap! locals assoc :code-mirror-component
            (js/CodeMirror.fromTextArea (@refs "ref")
               #js{:mode "wdl" :lineNumbers (:line-numbers? props) :readOnly (:read-only? props)})))})
