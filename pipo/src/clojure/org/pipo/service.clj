(ns org.pipo.service
  (:require
    [neko.threading :refer [on-ui]]
    [neko.debug :refer [*a]]
    [neko.resource :as res]
    [neko.notify :refer [toast notification fire cancel]]
    [clj-time.core :as t]
    [clj-time.local :as l]
    [org.pipo.log :as log]
    [org.pipo.prefs :as prefs]
    [org.pipo.utils :as utils]
    [org.pipo.broadcastreceiver :as tick]
    [org.pipo.servicehandler :as handler]
    [org.pipo.database :as db]
    [org.pipo.location :as location])
  (:import [android.app Service Notification]
           android.preference.PreferenceManager
           [android.os  Message])
  (:gen-class
    :prefix "service-"
    :extends android.app.Service
    :state state
    :init init
    ))

(res/import-all)

(def ^:const RADIUS_M 100)
(def ^:const THRESHOLD_M 20)
(def ^:const MAX_UPDATES 3)
(def tick-receiver (atom nil))
(def update-count (atom 0))

;; TODO: define the interval change times only, construct the intervals from them
(defn interval_day_in []
  {:begin (t/local-time 7)
   :end (t/minus (t/local-time 9 30) (t/millis 1))})
(defn interval_day []
  {:begin (t/local-time 9 30)
   :end (t/minus (t/local-time 15 30) (t/millis 1))})
(defn interval_day_out []
  {:begin (t/local-time 15 30)
   :end (t/minus (t/local-time 17 30) (t/millis 1))})
(defn interval_evening []
  {:begin (t/local-time 17 30)
   :end (t/minus (t/local-time 22) (t/millis 1))})
(defn interval_dusk []
  {:begin (t/local-time 22)
   :end (t/minus (t/local-time 0) (t/millis 1))})
(defn interval_dawn []
  {:begin (t/local-time 0)
   :end (t/minus (t/local-time 7) (t/millis 1))})

(defn enough-updates []
  ;;TODO: timeout in addition to count
  (swap! update-count inc)
  (if (>= @update-count MAX_UPDATES)
    (do
      (reset! update-count 0)
      true)
    false))

(defn my-on-location-fn [^android.location.Location location]
  (let [latitude (.getLatitude ^android.location.Location location)
        longitude (.getLongitude ^android.location.Location location)
        dest-location (android.location.Location. "pipo")]
    (log/d "on-location thread id " (Thread/currentThread))
    (.setLatitude dest-location (prefs/pref-get prefs/PREF_DEST_LAT))
    (.setLongitude dest-location (prefs/pref-get prefs/PREF_DEST_LONG))
    (let [distance (.distanceTo location dest-location)]
      (log/d (str "distance: " distance))
      (cond (and (= (prefs/pref-get prefs/PREF_STATE) prefs/STATE_OUT)
                 (< distance RADIUS_M))
            (do
              (if (db/punch-in-gps (l/local-now))
                (do
                  (on-ui (toast "GPS punch in" :short))
                  (prefs/update-state))))
            (and (= (prefs/pref-get prefs/PREF_STATE) prefs/STATE_IN)
                 (> distance (+ RADIUS_M THRESHOLD_M)))
            (do
              (if (db/punch-out-gps (l/local-now))
                (do
                  (on-ui (toast "GPS punch out" :short))
                  (prefs/update-state))))
            :else
            (log/w (str "no GPS punch, state: " (prefs/pref-get prefs/PREF_STATE) ", distance: " distance))
            ))
    (if (enough-updates)
      (location/stop-location-updates))
    ))

(defn time-to-get-location [^org.joda.time.DateTime date-time]
  (let [now (utils/get-local-time date-time)]
    ;; TODO: use some kind of map/any function
    (cond (and (t/within? (:begin (interval_day_in)) (:end (interval_day_in)) now)
               (= 0 (mod (t/minute now) 5)))
          true
          (and (t/within? (:begin (interval_day)) (:end (interval_day)) now)
               (= 0 (mod (t/minute now) 15)))
          true
          (and (t/within? (:begin (interval_day_out)) (:end (interval_day_out)) now)
               (= 0 (mod (t/minute now) 5)))
          true
          (and (t/within? (:begin (interval_evening)) (:end (interval_evening)) now)
               (= 0 (t/minute now)))
          true
          (and (or (t/within? (:begin (interval_dusk)) (:end (interval_dusk)) now)
                   (t/within? (:begin (interval_dawn)) (:end (interval_dawn)) now))
               (and (= 0 (mod (t/hour now) 2))
                    (= 0 (t/minute now))))
          true
          :else false
          )))

(defn tick-func []
  (let [now (l/local-now)]
    (log/d "service tick thread id " (Thread/currentThread))
    (if (location/location-updates-running)
      (location/stop-location-updates))
    (log/d "time-to-get-location?" (utils/date-to-str-full now))
    (if (time-to-get-location now)
      (do
        (log/d "yes")
        (location/start-location-updates my-on-location-fn)
        (log/d "location updates started"))
      (log/d "no"))))

(defn -init []
  [[] (atom {:data "state-data"})])

;; little functions to safely set the fields.
(defn- setfield
  [this key value]
  (swap! (.state this) into {key value}))

(defn- getfield
  [this key]
  (@(.state this) key))

(defn- create-notification []
  (let [mynotification
        ^Notification (notification {:icon R$drawable/ic_launcher
                                     :ticker-text "Activate location updates"
                                     :content-title "PiPo"
                                     :content-text "Location updates are being sent"
                                     :action [:activity "org.pipo.MAIN"]})]
    ; Set the notification persistent
    (set! (. mynotification flags) android.app.Notification/FLAG_ONGOING_EVENT)
    (fire :notification-key mynotification)))

(defn service-init []
  [[] (atom {:service-handler nil})])

(defn service-onCreate [this]
  (let [thread (android.os.HandlerThread.
                 "LocationServiceThread")]
    (log/d "service create thread id " (Thread/currentThread))
    (utils/init-time-zone this)
    (.start thread)
    (setfield this :service-handler (org.pipo.servicehandler. (.getLooper thread)))
    (create-notification)
    (log/i "Service created")))

(defn service-onStartCommand [^org.pipo.service this intent flags start-id]
  (let [state (.state this)
        service-handler (getfield this :service-handler)
        msg (.obtainMessage service-handler) ]
    (prefs/pref-set prefs/PREF_STATE_SERVICE prefs/SERVICE_RUNNING)
    (log/i (str "Service id: " start-id " started"))
    ; (set! (.-arg1 msg) start-id)
    ; (.sendMessage service-handler msg)

    (location/start-location-updates my-on-location-fn (.getLooper service-handler))

    ; TICKs are handled in tick-func by the service-handler thread
    (reset! tick-receiver
            (tick/register-receiver this tick-func service-handler))
    Service/START_STICKY
    ))

(defn service-onDestroy [this]
  (cancel :notification-key)
  (prefs/pref-set prefs/PREF_STATE_SERVICE prefs/SERVICE_STOPPED)
  (log/i "Service destroyed")
  (location/stop-location-updates)
  (tick/unregister-receiver this @tick-receiver)
  (reset! tick-receiver nil)
  )
