(ns org.pipo.t-database
  (:require [clojure.test :refer :all]
            [org.pipo.database :as pipo]
            [neko.data.sqlite :as db]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            )
  (:import
    [android.app Activity]
    org.robolectric.RuntimeEnvironment
    neko.App))

(defn db-fixture [f]
  (do
    (assert (= () (db/query-seq (pipo/pipo-db) :punches {})))
    (assert (= () (db/query-seq (pipo/pipo-db) :work {}))))
  (f)
  (pipo/wipe))

(use-fixtures :each db-fixture)

(deftest add-punch
  (let [punch-time (t/date-time 2000 1 1 12 00 00)]
    (pipo/add-punch pipo/IN pipo/MANUAL punch-time)
    (is (= (db/query-seq (pipo/pipo-db) :punches {:_id 1})
           (list {:_id 1
                  :type pipo/IN
                  :method pipo/MANUAL
                  :validity pipo/VALID
                  :time (c/to-long punch-time)})))))

(deftest get-punch-with-id
  (let [punch-time (t/date-time 2001 1 1 12 00 00)]
    (pipo/add-punch pipo/OUT pipo/MANUAL punch-time)
    (is (= (pipo/get-punch-with-id 1)
           {:_id 1
            :type pipo/OUT
            :method pipo/MANUAL
            :validity pipo/VALID
            :time (c/to-long punch-time)}))))

(deftest punch-in-manual
  (let [punch-time (t/date-time 2002 1 1 12 00 00)]
    (pipo/punch-in-manual punch-time)
    (is (= (pipo/get-punch-with-id 1)
           {:_id 1
            :type pipo/IN
            :method pipo/MANUAL
            :validity pipo/VALID
            :time (c/to-long punch-time)}))))

(deftest punch-in-gps
  (let [punch-time (t/date-time 2003 1 1 12 00 00)]
    (pipo/punch-in-gps punch-time)
    (is (= (pipo/get-punch-with-id 1)
           {:_id 1
            :type pipo/IN
            :method pipo/GPS
            :validity pipo/VALID
            :time (c/to-long punch-time)}))))

(deftest get-punches-by-date
  (let [punch1 (t/date-time 2000 1 1 12 00 00)
        punch2 (t/date-time 2000 1 2 13 00 00)
        punch3 (t/date-time 2000 1 2 12 00 00)
        punch4 (t/date-time 2000 1 3 12 00 00)]
    (pipo/add-punch pipo/IN pipo/MANUAL punch1)
    (pipo/add-punch pipo/IN pipo/MANUAL punch2)
    (pipo/add-punch pipo/IN pipo/MANUAL punch3)
    (pipo/add-punch pipo/IN pipo/MANUAL punch4)
    (is (= (pipo/get-punches-by-date (t/date-time 2000 1 2))
           (list
             {:_id 3
              :type pipo/IN
              :method pipo/MANUAL
              :validity pipo/VALID
              :time (c/to-long punch3)}
             {:_id 2
              :type pipo/IN
              :method pipo/MANUAL
              :validity pipo/VALID
              :time (c/to-long punch2)})))))

(deftest get-work-hours
  (let [punch1 (t/date-time 2000 1 1 12 00 00)
        punch2 (t/date-time 2000 1 1 13 00 00)]
    (pipo/add-punch pipo/IN pipo/MANUAL punch1)
    (pipo/add-punch pipo/IN pipo/MANUAL punch2)
    (is (= (pipo/get-work-hours {:_id 1
                                 :date "2000-01-01"
                                 :start_id 1
                                 :stop_id 2
                                 :lunch pipo/NO_LUNCH
                                 :validity pipo/VALID
                                 })
           (* 1000 60 60)))
    (is (= (pipo/get-work-hours {:_id 1
                                 :date "2000-01-01"
                                 :start_id 1
                                 :stop_id 2
                                 :lunch pipo/LUNCH
                                 :validity pipo/VALID
                                 })
           (- (* 1000 60 60) pipo/LUNCH_BREAK_MILLIS)))))

(deftest get-work-duration
  (let [punch1 (t/date-time 2000 1 1 12 00 00)
        punch2 (t/date-time 2000 1 1 13 00 00)
        punch3 (t/date-time 2000 1 1 20 00 00)]
    (pipo/add-punch pipo/IN pipo/MANUAL punch1)
    (pipo/add-punch pipo/OUT pipo/MANUAL punch2)
    (pipo/add-punch pipo/OUT pipo/MANUAL punch3)
    (is (= (pipo/get-work-duration 1 2)
           (* 1000 60 60)))
    (is (= (pipo/get-work-duration 1 3)
           (* 1000 60 60 8)))))

(deftest work-includes-lunch
  (let [punch1 (t/date-time 2000 1 1 10 00 00)
        punch2 (t/date-time 2000 1 1 15 59 59)
        punch3 (t/date-time 2000 1 1 16 00 00)]
    (pipo/add-punch pipo/IN pipo/MANUAL punch1)
    (pipo/add-punch pipo/OUT pipo/MANUAL punch2)
    (pipo/add-punch pipo/OUT pipo/MANUAL punch3)
    (is (= (pipo/work-includes-lunch 1 2)
           false))
    (is (= (pipo/work-includes-lunch 1 3)
           true))))

(deftest add-work
  (let [punch1 (t/date-time 2000 1 1 8 00 00)
        punch2 (t/date-time 2000 1 1 16 00 00)]
    (pipo/add-punch pipo/IN pipo/MANUAL punch1)
    (pipo/add-punch pipo/OUT pipo/MANUAL punch2)

    (is (= (db/query-seq (pipo/pipo-db) :work {:_id 1})
           '()))

    (pipo/add-work 1 2)

    (is (= (db/query-seq (pipo/pipo-db) :work {:_id 1})
           (list {:_id 1
                  :date "2000-01-01"
                  :start_id 1
                  :stop_id 2
                  :lunch pipo/LUNCH
                  :validity pipo/VALID})))))

(deftest get-work-by-date
  (let [punch1 (t/date-time 2000 1 1 8 00 00)
        punch2 (t/date-time 2000 1 1 13 00 00)
        punch3 (t/date-time 2000 1 1 14 00 00)
        punch4 (t/date-time 2000 1 1 20 00 00)]
    (pipo/punch-in-manual punch1)
    (pipo/punch-out-manual punch2)
    (pipo/punch-in-manual punch3)
    (pipo/punch-out-manual punch3)
    (is (= (pipo/get-work-by-date (t/date-time 2000 1 1))
           (list {:_id 1
                  :date "2000-01-01"
                  :start_id 1
                  :stop_id 2
                  :lunch pipo/NO_LUNCH
                  :validity pipo/VALID}
                 {:_id 2
                  :date "2000-01-01"
                  :start_id 3
                  :stop_id 4
                  :lunch pipo/NO_LUNCH
                  :validity pipo/VALID})))))

(deftest get-id
  (is (= (pipo/get-id {:some "fuu" :data "bar" :_id 234 :more 555})
         234))
  (is (= (pipo/get-id {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest get-start-id
  (is (= (pipo/get-start-id {:some "fuu" :data "bar" :start_id 12 :more 555})
         12))
  (is (= (pipo/get-start-id {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest get-stop-id
  (is (= (pipo/get-stop-id {:some "fuu" :data "bar" :stop_id 13 :more 555})
         13))
  (is (= (pipo/get-stop-id {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest get-type
  (is (= (pipo/get-type {:some "fuu" :data "bar" :type pipo/IN :more 555})
         pipo/IN))
  (is (= (pipo/get-type {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest get-time
  (is (= (pipo/get-time {:some "fuu" :data "bar" :time 123000 :more 555})
         123000))
  (is (= (pipo/get-time {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest get-date
  (is (= (pipo/get-date {:some "fuu" :data "bar" :date "2015-1-1" :more 555})
         "2015-1-1"))
  (is (= (pipo/get-date {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest get-punch-method
  (is (= (pipo/get-punch-method {:some "fuu" :data "bar" :method pipo/MANUAL :more 555})
         pipo/MANUAL))
  (is (= (pipo/get-punch-method {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest get-validity
  (is (= (pipo/get-validity {:some "fuu" :data "bar" :validity "invalid" :more 555})
         "invalid"))
  (is (= (pipo/get-validity {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest get-lunch
  (is (= (pipo/get-lunch {:some "fuu" :data "bar" :lunch "no lunch" :more 555})
         "no lunch"))
  (is (= (pipo/get-lunch {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest has-lunch
  (is (= (pipo/has-lunch {:some "fuu" :data "bar" :lunch "no lunch" :more 555})
         false))
  (is (= (pipo/has-lunch {:some "fuu" :data "bar" :lunch "lunch" :more 555})
         true))
  (is (= (pipo/has-lunch {:some "fuu" :data "bar" :more 555})
         false)))
