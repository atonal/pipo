(ns org.pipo.t-utils
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [clj-time.periodic :as p]
            [org.pipo.utils :as utils]))

(deftest time-to-str
  (is (= (utils/time-to-str (t/date-time 1998 1 3 12 33 24 123))
         "1998-01-03 12:33:24.123")))

(deftest date-to-str
  (is (= (utils/date-to-str (t/date-time 1998 1 3 12 33 24 123))
         "1998-01-03")))

(deftest previous-monday
  (is (t/equal? (utils/previous-monday (t/date-time 2015 12 18))
                (t/date-time 2015 12 14)))
  (is (t/equal? (utils/previous-monday (t/date-time 2015 12 14))
                (t/date-time 2015 12 14))))

(deftest weeks-in-year
  (is (= (utils/weeks-in-year 2014) 52))
  (is (= (utils/weeks-in-year 2015) 53)))

(deftest monday-from-week-number
  (is (t/equal? (utils/monday-from-week-number 51 2015)
                (t/date-time 2015 12 14)))
  (is (t/equal? (utils/monday-from-week-number 1 2015)
                (t/date-time 2014 12 29))))

(deftest week-from-week-number
  (is (= (utils/week-from-week-number 52 2014))
    (take 7 (p/periodic-seq (t/date-time 2014 12 29) (t/days 1)))))

(deftest get-next-week
  (is (= (utils/get-next-week 52 2014)
         {:week 1 :year 2015}))
  (is (= (utils/get-next-week 1 2015)
         {:week 2 :year 2015})))

(deftest get-previous-week
  (is (= (utils/get-previous-week 2 2015)
         {:week 1 :year 2015})))
  (is (= (utils/get-previous-week 1 2015)
         {:week 52 :year 2014}))

(deftest long-to-hms
  (is (= (utils/long-to-hms 59000)
         "00:00:59"))
  (is (= (utils/long-to-hms 60000)
         "00:01:00"))
  (is (= (utils/long-to-hms 61000)
         "00:01:01"))
  (is (= (utils/long-to-hms 1800000)
         "00:30:00"))
  (is (= (utils/long-to-hms 3599000)
         "00:59:59"))
  (is (= (utils/long-to-hms 3600000)
         "01:00:00"))
  (is (= (utils/long-to-hms 3661000)
         "01:01:01")))
