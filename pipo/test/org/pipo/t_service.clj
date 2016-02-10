(ns org.pipo.t-service
  (:require [clojure.test :refer :all]
            [clj-time.core :as t]
            [org.pipo.utils :as utils]
            [org.pipo.service :as service]))

(deftest time-to-get-location
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 3 7 33 24 123)))
         false))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 3 7 35 24 123)))
         true))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 3 9 35 24 123)))
         false))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 3 9 45 24 123)))
         true))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 3 15 55 24 123)))
         true))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 3 15 56 24 123)))
         false))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 3 17 30 24 123)))
         false))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 3 18 0 24 123)))
         true))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 3 22 0 24 123)))
         true))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 3 23 0 24 123)))
         false))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 3 3 0 24 123)))
         false))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 3 4 30 24 123)))
         false))
  (is (= (service/time-to-get-location (utils/from-local-time-zone
                                         (t/date-time 1998 1 3 4 0 24 123)))
         true)))
