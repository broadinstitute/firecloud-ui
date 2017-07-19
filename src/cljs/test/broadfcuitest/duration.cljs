(ns broadfcuitest.duration
  (:require
   [cljs.test :refer [deftest is testing]]
   [broadfcui.common.duration :as duration]
   [broadfcui.utils :as utils]
   ))

(deftest fuzzy-time

  (testing "no duration"
    (is (= "a few seconds"
           (duration/fuzzy-time 0 0 0 0 0 0))))
  (testing "few seconds"
    (is (= "a few seconds"
           (duration/fuzzy-time 0 0 0 0 0 (rand-int 30)))))
  (testing "almost a minute"
    (is (= "less than a minute"
           (duration/fuzzy-time 0 0 0 0 0 (+ (rand-int 28) 31)))))
  (let [random-60 (rand-int 58)
        random-12 (rand-int 11)
        random-31 (rand-int 30)
        random-1000 (+ 1 (rand-int 1000))]
    (testing "over a minute"
      (is (= (utils/maybe-pluralize (+ 1 random-60) "minute")
             (duration/fuzzy-time 0 0 0 0 (+ 1 random-60) random-60))))
    (testing "over an hour"
      (is (= (utils/maybe-pluralize (+ 1 random-12) "hour")
             (duration/fuzzy-time 0 0 0 (+ 1 random-12) random-60 random-60))))
    (testing "over a day"
      (is (= (utils/maybe-pluralize (+ 1 random-31) "day")
             (duration/fuzzy-time 0 0 (+ 1 random-31) random-12 random-60 random-60))))
    (testing "over a month"
      (is (= (utils/maybe-pluralize (+ 1 random-12) "month")
             (duration/fuzzy-time 0 (+ 1 random-12) random-31 random-12 random-60 random-60))))
    (testing "over a year"
      (is (= (utils/maybe-pluralize random-1000 "year")
             (duration/fuzzy-time random-1000 random-12 random-31 random-12 random-60 random-60))))))

(deftest fuzzy-duration-ms
  (let [start-time (.now js/Date)
        one-hour-later (+ start-time (* 1000 60 60))
        one-hour-earlier (- start-time (* 1000 60 60))]
    (testing "an hour in the future, no suffix"
      (is (= "1 hour"
             (duration/fuzzy-duration-ms start-time one-hour-later))))
    (testing "an hour in the future, with suffix"
      (is (= "in 1 hour"
             (duration/fuzzy-duration-ms start-time one-hour-later true))))
    (testing "an hour in the past, no suffix"
      (is (= "1 hour"
             (duration/fuzzy-duration-ms start-time one-hour-earlier))))
    (testing "an hour in the past, with suffix"
      (is (= "1 hour ago"
             (duration/fuzzy-duration-ms start-time one-hour-earlier true))))))

(deftest fuzzy-time-from-now
  (let [one-hour-from-now #(+ (.now js/Date) (* 1000 60 61))
        one-hour-before-now #(- (.now js/Date) (* 1000 60 61))]
    (testing "an hour and 1 minute in the future, no suffix"
      (is (= "1 hour"
             (duration/fuzzy-time-from-now-ms (one-hour-from-now) false))))
    (testing "an hour and 1 minute in the future, with suffix"
      (is (= "in 1 hour"
             (duration/fuzzy-time-from-now-ms (one-hour-from-now) true))))
    (testing "an hour and 1 minute in the past, no suffix"
      (is (= "1 hour"
             (duration/fuzzy-time-from-now-ms (one-hour-before-now) false))))
    (testing "an hour and 1 minute in the past, with suffix"
      (is (= "1 hour ago"
             (duration/fuzzy-time-from-now-ms (one-hour-before-now) true))))))

;(cljs.test/run-tests)
