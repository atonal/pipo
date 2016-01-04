(ns org.pipo.database
  (:require [neko.data.sqlite :as db]
            [neko.log :as log]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clj-time.local :as l]
            [clj-time.periodic :as p]
            [org.pipo.utils :as utils]))

(def ^:const IN "in")
(def ^:const OUT "out")
(def ^:const MANUAL "manual")
(def ^:const GPS "gps")
(def ^:const VALID "valid")
(def ^:const INVALID "invalid")

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
              :validity (str "text check(validity in ('" VALID "','" INVALID "')) not null default '" VALID "'")
              ; :validity "integer not null"
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

(defn get-punch-method [entry-seq]
  (:method entry-seq))

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
      (log/e "punch-in failed")
      id)))

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

(defn get-punches-cursor-by-date [^org.joda.time.DateTime date]
  (get-punches-cursor
    (str "time BETWEEN " (c/to-long (t/floor date t/day)) " AND "
         (c/to-long (t/floor (t/plus date (t/days 1)) t/day)))))

(defn add-work [start-id stop-id]
  (log/d "add-work:" start-id stop-id)
  (db/insert (pipo-db) :work {:date (utils/date-to-str-date
                                       (c/from-long
                                         (get-time
                                           (get-punch-with-id start-id))))
                               :start_id start-id
                               :stop_id stop-id
                               :validity VALID
                               }))

(defn get-work [where-clause-str]
  (if (instance? String where-clause-str)
    (db/query-seq (pipo-db) :work where-clause-str)
    (do
      (log/w "get-work - input not a string: " where-clause-str)
      nil)))

(defn get-work-cursor [where-clause-str]
  (if (instance? String where-clause-str)
    (db/query (pipo-db) :work where-clause-str)
    (do
      (log/w "get-work - input not a string: " where-clause-str)
      nil)))

(defn get-work-by-date [^org.joda.time.DateTime date]
  (get-work (str "date = '" (utils/date-to-str-date date) "'")))

(defn get-work-cursor-by-date [^org.joda.time.DateTime date]
  (get-work-cursor (str "date = '" (utils/date-to-str-date date) "'")))

;; in milliseconds?
(defn get-work-duration [work]
  (-
   (get-time (get-punch-with-id (get-stop-id work)))
   (get-time (get-punch-with-id (get-start-id work))))
  )

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
  (-> (pipo-db) .db (.delete "punches" "1" nil))
  (-> (pipo-db) .db (.delete "work" "1" nil)))

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
