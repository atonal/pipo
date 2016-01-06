(ns org.pipo.t-database
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [org.pipo.database :as db]))

(deftest get-id
  (is (= (db/get-id {:some "fuu" :data "bar" :_id 234 :more 555})
         234))
  (is (= (db/get-id {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest get-start-id
  (is (= (db/get-start-id {:some "fuu" :data "bar" :start_id 12 :more 555})
         12))
  (is (= (db/get-start-id {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest get-stop-id
  (is (= (db/get-stop-id {:some "fuu" :data "bar" :stop_id 13 :more 555})
         13))
  (is (= (db/get-stop-id {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest get-type
  (is (= (db/get-type {:some "fuu" :data "bar" :type "in" :more 555})
         "in"))
  (is (= (db/get-type {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest get-time
  (is (= (db/get-time {:some "fuu" :data "bar" :time 123000 :more 555})
         123000))
  (is (= (db/get-time {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest get-date
  (is (= (db/get-date {:some "fuu" :data "bar" :date "2015-1-1" :more 555})
         "2015-1-1"))
  (is (= (db/get-date {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest get-punch-method
  (is (= (db/get-punch-method {:some "fuu" :data "bar" :method "manual" :more 555})
         "manual"))
  (is (= (db/get-punch-method {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest get-validity
  (is (= (db/get-validity {:some "fuu" :data "bar" :validity "invalid" :more 555})
         "invalid"))
  (is (= (db/get-validity {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest get-lunch
  (is (= (db/get-lunch {:some "fuu" :data "bar" :lunch "no lunch" :more 555})
         "no lunch"))
  (is (= (db/get-lunch {:some "fuu" :data "bar" :more 555})
         nil)))

(deftest has-lunch
  (is (= (db/has-lunch {:some "fuu" :data "bar" :lunch "no lunch" :more 555})
         false))
  (is (= (db/has-lunch {:some "fuu" :data "bar" :lunch "lunch" :more 555})
         true))
  (is (= (db/has-lunch {:some "fuu" :data "bar" :more 555})
         false)))

