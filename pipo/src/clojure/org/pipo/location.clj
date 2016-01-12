(ns org.pipo.location
  (:require [neko.context :refer [get-service]]
            [neko.threading :refer [on-ui]]
            [neko.notify :refer [toast]]
            [neko.log :as log]
            [clj-time.local :as l]
            [org.pipo.prefs :as prefs]
            [org.pipo.database :as db]
            ))

(def ^:const UPDATE_INTERVAL_MS 2000)
(def ^:const UPDATE_DISTANCE_M 0)
(def ^:const RADIUS_M 100)
(def ^:const THRESHOLD_M 20)

(defonce location-state (atom {}))
(def location-data (atom {:lat "" :long ""}))

(defn- get-state [key]
  (@location-state key))

(defn- update-state [key val]
  (swap! location-state assoc key val))

(defn- set-latitude [new-lat]
  (swap! location-data assoc :lat (str new-lat)))

(defn- set-longitude [new-long]
  (swap! location-data assoc :long (str new-long)))

(defn get-latitude []
  (or (:lat @location-data) "unknown"))

(defn get-longitude []
  (or (:long @location-data) "unknown"))

(defn- set-location [latitude longitude]
  (set-latitude latitude)
  (set-longitude longitude))

(defn my-on-location-fn [ctx ^android.location.Location location]
  (let [latitude (.getLatitude ^android.location.Location location)
        longitude (.getLongitude ^android.location.Location location)
        dest-location (android.location.Location. "pipo")]
    (set-location latitude longitude)
    (.setLatitude dest-location (prefs/pref-get prefs/PREF_DEST_LAT))
    (.setLongitude dest-location (prefs/pref-get prefs/PREF_DEST_LONG))
    (let [distance (.distanceTo location dest-location)]
      (on-ui (toast (str "distance: " distance) :short))
      (cond (and (= (prefs/pref-get prefs/PREF_STATE) prefs/STATE_OUT)
                 (< distance RADIUS_M))
            (do
              (if (db/punch-in-gps (l/local-now))
                (do
                  (on-ui (toast "GPS punch in" :short))
                  (prefs/raw-in ctx)
                  (prefs/update-state-raw ctx))))
            (and (= (prefs/pref-get prefs/PREF_STATE) prefs/STATE_IN)
                 (> distance (+ RADIUS_M THRESHOLD_M)))
            (do
              (if (db/punch-out-gps (l/local-now))
                (do
                  (on-ui (toast "GPS punch out" :short))
                  (prefs/raw-out ctx)
                  (prefs/update-state-raw ctx))))
            :else
            (log/w (str "no GPS punch, state: " (prefs/pref-get prefs/PREF_STATE) ", distance: " distance))
            ))))

(defn- on-location [ctx location]
  (my-on-location-fn ctx location))

(defn- init-location-manager []
  (if-not (get-state :manager)
    (update-state :manager (get-service :location))))

(defn- init-location-listener [ctx]
  (if-not (get-state :listener)
    (update-state
      :listener
      (proxy [android.location.LocationListener] []
        (onLocationChanged [^android.location.Location location]
          (on-location ctx location))
        (onProviderDisabled [^String provider] ())
        (onProviderEnabled [^String provider] ())
        (onStatusChanged [^String provider status ^android.os.Bundle extras] ())))))

(defn- reset-location-listener []
  (update-state :listener nil))

(defn get-location-data []
  location-data)

(defn start-location-updates [ctx]
  (init-location-manager)
  (init-location-listener ctx)
  (.requestLocationUpdates
    ^android.location.LocationManager (get-state :manager)
    android.location.LocationManager/GPS_PROVIDER
    (long UPDATE_INTERVAL_MS)
    (float UPDATE_DISTANCE_M)
    ^android.location.LocationListener (get-state :listener)))

(defn stop-location-updates []
  (if (and (get-state :manager)
           (get-state :listener))
    (.removeUpdates
      ^android.location.LocationManager (get-state :manager)
      ^android.location.LocationListener (get-state :listener))
    (reset-location-listener)))
