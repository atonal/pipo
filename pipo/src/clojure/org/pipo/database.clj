(ns org.pipo.database
  (:require [neko.data.sqlite :as db]))

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

(defn add-hours [start stop]
  (db/insert (hours-db) :hours {:_id (str (java.util.UUID/randomUUID))
                        :start start
                        :stop stop}))
