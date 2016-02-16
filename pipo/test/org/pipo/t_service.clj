(ns org.pipo.t-service
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [org.pipo.utils :as utils]
            [org.pipo.service :as service]))

;; TODO: mock prefs/state to test while-in and while-out
(deftest time-to-get-location
  ;; Saturday
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 3 7 35 24 123)))
         false))
  ;; Sunday
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 4 7 35 24 123)))
         false))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 2 7 33 24 123)))
         false))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 2 7 35 24 123)))
         true))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 2 7 35 24 123)))
         true))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 2 9 35 24 123)))
         false))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 2 9 45 24 123)))
         true))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 2 15 55 24 123)))
         true))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 2 15 56 24 123)))
         false))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 2 17 30 24 123)))
         false))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 2 18 0 24 123)))
         true))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 2 22 0 24 123)))
         true))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 2 23 0 24 123)))
         false))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 2 3 0 24 123)))
         false))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 2 4 30 24 123)))
         false))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 2 4 0 24 123)))
         true)))

(deftest history-threshold?
  (is (= (service/history-threshold? '(1 1))
         false))
  (is (= (service/history-threshold? '(1 1 1))
         true))
  (is (= (service/history-threshold? '(1 1 2))
         false))
  (is (= (service/history-threshold? '(2 2 2 2))
         true)))

(deftest update-history
  (is (= (service/update-history 3 (atom '(1 1 1)))
         '(3 1 1)))
  (is (= (service/update-history 3 (atom '(1)))
         '(3 1)))
  (is (= (service/update-history 3 (atom nil))
         '(3))))
