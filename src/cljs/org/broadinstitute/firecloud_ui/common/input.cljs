(ns org.broadinstitute.firecloud-ui.common.input
  (:require
    [clojure.string :refer [trim]]
    [dmohs.react :as react]
    [org.broadinstitute.firecloud-ui.common :as common]
    [org.broadinstitute.firecloud-ui.common.style :as style]
    ))

(react/defc TextField
  {:get-text
   (fn [{:keys [refs]}]
     (-> (@refs "textfield") .getDOMNode .-value trim))
   :validate
   (fn [{:keys [props state this]}]
     (let [text (react/call :get-text this)
           fails (keep (fn [p] (when-not ((:test p) text) (:message p))) (:predicates props))]
       (when-not (empty? fails)
         (swap! state assoc :invalid true)
         fails)))
   :render
   (fn [{:keys [state props refs]}]
     (assert (not (empty? (:predicates props))) "No predicates for input/TextField")
     (style/create-text-field {:ref "textfield"
                               :style (merge (:style props)
                                        (when (:invalid @state)
                                          {:borderColor (:exception-red style/colors)}))
                               :onChange #(swap! state dissoc :invalid)
                               :defaultValue (:defaultValue props)
                               :placeholder (:placeholder props)}))})

(defn get-text [refs & ids]
  (map #(react/call :get-text (@refs %)) ids))

(defn validate [refs & ids]
  (not-empty (distinct (flatten (keep #(react/call :validate (@refs %)) ids)))))

;; usage: (let [[field1 field2 & fails] (get-and-validate refs "field1-ref" "field2-ref")]
;;          (if fails
;;              (do-failure fails)
;;              (do-success field1 field2)))
(defn get-and-validate [refs & ids]
  (concat (apply get-text refs ids) (apply validate refs ids)))


(defn nonempty [field-name]
  {:test #(not (empty? %)) :message (str field-name " cannot be empty")})
