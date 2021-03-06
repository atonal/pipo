(ns org.pipo.utils
  (:require [org.pipo.log :as log]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clj-time.format :as f]
            [clj-time.periodic :as p]
            [clj-time.local :as l]
            [clj-time.predicates :as pred])
  (:import
    java.util.TimeZone
    org.joda.time.DateTimeZone
    net.danlew.android.joda.JodaTimeAndroid))

(defn init-time-zone [ctx]
  (let [tzId (TimeZone/getDefault)]
    (JodaTimeAndroid/init ctx)
    (try
      (DateTimeZone/setDefault (DateTimeZone/forTimeZone tzId))
      (catch IllegalArgumentException e
        (log/w "Could not recognize timezone id \"" + tzId + "\"" e)))))

(defn datetime-full-formatter []
  (f/with-zone (f/formatter "yyyy-MM-dd HH:mm:ss.SSS") (t/default-time-zone)))

(defn datetime-formatter []
  (f/with-zone (f/formatters :mysql) (t/default-time-zone)))

(defn date-formatter []
  (f/with-zone (f/formatters :date) (t/default-time-zone)))

(defn hms-formatter []
  (f/formatters :hour-minute-second))

(defn hm-formatter []
  (f/formatters :hour-minute))

(defn daylist-formatter []
  (f/with-zone (f/formatter "E d.M.") (t/default-time-zone)))

;; TODO: get time zone offset:
; (/ (.getOffset (java.util.TimeZone/getDefault) (c/to-long (l/local-now))) 3600000)
; (/ (.getRawOffset (java.util.TimeZone/getDefault)) 3600000)

;; TODO: rename to-local-time(-zone)
(defn to-local-time-zone [^org.joda.time.DateTime date-time]
  (t/to-time-zone date-time (t/default-time-zone)))

(defn from-local-time-zone [^org.joda.time.DateTime date-time]
  (t/from-time-zone date-time (t/default-time-zone)))

(defn get-local-time [^org.joda.time.DateTime date-time]
  (t/local-time (t/hour date-time)
                (t/minute date-time)
                (t/second date-time)
                (t/milli date-time)))

(defn date-to-str-full [^org.joda.time.DateTime date-time]
  (f/unparse (datetime-full-formatter) date-time))

(defn date-to-str [^org.joda.time.DateTime date-time]
  (f/unparse (datetime-formatter) date-time))

(defn date-to-str-date [^org.joda.time.DateTime date-time]
  (f/unparse (date-formatter) date-time))

(defn str-to-date-date [date-str]
  (f/parse (date-formatter) date-str))

(defn date-to-str-hms [^org.joda.time.DateTime date-time]
  (f/unparse (hms-formatter) date-time))

(defn date-to-str-hm [^org.joda.time.DateTime date-time]
  (f/unparse (hm-formatter) date-time))

(defn date-to-str-day [^org.joda.time.DateTime date-time]
  (f/unparse (daylist-formatter) date-time))

(defn date-to-str-hour-decimal [^org.joda.time.DateTime date-time]
  (str (t/hour date-time) "."
       (format "%02d" (int (/ (* 100 (t/minute date-time)) 60)))))

(defn previous-monday [^org.joda.time.DateTime dt]
  (t/minus dt (t/days (dec (t/day-of-week dt)))))

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
          weeks-to-step (if (> (t/day-of-week years-first-day) 4) week-nr (dec week-nr))]
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
  (if (> (inc week-nr) (weeks-in-year year))
    {:week 1 :year (+ year 1)}
    {:week (+ week-nr 1) :year year}))

;; TODO: map as argument
(defn get-previous-week [week-nr year]
  (if (= week-nr 1)
    {:week (weeks-in-year (- year 1)) :year (- year 1)}
    {:week (- week-nr 1) :year year}))

(defn get-current-week-by-date [^org.joda.time.DateTime date]
  (let [week-nr (t/week-number-of-year date)
        year (if (and (>= week-nr 52) (pred/january? date))
               (dec (t/year date))
               (t/year date))]
    {:week week-nr
     :year year}))

(defn get-current-week []
  (get-current-week-by-date (l/local-now)))

(defn long-to-hms [dt-long]
  (date-to-str-hms (c/from-long dt-long)))

(defn long-to-hm [dt-long]
  (date-to-str-hm (c/from-long dt-long)))

(defn long-to-decimal [dt-long]
  (date-to-str-hour-decimal (c/from-long dt-long)))

(defn date-equals? [^org.joda.time.DateTime dt1 ^org.joda.time.DateTime dt2]
  (and (= (t/year dt1) (t/year dt2))
       (= (t/month dt1) (t/month dt2))
       (= (t/day dt1) (t/day dt2))))

(defn day-begin [^org.joda.time.DateTime date]
  (t/floor date t/day))

(defn day-end [^org.joda.time.DateTime date]
  (t/minus (t/floor (t/plus date (t/days 1)) t/day) (t/millis 1)))
