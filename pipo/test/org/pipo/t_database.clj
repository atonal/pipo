(ns org.pipo.t-database
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [org.pipo.database :as db]))

(deftest time-to-str
  (is (= (db/time-to-str (t/date-time 1998 1 3 12 33 24)) "1998-01-03 12:33:24")))
