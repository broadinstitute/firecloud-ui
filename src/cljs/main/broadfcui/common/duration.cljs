(ns broadfcui.common.duration
  (:require
   [broadfcui.utils :as utils]
   ))

(defn fuzzy-time [years months days hours minutes seconds]
  (cond (pos? years) (utils/maybe-pluralize years "year")
        (pos? months) (utils/maybe-pluralize months "month")
        (pos? days) (utils/maybe-pluralize days "day")
        (pos? hours) (utils/maybe-pluralize hours "hour")
        (pos? minutes) (utils/maybe-pluralize minutes "minute")
        (> seconds 30) "less than a minute"
        :else "a few seconds"))

(defn fuzzy-duration-ms
  ([start end] (fuzzy-duration-ms start end false))
  ([start end suffix?]
   (let [duration-ms (- start end)
         duration-date (js/Date. (Math/abs duration-ms))
         in-past? (pos? duration-ms)]
     (str (when (and suffix? (not in-past?)) "in ")
          (fuzzy-time (- (.getUTCFullYear duration-date) 1970)
                      (.getUTCMonth duration-date)
                      (dec (.getUTCDate duration-date))
                      (.getUTCHours duration-date)
                      (.getUTCMinutes duration-date)
                      (.getUTCSeconds duration-date))
          (when (and suffix? in-past?) " ago")))))

(defn fuzzy-time-from-now-ms [time-ms suffix?]
  (fuzzy-duration-ms (.now js/Date) time-ms suffix?))
