(ns org.pipo.service
  (:require
    [neko.threading :refer [on-ui]]
    [neko.debug :refer [*a]]
    [neko.resource :as res]
    [neko.notify :refer [toast notification fire cancel]]
    [clj-time.local :as l]
    [org.pipo.log :as log]
    [org.pipo.prefs :as prefs]
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
(def ^:const MAX_UPDATES 1)
(def tick-receiver (atom nil))
(def update-count (atom 0))

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
      (on-ui (toast (str "distance: " distance) :short))
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

(defn time-to-get-location []
  true)

(defn tick-func []
  (log/d "service tick thread id " (Thread/currentThread))
  (on-ui (toast (str "Time changed! (from Service)") :short))
  (if (and (time-to-get-location)
           ;; TODO: If already running, check if need to change update parameters
           (not (location/location-updates-running)))
    (do
      (location/start-location-updates my-on-location-fn)
      (log/d "location updates started")))
  )

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
    (.start thread)
    (setfield this :service-handler (org.pipo.servicehandler. (.getLooper thread)))
    (create-notification)
    (on-ui (toast "Service created" :short))))

(defn service-onStartCommand [^org.pipo.service this intent flags start-id]
  (let [state (.state this)
        service-handler (getfield this :service-handler)
        msg (.obtainMessage service-handler) ]
    (prefs/pref-set prefs/PREF_STATE_SERVICE prefs/SERVICE_RUNNING)
    (on-ui (toast (str "Service id: " start-id " started") :short))
    ; (set! (.-arg1 msg) start-id)
    ; (.sendMessage service-handler msg)

    ; TICKs are handled in tick-func by the service-handler thread
    (reset! tick-receiver
            (tick/register-receiver this tick-func service-handler))
    Service/START_STICKY
    ))

(defn service-onDestroy [this]
  (cancel :notification-key)
  (prefs/pref-set prefs/PREF_STATE_SERVICE prefs/SERVICE_STOPPED)
  (on-ui (toast "Service destroyed" :short))
  (location/stop-location-updates)
  (tick/unregister-receiver this @tick-receiver)
  (reset! tick-receiver nil)
  )
