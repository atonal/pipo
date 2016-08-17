(ns org.pipo.location
  (:require [neko.context :refer [get-service]]
            [neko.threading :refer [on-ui]]
            [neko.notify :refer [toast]]
            [org.pipo.log :as log]
            )
  (:import android.os.Looper))

(def ^:const UPDATE_INTERVAL_MS 5000)
(def ^:const UPDATE_DISTANCE_M 0)
(defonce location-state (atom {}))
(def location-data (atom {:lat "" :long ""}))

(defn- get-location-state [key]
  (@location-state key))

(defn- update-location-state [key val]
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

(defn- init-location-manager []
  (if-not (get-location-state :manager)
    (update-location-state :manager (get-service :location))))

(defn- init-location-listener [on-location-fn]
  (if-not (get-location-state :listener)
    (update-location-state
      :listener
      (proxy [android.location.LocationListener] []
        (onLocationChanged [^android.location.Location location]
          (let [latitude (.getLatitude location)
                longitude (.getLongitude location)]
            (set-location latitude longitude)
            (on-location-fn location)
            ))
        (onProviderDisabled [^String provider] ())
        (onProviderEnabled [^String provider] ())
        (onStatusChanged [^String provider status ^android.os.Bundle extras] ())))))

(defn- reset-location-listener []
  (update-location-state :listener nil))

(defn get-location-data []
  location-data)

(defn location-updates-running []
  ;; TODO: need to check if listener is enabled?
  (and (not (nil? (get-location-state :manager)))
       (not (nil? (get-location-state :listener)))))

(defn start-location-updates [on-location-fn & looper]
  (let [^Looper looper-thread (first looper)] ;; looper or nil
    (init-location-manager)
    (init-location-listener on-location-fn)
    (.requestLocationUpdates
      ^android.location.LocationManager (get-location-state :manager)
      android.location.LocationManager/GPS_PROVIDER
      (long UPDATE_INTERVAL_MS)
      (float UPDATE_DISTANCE_M)
      ^android.location.LocationListener (get-location-state :listener)
      looper-thread)))

(defn stop-location-updates []
  (when (location-updates-running)
    (.removeUpdates
      ^android.location.LocationManager (get-location-state :manager)
      ^android.location.LocationListener (get-location-state :listener))
    (reset-location-listener)))
