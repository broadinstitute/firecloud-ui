(ns broadfcui.common.input
  (:require
   [dmohs.react :as react]
   [broadfcui.common :as common]
   [broadfcui.common.style :as style]
   ))

(react/defc TextField
  {:get-text
   (fn [{:keys [refs]}]
     (common/get-text refs "textfield"))
   :validate
   (fn [{:keys [props state this]}]
     (let [text (react/call :get-text this)
           fails (keep (fn [p] (when-not ((:test p) text) (:message p)))
                       (filter some? (:predicates props)))]
       (when-not (empty? fails)
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
                              {:borderColor (:exception-state style/colors)}))
              :onChange #(do (swap! state dissoc :invalid)
                             (when-let [x (:onChange props)]
                               (x %)))}
             (dissoc props :ref :style :onChange :predicates))))})

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
  (if (= 1 (count ids))
    (cons (get-text refs (first ids)) (validate refs (first ids)))
    (concat (apply get-text refs ids) (apply validate refs ids))))


;; Some premade predicates:

(defn nonempty [field-name]
  {:test (comp not empty?) :message (str field-name " cannot be empty")})

(defn integer [field-name & {:keys [min max]}]
  (let [parses (partial re-matches #"\-?[0-9]+")
        in-range #(<= (or min -Infinity) (int %) (or max Infinity))]
    {:test (every-pred parses in-range)
     :message (str field-name " must be an integer"
                   (cond (and min max) (str " between " min " and " max)
                         min (str " ≥ " min)
                         max (str " ≤ " max)))}))

(defn alphanumeric_- [field-name]
  {:test #(re-matches #"[A-Za-z0-9_\-]*" %)
   :message (str field-name " may only contain letters, numbers, underscores, and dashes.")})

(def hint-alphanumeric_- "Only letters, numbers, underscores, and dashes allowed")

(defn max-string-length [field-name length]
  {:test #(<= (.-length %) length)
   :message (str field-name " must be no more than " length " characters.")})

(defn alphanumeric_-period [field-name]
  {:test #(re-matches #"[A-Za-z0-9_\-.]*" %)
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


