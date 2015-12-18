(ns org.pipo.utils
  (:require [neko.log :as log]
            [clj-time.core :as t]
            [clj-time.periodic :as p]))

(defn previous-monday [^org.joda.time.DateTime dt]
  (t/minus dt (t/days (- (t/day-of-week dt) 1))))

(defn weeks-in-year [year]
  (if (= 53 (t/week-number-of-year
              (t/last-day-of-the-month
                (t/date-time year 12))))
    53
    52))

(defn monday-from-week-number [week-nr year]
  (if (> week-nr (weeks-in-year year))
    (do
      (log/w "not that many weeks in year")
      nil)
    (let [years-first-day (t/date-time year)
          weeks-to-step (if (> (t/day-of-week years-first-day) 4) week-nr (- week-nr 1))]
      (t/plus
        (previous-monday years-first-day)
        (t/weeks weeks-to-step)))))

(defn week-from-week-number [week-nr year]
  (if (> week-nr (weeks-in-year year))
    (do
      (log/w "not that many weeks in year")
      nil)
    (take 7 (p/periodic-seq (monday-from-week-number week-nr year) (t/days 1)))))

(defn get-next-week [week-nr year]
  (if (> (+ week-nr 1) (weeks-in-year year))
    {:week 1 :year (+ year 1)}
    {:week (+ week-nr 1) :year year}))

; (get-next-week 52 2014)
; (map time-to-str (week-from-week-number 53 2014))
; (weeks-in-year 2014)
; (time-to-str (monday-from-week-number 53 2014))
; (t/week-number-of-year (monday-from-week-number 53 2014))
; (t/week-number-of-year (t/last-day-of-the-month (t/date-time 2015 12)))
; (t/week-number-of-year (t/date-time 2016))
; (t/week-number-of-year (t/now))
; (t/day-of-week (t/now))

