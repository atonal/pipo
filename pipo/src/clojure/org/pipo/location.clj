(ns org.pipo.location
  (:require [neko.context :refer [get-service]]
            [neko.threading :refer [on-ui]]
            [neko.notify :refer [toast]]
            ))

(def ^:const UPDATE_INTERVAL_MS 2000)
(def ^:const UPDATE_DISTANCE_M 0)

(defonce location-data (atom {}))

(defn get-state [key]
  (@location-data key))

(defn update-state [key val]
  (swap! location-data assoc key val))

(defn on-location [location ui-fn]
  (let [latitude (.getLatitude ^android.location.Location location)
        longitude (.getLongitude ^android.location.Location location)]
      (ui-fn latitude longitude)))

(defn init-location-manager []
  (if-not (get-state :manager)
    (update-state :manager (get-service :location))))

(defn init-location-listener [ui-fn]
  (if-not (get-state :listener)
    (update-state
      :listener
      (proxy [android.location.LocationListener] []
        (onLocationChanged [^android.location.Location location]
          (on-location location ui-fn))
        (onProviderDisabled [^String provider] ())
        (onProviderEnabled [^String provider] ())
        (onStatusChanged [^String provider status ^android.os.Bundle extras] ())))))

(defn reset-location-listener []
  (update-state :listener nil))

(defn start-location-updates [ui-fn]
  (init-location-manager)
  (init-location-listener ui-fn)
  (.requestLocationUpdates
    ^android.location.LocationManager (get-state :manager)
    android.location.LocationManager/GPS_PROVIDER
    (long UPDATE_INTERVAL_MS)
    (float UPDATE_DISTANCE_M)
    ^android.location.LocationListener (get-state :listener)))

(defn stop-location-updates []
  (if (and (get-state :manager) (get-state :istener))
    (.removeUpdates
      ^android.location.LocationManager (get-state :manager)
      ^android.location.LocationListener (get-state :listener))
    (reset-location-listener)))
