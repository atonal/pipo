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
                  :type "in"
                  :method "manual"
                  :validity "valid"
                  :time (c/to-long punch-time)})))))

(deftest get-punch-with-id
  (let [punch-time (t/date-time 2001 1 1 12 00 00)]
    (pipo/add-punch pipo/IN pipo/MANUAL punch-time)
    (is (= (pipo/get-punch-with-id 1)
           {:_id 1
            :type "in"
            :method "manual"
            :validity "valid"
            :time (c/to-long punch-time)}))))

(deftest punch-in-manual
  (let [punch-time (t/date-time 2002 1 1 12 00 00)]
    (pipo/punch-in-manual punch-time)
    (is (= (pipo/get-punch-with-id 1)
           {:_id 1
            :type "in"
            :method "manual"
            :validity "valid"
            :time (c/to-long punch-time)}))))

(deftest punch-in-gps
  (let [punch-time (t/date-time 2003 1 1 12 00 00)]
    (pipo/punch-in-gps punch-time)
    (is (= (pipo/get-punch-with-id 1)
           {:_id 1
            :type "in"
            :method "gps"
            :validity "valid"
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
              :type "in"
              :method "manual"
              :validity "valid"
              :time (c/to-long punch3)}
             {:_id 2
              :type "in"
              :method "manual"
              :validity "valid"
              :time (c/to-long punch2)})))))

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
  (is (= (pipo/get-type {:some "fuu" :data "bar" :type "in" :more 555})
         "in"))
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
  (is (= (pipo/get-punch-method {:some "fuu" :data "bar" :method "manual" :more 555})
         "manual"))
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
