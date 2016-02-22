(ns org.pipo.t-utils
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [clj-time.periodic :as p]
            [org.pipo.utils :as utils]))

(deftest date-to-str-full
  (is (= (utils/date-to-str-full (t/date-time 1998 1 3 12 33 24 123))
         "1998-01-03 12:33:24.123")))

(deftest date-to-str-date
  (is (= (utils/date-to-str-date (t/date-time 1998 1 3 12 33 24 123))
         "1998-01-03")))

(deftest date-to-str-hms
  (is (= (utils/date-to-str-hms (t/date-time 1998 1 3 12 33 24 123))
         "12:33:24")))

(deftest date-to-str-day
  (is (= (utils/date-to-str-day (t/date-time 2015 12 23 2 3 4))
         "Wed 23.12.")))

(deftest date-to-str-hour-decimal
  (is (= (utils/date-to-str-hour-decimal (t/date-time 2015 12 23 2 0 4))
         "2.00"))
  (is (= (utils/date-to-str-hour-decimal (t/date-time 2015 12 23 2 45 4))
         "2.75"))
  (is (= (utils/date-to-str-hour-decimal (t/date-time 2015 12 23 2 30 4))
         "2.50")))

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

(deftest date-equals?
  (is (= (utils/date-equals? (t/date-time 2014 1 1 1 1 1 1)
                             (t/date-time 2014 1 1 1 1 1 1))
         true))
  (is (= (utils/date-equals? (t/date-time 2014 1 1 1 1 1 1)
                             (t/date-time 2014 1 1 1 1 1 2))
         true))
  (is (= (utils/date-equals? (t/date-time 2014 1 1 1 1 1 1)
                             (t/date-time 2014 1 1 1 1 2 1))
         true))
  (is (= (utils/date-equals? (t/date-time 2014 1 1 1 1 1 1)
                             (t/date-time 2014 1 1 1 2 1 1))
         true))
  (is (= (utils/date-equals? (t/date-time 2014 1 1 1 1 1 1)
                             (t/date-time 2014 1 1 2 1 1 1))
         true))
  (is (= (utils/date-equals? (t/date-time 2014 1 1 1 1 1 1)
                             (t/date-time 2014 1 2 1 1 1 1))
         false))
  (is (= (utils/date-equals? (t/date-time 2014 1 1 1 1 1 1)
                             (t/date-time 2014 2 1 1 1 1 1))
         false))
  (is (= (utils/date-equals? (t/date-time 2014 1 1 1 1 1 1)
                             (t/date-time 2015 1 1 1 1 1 1))
         false)))

(deftest get-current-week-by-date
  (is (= (utils/get-current-week-by-date (t/date-time 2015 12 31))
         {:week 53 :year 2015}))
  (is (= (utils/get-current-week-by-date (t/date-time 2016 1 1))
         {:week 53 :year 2015})))

(deftest get-time
  (is (t/equal? (utils/get-local-time (t/date-time 5 5 5 6 59))
                (t/local-time 6 59)))
  (is (t/equal? (utils/get-local-time (t/date-time 5 5 5 7))
                (t/local-time 7)))
  (is (t/equal? (utils/get-local-time (t/date-time 5 5 5 23 55))
                (t/local-time 23 55))))

(deftest day-begin
  (is (t/equal? (utils/day-begin (t/date-time 2015 2 3 12 22 32 234))
                (t/date-time 2015 2 3 0 0 0 0))))

(deftest day-end
  (is (t/equal? (utils/day-end (t/date-time 2015 2 3 12 22 32 234))
                (t/date-time 2015 2 3 23 59 59 999))))
