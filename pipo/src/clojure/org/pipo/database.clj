(ns org.pipo.database
  (:require [neko.data.sqlite :as db]
            [org.pipo.log :as log]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clj-time.local :as l]
            [clj-time.periodic :as p]
            [org.pipo.utils :as utils])
  (:import android.database.sqlite.SQLiteDatabase
           neko.data.sqlite.TaggedDatabase))

(def ^:const IN "in")
(def ^:const OUT "out")
(def ^:const MANUAL "manual")
(def ^:const GPS "gps")
(def ^:const VALID "valid")
(def ^:const INVALID "invalid")
(def ^:const LUNCH "lunch")
(def ^:const NO_LUNCH "no lunch")
(def ^:const HOURS_FOR_LUNCH_MILLIS 21600000) ; 6 h
(def ^:const LUNCH_BREAK_MILLIS 1800000) ; 30 min

(def pipo-schema
  (db/make-schema
   :name "pipo.db"
   :version 1
   :tables {:punches
            {:columns
             {:_id "integer primary key"
              :type (str "text check(type in ('" IN "','" OUT "')) not null default '" IN "'")
              :method (str "text check(method in ('" MANUAL "','" GPS "')) not null default '" MANUAL "'")
              :time "long not null default '0'"}}
            :work
            {:columns
             {:_id "integer primary key"
              :date "text not null"
              :start_id "integer not null"
              :stop_id "integer not null"
              :lunch (str "text check(lunch in ('" LUNCH "','" NO_LUNCH "')) not null default '" NO_LUNCH "'")
              :validity (str "text check(validity in ('" VALID "','" INVALID "')) not null default '" VALID "'")
              }}}))

(defn get-id [entry-seq]
  (:_id entry-seq))

(defn get-start-id [entry-seq]
  (:start_id entry-seq))

(defn get-stop-id [entry-seq]
  (:stop_id entry-seq))

(defn get-type [entry-seq]
  (:type entry-seq))

(defn get-time [entry-seq]
  (:time entry-seq))

(defn get-date [entry-seq]
  (:date entry-seq))

(defn get-punch-method [entry-seq]
  (:method entry-seq))

(defn get-validity [entry-seq]
  (:validity entry-seq))

(defn get-lunch [entry-seq]
  (:lunch entry-seq))

(defn has-lunch [entry-seq]
  (= LUNCH (get-lunch entry-seq)))

(def get-db-helper
  (memoize
    (fn [] (db/create-helper pipo-schema))))

(defn pipo-db [] (db/get-database (get-db-helper) :write))

(defn add-punch [type-str method-str ^org.joda.time.DateTime punch-time]
  (log/d "add-punch:" type-str (utils/date-to-str-full punch-time))
  (db/insert (pipo-db) :punches {:type type-str
                                 :method method-str
                                 :time (c/to-long punch-time)}))

(defn get-punch-with-id [id]
  (first (db/query-seq (pipo-db) :punches {:_id id})))

(defn- punch-in [unix-time method]
  (let [id (add-punch IN method unix-time)]
    (if (< id 0)
      (log/e "punch-in failed"))
    (>= id 0)))

(defn punch-in-manual [unix-time]
  (punch-in unix-time MANUAL))

(defn punch-in-gps [unix-time]
  (punch-in unix-time GPS))

(defn get-punches-cursor [where-clause-str]
  (if (instance? String where-clause-str)
    (db/query (pipo-db) :punches where-clause-str)
    (do
      (log/w "get-punches-cursor - input not a string: " where-clause-str)
      nil)))

(defn get-punches-by-date-cursor [^org.joda.time.DateTime date]
  (get-punches-cursor
    (str "time BETWEEN " (c/to-long (t/floor date t/day)) " AND "
         (c/to-long (t/floor (t/plus date (t/days 1)) t/day)))))

;; includes lunch break!
(defn get-work-hours [work-seq]
  (let [duration (- (get-time (get-punch-with-id (get-stop-id work-seq)))
                    (get-time (get-punch-with-id (get-start-id work-seq))))]
    (if (has-lunch work-seq)
      (- duration LUNCH_BREAK_MILLIS)
      duration)))

;; does not include lunch break!
(defn get-work-duration
  ([start-id stop-id]
   (- (get-time (get-punch-with-id stop-id))
      (get-time (get-punch-with-id start-id)))))

(defn work-includes-lunch [start-id stop-id]
  (if (> (get-work-duration start-id stop-id) HOURS_FOR_LUNCH_MILLIS)
    true
    false))

(defn add-work [start-id stop-id]
  (log/d "add-work:" start-id stop-id)
  (db/insert (pipo-db) :work {:date (utils/date-to-str-date
                                       (c/from-long
                                         (get-time
                                           (get-punch-with-id start-id))))
                               :start_id start-id
                               :stop_id stop-id
                               :lunch (if (work-includes-lunch start-id stop-id)
                                        LUNCH
                                        NO_LUNCH)
                               :validity VALID
                               }))

(defn get-work-cursor [where-clause-str]
  (if (instance? String where-clause-str)
    (db/query (pipo-db) :work where-clause-str)
    (do
      (log/w "get-work - input not a string: " where-clause-str)
      nil)))

(defn get-work [where-clause-str]
  (db/seq-cursor (get-work-cursor where-clause-str)))

(defn get-work-by-date-cursor [^org.joda.time.DateTime date]
  (get-work-cursor (str "date = '" (utils/date-to-str-date date) "'")))

(defn get-work-by-date [^org.joda.time.DateTime date]
  (db/seq-cursor (get-work-by-date-cursor date)))

(defn get-latest-punch []
  (first (db/query-seq (pipo-db) :punches "time in (select max(time) from punches)")))

(defn get-latest-punch-type [type-str]
  (first
    (db/query-seq
      (pipo-db)
      :punches
      (str "time in (select max(time) from punches where type = '" type-str "')"))))

(defn get-latest-punch-in []
  (get-latest-punch-type IN))

(defn get-latest-punch-out []
  (get-latest-punch-type OUT))

(defn wipe []
  (.delete ^SQLiteDatabase (.db ^TaggedDatabase (pipo-db)) "punches" "1" nil)
  (.delete ^SQLiteDatabase (.db ^TaggedDatabase (pipo-db)) "work" "1" nil))

(defn punch-out [unix-time method]
  (let [out-id (add-punch OUT method unix-time)]
    (if (< out-id 0)
      (log/e "punch-out failed")
      (add-work (get-id (get-latest-punch-in)) out-id))))

(defn punch-out-manual [unix-time]
  (punch-out unix-time MANUAL))

(defn punch-out-gps [unix-time]
  (punch-out unix-time GPS))

; (get-punches 2)
; (db/query-seq (pipo-db) :punches {:start [:or 555 (c/to-epoch(t/date-time 2012 3 4))]})
; (c/to-epoch (t/date-time 1998 4 25 12 12 12))
; (add-punch (t/date-time 2015 3 4))
; (punch-in (t/date-time 2014 4 5))
; (punch-out (t/date-time 2015 4 5))
; (db/query-seq (pipo-db) :punches "time >= 555 AND type = 'in'")
