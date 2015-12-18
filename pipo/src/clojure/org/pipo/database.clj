(ns org.pipo.database
  (:require [neko.data.sqlite :as db]
            [neko.log :as log]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clj-time.local :as l]
            [clj-time.periodic :as p]
            [clj-time.format :as f]))

(def ^:const IN "in")
(def ^:const OUT "out")

(def time-formatter (f/formatter "yyyy-MM-dd HH:mm:ss.SSS"))

(defn time-to-str [^org.joda.time.DateTime date-time]
  (f/unparse time-formatter date-time))

(def pipo-schema
  (db/make-schema
   :name "pipo.db"
   :version 1
   :tables {:punches
            {:columns
             {:_id "integer primary key"
              :type (str "text check(type in ('" IN "','" OUT "')) not null default '" IN "'")
              :time "long not null default '0'"}}
            :hours
            {:columns
             {:_id "integer primary key"
              :start_id "integer not null"
              :stop_id "integer not null"}}}))

(defn get-id [entry-seq]
  (:_id entry-seq))

(defn get-type [entry-seq]
  (:type entry-seq))

(defn get-time [entry-seq]
  (:time entry-seq))

(def get-db-helper
  (memoize
    (fn [] (db/create-helper pipo-schema))))

(defn pipo-db [] (db/get-database (get-db-helper) :write))

(defn add-punch [type-str ^org.joda.time.DateTime punch-time]
  (log/d "add-punch:" type-str (time-to-str punch-time))
  (db/insert (pipo-db) :punches {:type type-str
                                 :time (c/to-long punch-time)}))

(defn get-punch-seq-id [id]
  (first (db/query-seq (pipo-db) :punches {:_id id})))

(defn punch-in [unix-time]
  (let [id (add-punch IN unix-time)]
    (if (< id 0)
      (log/e "punch-in failed"))))

(defn get-punches [where-clause-str]
  (if (instance? String where-clause-str)
    (db/query (pipo-db) :punches where-clause-str)
    (do
      (log/w "get-punches - input not a string: " where-clause-str)
      nil)))

(defn add-hours [start-id stop-id]
  (log/d "add-hours:" start-id stop-id)
  (db/insert (pipo-db) :hours {:start_id start-id
                               :stop_id stop-id}))

(defn get-hours [where-clause-str]
  (if (instance? String where-clause-str)
    (db/query (pipo-db) :hours where-clause-str)
    (do
      (log/w "get-hours - input not a string: " where-clause-str)
      nil)))

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
  (-> (pipo-db) .db (.delete "hours" "1" nil)))

(defn punch-out [unix-time]
  (let [out-id (add-punch OUT unix-time)]
    (if (< out-id 0)
      (log/e "punch-out failed")
      (add-hours (get-id (get-latest-punch-in)) out-id))))

; (get-punches 2)
; (db/query-seq (pipo-db) :punches {:start [:or 555 (c/to-epoch(t/date-time 2012 3 4))]})
; (c/to-epoch (t/date-time 1998 4 25 12 12 12))
; (f/show-formatters)
; (add-punch (t/date-time 2015 3 4))
; (punch-in (t/date-time 2014 4 5))
; (punch-out (t/date-time 2015 4 5))
; (db/query-seq (pipo-db) :punches "time >= 555 AND type = 'in'")
