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

(defpreferences pipo-state "pipo_sp_state")
(defpreferences pipo-service "pipo_sp_service")
(defpreferences pipo-yearweek "pipo_sp_yearweek")
(defpreferences pipo-latlong "pipo_sp_latlong")
(defpreferences pipo-fmt "pipo_sp_fmt")

; TODO: get the key from the pref-name
(defmacro defpref [pref-name pref-key default pref]
  `(def ~(vary-meta pref-name assoc :tag `:const)
     {:key ~pref-key :default ~default :pref ~pref}))

(defpref PREF_STATE :state STATE_OUT pipo-state)
(defpref PREF_STATE_SERVICE :state-service SERVICE_STOPPED pipo-service)
(defpref PREF_YEAR :year 2015 pipo-yearweek)
(defpref PREF_WEEK :week 51 pipo-yearweek)
(defpref PREF_DEST_LAT :lat 0 pipo-latlong)
(defpref PREF_DEST_LONG :long 0 pipo-latlong)
(defpref PREF_HOUR_FORMATTER :fmt "hm" pipo-fmt)

(defn pref-get [pref-name & pref-state]
  (let [pref (or (first pref-state) @(:pref pref-name))]
    (or
      ((:key pref-name) pref)
      (:default pref-name))))

(defn pref-set-named [pref-atom pref-name new-val]
  (if (not= new-val (pref-get pref-name))
    (swap! pref-atom assoc (:key pref-name) new-val)))

(defn pref-set [pref-name new-val]
  (log/i "set pref to: " new-val)
  (pref-set-named (:pref pref-name) pref-name new-val))

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
