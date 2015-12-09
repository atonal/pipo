(ns org.pipo.database
  (:require [neko.data.sqlite :as db]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clj-time.local :as l]
            [clj-time.format :as f]))

(def db-schema
  (db/make-schema
   :name "pipo.db"
   :version 1
   :tables {:hours
            {:columns {:_id "text primary key"
                       :start "integer not null"
                       :stop "integer not null"
                       }}}))

(def get-db-helper
  (memoize
    (fn [] (db/create-helper db-schema))))

(defn hours-db [] (db/get-database (get-db-helper) :write))

(defn add-hours [^org.joda.time.DateTime start ^org.joda.time.DateTime stop]
  (db/insert (hours-db) :hours {:_id (str (java.util.UUID/randomUUID))
                        :start (c/to-epoch start)
                        :stop (c/to-epoch stop)}))

; (db/query-seq (hours-db) :hours {:start [:or 555 1234]})
; (db/query-seq (hours-db) :hours "start >= 555")
; (db/query-seq (hours-db) :hours {:start [:or 555 (c/to-epoch(t/date-time 2012 3 4))]})
; (c/to-long (t/now))
; (c/to-long (l/local-now))
; (c/to-epoch (t/date-time 1998 4 25 12 12 12))
; (t/now)
; (f/show-formatters)
; (add-hours (t/date-time 2012 3 4) (t/now))
