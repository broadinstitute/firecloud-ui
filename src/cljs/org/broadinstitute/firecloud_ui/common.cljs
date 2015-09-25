(ns org.broadinstitute.firecloud-ui.common)


(def keymap
  {:backspace 8 :tab 9 :enter 13 :shift 16 :ctrl 17 :alt 18 :capslock 20 :esc 27 :space 32
   :pgup 33 :pgdown 34 :end 35 :home 67 :left 37 :up 38 :right 39 :down 40 :insert 45 :del 46})

(defn create-key-handler
  ([keys func] (create-key-handler keys (fn [e] true) func))
  ([keys modifier func] (fn [e]
                          (when (modifier e)
                            (let [keycode (.-keyCode e)]
                              (when (some #(= keycode (% keymap)) keys)
                                (func e)))))))

(defn clear! [refs & ids]
  (doseq [id ids]
    (set! (.-value (.getDOMNode (@refs id))) "")))

(defn get-text [refs & ids]
  (if (= 1 (count ids))
    (-> (@refs (first ids)) .getDOMNode .-value clojure.string/trim)
    (map
      (fn [id]
        (-> (@refs id) .getDOMNode .-value clojure.string/trim))
      ids)))

(defn clear-both [] [:div {:style {:clear "both"}}])

(defn scroll-to [x y] (.scrollTo js/window x y))

(defn scroll-to-top [] (scroll-to 0 0))

(defn is-in-view [elem]
  (let [doc-view-top (.-scrollY js/window)
        doc-view-bottom (+ doc-view-top (.-innerHeight js/window))
        elem-top (.-offsetTop elem)
        elem-bottom (+ elem-top (.-offsetHeight elem))]
    (and (< doc-view-top elem-top) (> doc-view-bottom elem-bottom))))


(def ^:private user-select-keys ["userSelect" "webkitTouchCallout" "webkitUserSelect"
                                 "mozUserSelect" "khtmlUserSelect" "msUserSelect"])

(defn disable-text-selection []
  (let [state (into {} (map (juxt identity #(aget (-> js/document .-body .-style) %)) user-select-keys))]
    (doseq [k user-select-keys]
      (aset (-> js/document .-body .-style) k "none"))
    state))

(defn restore-text-selection [state]
  (doseq [k user-select-keys]
    (aset (-> js/document .-body .-style) k (state k))))

(defn focus-and-select [dom-node]
  (.focus dom-node)
  (when (= "text" (.-type dom-node))
    (.select dom-node)))


(defn compute-status [workspace]
  (let [count (get-in workspace ["workspaceSubmissionStats" "runningSubmissionsCount"])]
    (cond (not (nil? (get-in workspace ["workspaceSubmissionStats" "lastFailureDate"]))) "Exception"
          (zero? count) "Complete"
          :else "Running")))
