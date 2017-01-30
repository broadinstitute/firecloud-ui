(ns broadfcui.common.duration)

(defn- maybe-pluralize [number unit]
  (if (> number 1)
    (str number unit "s")
    (str number unit)))

(defn fuzzy-time [years months days hours minutes seconds]
  (cond (> years 0) (maybe-pluralize years " year")
        (> months 0) (maybe-pluralize months " month")
        (> days 1) (maybe-pluralize days " day")
        (> hours 0) (maybe-pluralize hours " hour")
        (> minutes 0) (maybe-pluralize minutes " minute")
        (> seconds 30) "less than a minute"
        :else "a few seconds"))

(defn fuzzy-duration-ms
  ([start end] (fuzzy-duration-ms start end false))
  ([start end suffix?]
   (let [duration-ms (- start end)
         duration-date (js/Date. (Math/abs duration-ms))
         in-past? (< 0 duration-ms)]
     (str (when (and suffix? (not in-past?)) "in ")
          (fuzzy-time (- (.getUTCFullYear duration-date) 1970)
                      (.getUTCMonth duration-date)
                      (.getUTCDate duration-date)
                      (.getUTCHours duration-date)
                      (.getUTCMinutes duration-date)
                      (.getUTCSeconds duration-date))
          (when (and suffix? in-past?) " ago")))))

(defn fuzzy-time-from-now-ms [time-ms suffix?]
  (fuzzy-duration-ms (.now js/Date) time-ms suffix?))
