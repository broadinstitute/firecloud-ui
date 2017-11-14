(ns broadfcui.common.input
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.style :as style]
   ))

(react/defc TextField
  {:get-trimmed-text
   (fn [{:keys [refs]}]
     (common/get-trimmed-text refs "textfield"))
   :validate
   (fn [{:keys [props state this]}]
     (let [text (this :get-trimmed-text)
           fails (keep (fn [p] (when-not ((:test p) text) (:message p)))
                       (filter some? (:predicates props)))]
       (when (seq fails)
         (swap! state assoc :invalid true)
         fails)))
   :access-field
   (fn [{:keys [refs]}]
     (@refs "textfield"))
   :render
   (fn [{:keys [state props]}]
     (style/create-text-field
      (merge {:ref "textfield"
              :style (merge (or (:style props) {})
                            (when (:invalid @state)
                              {:outline (str (:state-exception style/colors) " auto 5px")}))
              :onChange #(do (swap! state dissoc :invalid)
                             (when-let [x (:onChange props)]
                               (x %)))}
             (dissoc props :ref :style :onChange :predicates))))})

(defn get-text [refs & ids]
  (if (= 1 (count ids))
    ((@refs (first ids)) :get-trimmed-text)
    (map #((@refs %) :get-trimmed-text) ids)))

(defn validate [refs & ids]
  (not-empty (distinct (flatten (keep #((@refs %) :validate) ids)))))

;; usage: (let [[field1 field2 & fails] (get-and-validate refs "field1-ref" "field2-ref")]
;;          (if fails
;;              (do-failure fails)
;;              (do-success field1 field2)))
(defn get-and-validate [refs & ids]
  (if (= 1 (count ids))
    (cons (get-text refs (first ids)) (validate refs (first ids)))
    (concat (apply get-text refs ids) (apply validate refs ids))))


;; Some premade predicates:

(defn nonempty [field-name]
  {:test (comp not empty?) :message (str field-name " cannot be empty")})

(defn integer [field-name & {:keys [min max]}]
  (let [parses (partial re-matches #"\-?[0-9]+")
        in-range #(<= (or min js/-Infinity) (int %) (or max js/Infinity))]
    {:test (every-pred parses in-range)
     :message (str field-name " must be an integer"
                   (cond (and min max) (str " between " min " and " max)
                         min (str " ≥ " min)
                         max (str " ≤ " max)))}))

(defn alphanumeric_- [field-name]
  {:test #(re-matches #"[A-Za-z0-9_\-]*" %)
   :message (str field-name " may only contain letters, numbers, underscores, and dashes.")})

(def hint-alphanumeric_- "Only letters, numbers, underscores, and dashes allowed")

(defn max-string-length [field-name max-length]
  {:test #(<= (count %) max-length)
   :message (str field-name " must be no more than " max-length " characters.")})

(defn nonempty-alphanumeric_-period [field-name]
  {:test #(re-matches #"^[A-Za-z0-9_\-.]+$" %)
   :message (str field-name " may only contain letters, numbers, underscores, dashes, and periods.")})

(def hint-alphanumeric_-period "Only letters, numbers, underscores, dashes, and periods allowed")

(def ^:private strictnesses
  {:simple #"^\S+@\S+\.\S+$"
   :strict #"^([\w-]+(?:\.[\w-]+)*)@((?:[\w-]+\.)*\w[\w-]{0,66})\.([a-z]{2,6}(?:\.[a-z]{2})?)$"})

(defn- is-valid-email? [test strictness]
  (re-matches (get strictnesses strictness (:simple strictnesses)) (clojure.string/lower-case test)))

(defn valid-email [field-name & [strictness]]
  {:test #(is-valid-email? % strictness) :message (str field-name " must be a valid email address")})

(defn valid-email-or-empty [field-name & [strictness]]
  {:test (fn [test] (or (empty? test) (is-valid-email? test strictness)))
   :message (str field-name " must be a valid email address")})


