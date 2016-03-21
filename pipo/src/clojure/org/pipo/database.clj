(ns org.pipo.database
  (:require [neko.data.sqlite :as db]
            [org.pipo.log :as log]
            [clj-time.core :as t]
            [clj-time.local :as l]
            [clj-time.coerce :as c]
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
              :validity (str "text check(validity in ('" VALID "','" INVALID "')) not null default '" VALID "'")
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
                                 :validity VALID
                                 :time (c/to-long punch-time)}))

(defn get-punch-with-id [id]
  (first (db/query-seq (pipo-db) :punches {:_id id})))

(defn- punch-in [date-time method]
  (let [id (add-punch IN method date-time)]
    (if (< id 0)
      (log/e "punch-in failed"))
    (>= id 0)))

(defn punch-in-manual [date-time]
  (punch-in date-time MANUAL))

(defn punch-in-gps [date-time]
  (punch-in date-time GPS))

(defn get-punches-cursor [clause-str]
  (if (instance? String clause-str)
    (db/query (pipo-db) :punches clause-str)
    (do
      (log/w "get-punches-cursor - input not a string: " clause-str)
      nil)))

(defn get-punches-by-date-cursor [^org.joda.time.DateTime date]
  (get-punches-cursor
    (str "time BETWEEN " (c/to-long (utils/day-begin date)) " AND "
         (c/to-long (utils/day-end date))
         " ORDER BY time ASC" )))

(defn get-punches-by-date [^org.joda.time.DateTime date]
  (db/seq-cursor (get-punches-by-date-cursor date)))

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
  (db/insert
    (pipo-db)
    :work
    {:date (utils/date-to-str-date
             (utils/to-local-time-zone
               (c/from-long
                 (get-time
                   (get-punch-with-id start-id)))))
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

(defn get-latest-punch
  ([]
   (get-latest-punch (l/local-now)))
  ([date-time]
   (first
     (db/query-seq
       (pipo-db)
       :punches
       (str "time in (select max(time) from punches where validity = '" VALID "'"
            " and time <= " (c/to-long date-time) ")")))))


(defn get-latest-valid-punch [^org.joda.time.DateTime date]
  (first
    (db/query-seq
      (pipo-db)
      :punches
      (str "time in (select max(time) from punches where validity = '" VALID "'"
           " and time between " (c/to-long (utils/day-begin date)) " and "
           (c/to-long (utils/day-end date)) ")"))))

(defn get-latest-punch-type
  ([type-str]
   (get-latest-punch-type type-str (l/local-now)))
  ([type-str date-time]
  (first
    (db/query-seq
      (pipo-db)
      :punches
      (str "time in (select max(time) from punches where validity = '" VALID "'"
           " and type = '" type-str "' and time <= " (c/to-long date-time) ")")))))

(defn get-latest-punch-in []
  (get-latest-punch-type IN))

(defn get-latest-punch-out []
  (get-latest-punch-type OUT))

(defn get-time-since-latest-punch-in [^org.joda.time.DateTime date]
  (let [latest-punch (get-latest-valid-punch date)]
    (if (= (get-type latest-punch) IN)
      (let [diff (- (c/to-long date)
                    (get-time latest-punch))]
        (if (< diff 0)
          0
          diff
          ))
      0)))

(defn wipe []
  (.delete ^SQLiteDatabase (.db ^TaggedDatabase (pipo-db)) "punches" "1" nil)
  (.delete ^SQLiteDatabase (.db ^TaggedDatabase (pipo-db)) "work" "1" nil))

(defn wipe-work-by-date [^org.joda.time.DateTime date]
  (log/d "wipe-work-by-date" date)
  (.delete
    ^SQLiteDatabase (.db ^TaggedDatabase (pipo-db))
    "work"
    (str "date = '" (utils/date-to-str-date (utils/to-local-time-zone date)) "'") nil))

(defn construct-func []
  (let [state (atom "out")
        in-id (atom nil)]
    (fn [data]
      (let [punch-type (get-type data)
            punch-validity (get-validity data)
            punch-id (get-id data)]
        (log/d (str "data: " data))
        (log/d (str "state: " @state))
        (log/d (str "punch type: " punch-type))
        (log/d (str "punch validity " punch-validity))
        (if (= punch-validity VALID)
          (cond (and (= @state "out")
                     (= punch-type IN))
                (do
                  (log/d "work start")
                  (reset! state "in")
                  (reset! in-id punch-id)
                  (assoc data :action "work-start")
                  )
                (and (= @state "in")
                     (= punch-type OUT))
                (do
                  (log/d "work stop")
                  (reset! state "out")
                  (add-work @in-id punch-id)
                  (assoc data :action "work-stop")
                  )
                :else data)
          (do
            (log/d "punch not valid")
            data
            ))))))

(defn construct-work [punch-data]
  (log/d "construct-work" punch-data)
  (doall (map (construct-func) punch-data)))

(defn construct-work-for-date [^org.joda.time.DateTime date]
  (log/d "construct-work-for-date" date)
  (construct-work (get-punches-by-date date)))

(defn update-days-work [^org.joda.time.DateTime date]
  (log/d "update-days-work")
  (wipe-work-by-date date)
  (construct-work-for-date date))

(defn punch-out [date-time method]
  (let [out-id (add-punch OUT method date-time)]
    (if (< out-id 0)
      (log/e "punch-out failed")
      (update-days-work date-time))
    (>= out-id 0)))

(defn punch-out-manual [date-time]
  (punch-out date-time MANUAL))

(defn punch-out-gps [date-time]
  (punch-out date-time GPS))

(defn update-punch [id keyw value]
  (db/update (pipo-db) :punches {keyw value} {:_id id}))

(defn update-work [id keyw value]
  (db/update (pipo-db) :work {keyw value} {:_id id}))

(defn punch-toggle-validity [id]
  (if (= (get-validity (get-punch-with-id id)) VALID)
    (do
      (log/d (str "punch id " id " " VALID " -> " INVALID))
      (update-punch id :validity INVALID))
    (do
      (log/d (str "punch id " id " " INVALID " -> " VALID))
      (update-punch id :validity VALID))
    ))

(defn work-toggle-lunch [work-seq]
  (if (= (get-lunch work-seq) LUNCH)
    (do
      (log/d (str "work id " (get-id work-seq) " " LUNCH " -> " NO_LUNCH))
      (update-work (get-id work-seq) :lunch NO_LUNCH))
    (do
      (log/d (str "work id " (get-id work-seq) " " NO_LUNCH " -> " LUNCH))
      (update-work (get-id work-seq) :lunch LUNCH))))

; (wipe-work-by-date (t/now))

; (get-punches 2)
; (db/query-seq (pipo-db) :punches {:start [:or 555 (c/to-epoch(t/date-time 2012 3 4))]})
; (c/to-epoch (t/date-time 1998 4 25 12 12 12))
; (add-punch (t/date-time 2015 3 4))
; (punch-in (t/date-time 2014 4 5))
; (punch-out (t/date-time 2015 4 5))
; (db/query-seq (pipo-db) :punches "time >= 555 AND type = 'in'")
