(ns org.pipo.utils
  (:require [neko.log :as log]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clj-time.format :as f]
            [clj-time.periodic :as p]))

(def datetime-formatter (f/formatter "yyyy-MM-dd HH:mm:ss.SSS"))
(def date-formatter (f/formatters :date))
(def hms-formatter (f/formatters :hour-minute-second))
(def daylist-formatter (f/formatter "E d.M."))

(defn date-to-str-full [^org.joda.time.DateTime date-time]
  (f/unparse datetime-formatter date-time))

(defn date-to-str-date [^org.joda.time.DateTime date-time]
  (f/unparse date-formatter date-time))

(defn date-to-str-hms [^org.joda.time.DateTime date-time]
  (f/unparse hms-formatter date-time))

(defn date-to-str-day [^org.joda.time.DateTime date-time]
  (f/unparse daylist-formatter date-time))

(defn previous-monday [^org.joda.time.DateTime dt]
  (t/minus dt (t/days (- (t/day-of-week dt) 1))))

(defn weeks-in-year [year]
  (if (= 53 (t/week-number-of-year
              (t/last-day-of-the-month
                (t/date-time year 12))))
    53
    52))

;; TODO: map as argument
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

;; TODO: map as argument
(defn week-from-week-number [week-nr year]
  (if (> week-nr (weeks-in-year year))
    (do
      (log/w "not that many weeks in year")
      nil)
    (take 7 (p/periodic-seq (monday-from-week-number week-nr year) (t/days 1)))))

;; TODO: map as argument
(defn get-next-week [week-nr year]
  (if (> (+ week-nr 1) (weeks-in-year year))
    {:week 1 :year (+ year 1)}
    {:week (+ week-nr 1) :year year}))

;; TODO: map as argument
(defn get-previous-week [week-nr year]
  (if (= week-nr 1)
    {:week (weeks-in-year (- year 1)) :year (- year 1)}
    {:week (- week-nr 1) :year year}))

(defn get-current-week []
  (let [today (t/now)]
    {:week (t/week-number-of-year today)
     :year (t/year today)}))

(defn long-to-hms [dt-long]
  (date-to-str-hms (c/from-long dt-long)))

(defn date-equals? [^org.joda.time.DateTime dt1 ^org.joda.time.DateTime dt2]
  (and (= (t/year dt1) (t/year dt2))
       (= (t/month dt1) (t/month dt2))
       (= (t/day dt1) (t/day dt2))))
