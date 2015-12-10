(ns org.pipo.database
  (:require [neko.data.sqlite :as db]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clj-time.local :as l]
            [clj-time.format :as f]))

(def pipo-schema
  (db/make-schema
   :name "pipo.db"
   :version 1
   :tables {:hours
            {:columns {:_id "integer primary key"
                       :type "text check(type in ('in','out')) not null default 'in'"
                       :time "integer not null default '0'"
                       }}}))

(def get-db-helper
  (memoize
    (fn [] (db/create-helper pipo-schema))))

(defn pipo-db [] (db/get-database (get-db-helper) :write))

(defn add-punch [type-str ^org.joda.time.DateTime unix-time]
  (db/insert (pipo-db) :hours {:type type-str
                               :time (c/to-epoch unix-time)}))

(defn punch-in [^org.joda.time.DateTime unix-time]
  (add-punch "in" unix-time))

(defn punch-out [^org.joda.time.DateTime unix-time]
  (add-punch "out" unix-time))

(defn get-punches [where-clause-str]
  (if (instance? String where-clause-str)
    (db/query (pipo-db) :hours "time >= 555")))

; (get-punches)
; (db/query-seq (pipo-db) :hours {:start [:or 555 (c/to-epoch(t/date-time 2012 3 4))]})
; (c/to-epoch (t/date-time 1998 4 25 12 12 12))
; (f/show-formatters)
; (add-punch (t/date-time 2015 3 4))
; (punch-in (t/date-time 2014 4 5))
; (punch-out (t/date-time 2015 4 5))
; (db/query-seq (pipo-db) :hours "time >= 555 AND type = 'in'")
