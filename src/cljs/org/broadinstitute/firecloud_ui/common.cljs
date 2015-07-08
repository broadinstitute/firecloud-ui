(ns org.broadinstitute.firecloud-ui.common)


(def keymap
  {:backspace 8 :tab 9 :enter 13 :shift 16 :ctrl 17 :alt 18 :capslock 20 :esc 27 :space 32
   :pgup 33 :pgdown 34 :end 35 :home 67 :left 37 :up 38 :right 39 :down 40 :insert 45 :del 46})

(defn create-key-handler [keys func]
  (fn [e]
    (let [keycode (.-keyCode e)]
      (when (some #(= keycode (% keymap)) keys)
        (func e)))))

(defn clear! [refs & ids]
  (doseq [id ids]
    (set! (.-value (.getDOMNode (@refs id))) "")))
