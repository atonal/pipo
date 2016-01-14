(ns org.pipo.prefs
  (:require [neko.data.shared-prefs :refer [defpreferences put]]
            [org.pipo.database :as db]
            [org.pipo.log :as log])
  (:import android.preference.PreferenceManager
           android.content.SharedPreferences))

(def ^:const STATE_IN "IN")
(def ^:const STATE_OUT "OUT")
(def ^:const SERVICE_RUNNING "RUNNING")
(def ^:const SERVICE_STOPPED "STOPPED")

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
    (if (= type-latest db/IN)
      (pref-set PREF_STATE STATE_IN)
      (pref-set PREF_STATE STATE_OUT))))

;; Functions that need to be called from service to update pref atom
(defn- update-pref-atom [ctx ^SharedPreferences pref-raw]
  (dorun
    (map (fn [[key val]]
           (swap! pipo-prefs assoc (keyword key) val))
         (.getAll pref-raw))))

(defn pref-set-service [ctx pref-name new-val]
  (let [pref-raw (PreferenceManager/getDefaultSharedPreferences ctx)]
    (-> (.edit pref-raw)
        (put (:key pref-name) new-val)
        .commit)
    (update-pref-atom ctx pref-raw)))

(defn update-state-service [ctx]
  (let [type-latest (db/get-type (db/get-latest-punch))]
    (if (= type-latest db/IN)
      (pref-set-service ctx PREF_STATE STATE_IN)
      (pref-set-service ctx PREF_STATE STATE_OUT))))
