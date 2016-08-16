(ns org.pipo.prefs
  (:require [neko.data.shared-prefs :refer [defpreferences put]]
            [org.pipo.database :as db]
            [org.pipo.utils :as utils]
            [org.pipo.log :as log]))

(def ^:const STATE_IN "IN")
(def ^:const STATE_OUT "OUT")
(def ^:const SERVICE_RUNNING "RUNNING")
(def ^:const SERVICE_STOPPED "STOPPED")
(def ^:const HOUR_FORMATTERS {:dec utils/long-to-decimal :hm utils/long-to-hm})


(defpreferences pipo-prefs "pipo_sp")

; TODO: get the key from the pref-name
(defmacro defpref [pref-name pref-key default]
  `(def ~(vary-meta pref-name assoc :tag `:const) {:key ~pref-key :default ~default}))

(defpref PREF_STATE :state STATE_OUT)
(defpref PREF_STATE_SERVICE :state-service SERVICE_STOPPED)
(defpref PREF_YEAR :year 2015)
(defpref PREF_WEEK :week 51)
(defpref PREF_DEST_LAT :lat 0)
(defpref PREF_DEST_LONG :long 0)
(defpref PREF_HOUR_FORMATTER :fmt "hm")

(defn get-prefs []
  (log/d "get-prefs" pipo-prefs)
  pipo-prefs)

(defn pref-set-named [pref-atom pref-name new-val]
  (swap! pref-atom assoc (:key pref-name) new-val))

(defn pref-set [pref-name new-val]
  (pref-set-named pipo-prefs pref-name new-val))

(defn pref-get [pref-name & pref-state]
  (let [pref (or (first pref-state) @pipo-prefs)]
    (or
      ((:key pref-name) pref)
      (:default pref-name))))

(defn update-state []
  (let [type-latest (db/get-type (db/get-latest-punch))]
    (log/d "lates punch:" (db/get-latest-punch))
    (if (= type-latest db/IN)
      (pref-set PREF_STATE STATE_IN)
      (pref-set PREF_STATE STATE_OUT))))

(defn get-hour-formatter-kw []
  (keyword (pref-get PREF_HOUR_FORMATTER)))

(defn get-hour-formatter []
  ((get-hour-formatter-kw) HOUR_FORMATTERS))

(defn set-hour-formatter [fmt]
  ;; pre is-string?
  (if (not (nil? ((keyword fmt) HOUR_FORMATTERS)))
    (pref-set PREF_HOUR_FORMATTER fmt)))

; TODO: more generic toggle function, if more that two formatters
(defn toggle-hour-formatter []
  (if (= :dec (get-hour-formatter-kw))
    (set-hour-formatter "hm")
    (set-hour-formatter "dec")))
