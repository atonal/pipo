(ns org.pipo.database
  (:require [neko.data.sqlite :as db]
            [neko.log :as log]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clj-time.local :as l]
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
   :tables {:hours
            {:columns {:_id "integer primary key"
                       :type (str "text check(type in ('" IN "','" OUT "')) not null default '" IN "'")
                       :time "long not null default '0'"
                       }}}))

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
  (db/insert (pipo-db) :hours {:type type-str
                               :time (c/to-long punch-time)}))

(defn punch-in [unix-time]
  (add-punch IN unix-time))

(defn punch-out [unix-time]
  (add-punch OUT unix-time))

(defn get-punches [where-clause-str]
  (if (instance? String where-clause-str)
    (db/query (pipo-db) :hours where-clause-str)
    (do
      (log/w "get-punches - input not a string: " where-clause-str)
      nil)))

(defn get-latest-punch []
  (db/query-seq (pipo-db) :hours "time in (select max(time) from hours)"))

(defn get-latest-punch-type [type-str]
  (db/query-seq
    (pipo-db)
    :hours
    (str "time in (select max(time) from hours where type = '" type-str "')")))

(defn get-latest-punch-in []
  (get-latest-punch-type IN))

(defn get-latest-punch-out []
  (get-latest-punch-type OUT))

(defn wipe []
  (-> (pipo-db) .db (.delete "hours" "1" nil)))

; (get-punches 2)
; (db/query-seq (pipo-db) :hours {:start [:or 555 (c/to-epoch(t/date-time 2012 3 4))]})
; (c/to-epoch (t/date-time 1998 4 25 12 12 12))
; (f/show-formatters)
; (add-punch (t/date-time 2015 3 4))
; (punch-in (t/date-time 2014 4 5))
; (punch-out (t/date-time 2015 4 5))
; (db/query-seq (pipo-db) :hours "time >= 555 AND type = 'in'")
