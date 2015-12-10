(ns org.pipo.database
  (:require [neko.data.sqlite :as db]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clj-time.local :as l]
            [clj-time.format :as f]))

(def pipo-single-schema
  (db/make-schema
   :name "pipo-single.db"
   :version 1
   :tables {:hours
            {:columns {:_id "integer primary key"
                       :type "text check(type in ('in','out')) not null default 'in'"
                       :time "integer not null default '0'"
                       }}}))

(def get-pipo-single-helper
  (memoize
    (fn [] (db/create-helper pipo-single-schema))))

(defn pipo-single-db [] (db/get-database (get-pipo-single-helper) :write))

(defn pipo-single-add [^org.joda.time.DateTime unix-time]
  (db/insert (pipo-single-db) :hours {:type "in"
                                      :time (c/to-epoch unix-time)}))

(defn pipo-single-get-punches []
  (db/query (pipo-single-db) :hours "time >= 555"))

; (pipo-single-get-punches)
; (db/query-seq (pipo-single-db) :hours {:start [:or 555 (c/to-epoch(t/date-time 2012 3 4))]})
; (c/to-long (t/now))
; (c/to-long (l/local-now))
; (c/to-epoch (t/date-time 1998 4 25 12 12 12))
; (t/now)
; (f/show-formatters)
; (pipo-single-add (t/date-time 2015 3 4))
; (db/query-seq (pipo-single-db) :hours "time >= 555 AND type = 'in'")
