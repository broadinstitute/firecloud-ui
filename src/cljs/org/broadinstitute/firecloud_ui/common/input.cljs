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
     (-> (@refs "textfield") .-value trim))
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
                               :style (merge (or (:style props) {})
                                        (when (:invalid @state)
                                          {:borderColor (:exception-red style/colors)}))
                               :onChange #(swap! state dissoc :invalid)
                               :defaultValue (:defaultValue props)
                               :placeholder (:placeholder props)
                               :disabled (:disabled props)
                               :spellCheck (:spellCheck props)}))})

(defn get-text [refs & ids]
  (if (= 1 (count ids))
    (react/call :get-text (@refs (first ids)))
    (map #(react/call :get-text (@refs %)) ids)))

(defn validate [refs & ids]
  (not-empty (distinct (flatten (keep #(react/call :validate (@refs %)) ids)))))

;; usage: (let [[field1 field2 & fails] (get-and-validate refs "field1-ref" "field2-ref")]
;;          (if fails
;;              (do-failure fails)
;;              (do-success field1 field2)))
(defn get-and-validate [refs & ids]
  (concat (apply get-text refs ids) (apply validate refs ids)))


;; Some premade predicates:

(defn nonempty [field-name]
  {:test #(not (empty? %)) :message (str field-name " cannot be empty")})

(defn alphanumeric_- [field-name]
  {:test #(re-matches #"[A-Za-z0-9_\-]*" %)
   :message (str field-name " may only contain letters, numbers, underscores, and dashes.")})


(def ^:private strictnesses
  {:simple #"^\S+@\S+\.\S+$"
   :strict #"^([\w-]+(?:\.[\w-]+)*)@((?:[\w-]+\.)*\w[\w-]{0,66})\.([a-z]{2,6}(?:\.[a-z]{2})?)$"})

(defn- is-valid-email? [test strictness]
  (re-matches (get strictnesses strictness (:simple strictnesses)) (clojure.string/lower-case test)))

(defn valid-email [field-name & [strictness]]
  {:test #(is-valid-email? % strictness) :message (str field-name " is not a valid email address")})

(defn valid-email-or-empty [field-name & [strictness]]
  {:test (fn [test] (or (empty? test) (is-valid-email? test strictness)))
   :message (str field-name " is not a valid email address (or empty)")})


