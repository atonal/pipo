(ns org.pipo.prefs
  (:require [neko.data.shared-prefs :refer [defpreferences put]]
            [org.pipo.database :as db])
  (:import android.preference.PreferenceManager))

(def ^:const STATE_IN "IN")
(def ^:const STATE_OUT "OUT")

(defpreferences pipo-prefs "pipo_sp")

; TODO: get the key from the pref-name
(defmacro defpref [pref-name pref-key default]
  `(def ~(vary-meta pref-name assoc :tag `:const) {:key ~pref-key :default ~default}))

(defpref PREF_STATE :state STATE_OUT)
(defpref PREF_YEAR :year 2015)
(defpref PREF_WEEK :week 51)
(defpref PREF_DEST_LAT :lat 0)
(defpref PREF_DEST_LONG :long 0)

(defn get-prefs []
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

;; is update needed? at least if app is running, perhaps?
(defn update-state-raw [ctx]
  (let [pref-raw (PreferenceManager/getDefaultSharedPreferences ctx)]
    (map (fn [[key val]] (swap! pipo-prefs assoc key val))
         (reduce (fn [m [key val]] (assoc m (keyword key) val))
                 {} (.getAll pref-raw)))))

(defn raw-in [ctx]
  (let [pref-raw (PreferenceManager/getDefaultSharedPreferences ctx)]
    (-> (.edit pref-raw)
        (put (:key PREF_STATE) STATE_IN)
        .commit)))

(defn raw-out [ctx]
  (let [pref-raw (PreferenceManager/getDefaultSharedPreferences ctx)]
    (-> (.edit pref-raw)
        (put (:key PREF_STATE) STATE_OUT)
        .commit)))
