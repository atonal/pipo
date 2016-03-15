(ns org.pipo.service
  (:require
    [neko.threading :refer [on-ui]]
    [neko.debug :refer [*a]]
    [neko.resource :as res]
    [neko.notify :refer [toast notification fire cancel]]
    [clj-time.core :as t]
    [clj-time.local :as l]
    [clj-time.predicates :as pred]
    [org.pipo.log :as log]
    [org.pipo.prefs :as prefs]
    [org.pipo.utils :as utils]
    [org.pipo.broadcastreceiver :as tick]
    [org.pipo.servicehandler :as handler]
    [org.pipo.database :as db]
    [org.pipo.location :as location])
  (:import [android.app Service Notification]
           android.preference.PreferenceManager
           [android.os Message])
  (:gen-class
    :prefix "service-"
    :extends android.app.Service
    :state state
    :init init
    ))

(res/import-all)

(def ^:const RADIUS_M 100)
(def ^:const THRESHOLD_M 20)
(def ^:const MAX_UPDATES 10)
(def ^:const HISTORY_LEN 3)
(def tick-receiver (atom nil))
(def update-count (atom 0))
(def history (atom nil))

(defn every-minute? [^org.joda.time.LocalTime local-time]
  true)

(defn every-five-minutes? [^org.joda.time.LocalTime local-time]
  (= 0 (mod (t/minute local-time) 5)))

(defn every-fifteen-minutes? [^org.joda.time.LocalTime local-time]
  (= 0 (mod (t/minute local-time) 15)))

(defn every-half-hour? [^org.joda.time.LocalTime local-time]
  (= 0 (mod (t/minute local-time) 30)))

(defn every-hour? [^org.joda.time.LocalTime local-time]
  (= 0 (t/minute local-time)))

(defn every-second-hour? [^org.joda.time.LocalTime local-time]
  (and (= 0 (mod (t/hour local-time) 2))
       (= 0 (t/minute local-time))))

(def intervals
  ;; TODO: Define in "HH:mm" format?
  [{:hour 0 :minute 0 :while-out every-second-hour? :while-in every-second-hour?}
   {:hour 7 :minute 0 :while-out every-five-minutes? :while-in every-fifteen-minutes?}
   {:hour 7 :minute 50 :while-out every-minute? :while-in every-fifteen-minutes?}
   {:hour 9 :minute 30 :while-out every-fifteen-minutes? :while-in every-half-hour?}
   {:hour 15 :minute 30 :while-out every-fifteen-minutes? :while-in every-five-minutes?}
   {:hour 17 :minute 30 :while-out every-hour? :while-in every-fifteen-minutes?}
   {:hour 22 :minute 0 :while-out every-second-hour? :while-in every-second-hour?}])

(defn get-freq-func [interval state]
  (if (= state prefs/STATE_IN)
    (:while-in interval)
    (:while-out interval)))

(defn get-intervals [coll]
  (let [rotated (take (count coll) (drop 1 (cycle coll)))]
    (map #(hash-map :begin %1 :end %2) coll rotated)))

(defn begin-time [begin]
  (t/local-time (:hour begin) (:minute begin)))

(defn end-time [end]
   (t/minus (t/local-time (:hour end) (:minute end)) (t/millis 1)))

(defn max-updates []
  (swap! update-count inc)
  (if (>= @update-count MAX_UPDATES)
    (do
      (reset! update-count 0)
      true)
    false))

(defn history-threshold? [coll]
  (and
    (>= (count coll) HISTORY_LEN)
    (apply = coll)))

(defn update-history [latest history]
  (swap! history #(take HISTORY_LEN (conj % latest))))

(defn punch-if-in-dest [current-location dest-location]
  (let [distance (.distanceTo current-location dest-location)]
    (log/d (str "distance: " distance))
    (if (< distance RADIUS_M)
      (update-history prefs/STATE_IN history)
      (update-history prefs/STATE_OUT history))
    (log/d "history-threshold?" @history)
    (cond (and (= (prefs/pref-get prefs/PREF_STATE) prefs/STATE_OUT)
               (< distance RADIUS_M))
          (do
            (if (history-threshold? @history)
              (if (db/punch-in-gps (l/local-now))
                (do
                  (on-ui (toast "GPS punch in" :short))
                  (prefs/update-state)))))
          (and (= (prefs/pref-get prefs/PREF_STATE) prefs/STATE_IN)
               (> distance (+ RADIUS_M THRESHOLD_M)))
          (do
            (if (history-threshold? @history)
              (if (db/punch-out-gps (l/local-now))
                (do
                  (on-ui (toast "GPS punch out" :short))
                  (prefs/update-state)))))
          :else
          (log/w (str "no GPS punch, state: " (prefs/pref-get prefs/PREF_STATE) ", distance: " distance))
          )))

(defn my-on-location-fn [^android.location.Location current-location]
  (let [latitude (.getLatitude ^android.location.Location current-location)
        longitude (.getLongitude ^android.location.Location current-location)
        dest-location (android.location.Location. "pipo")]
    (log/d "on-location thread id " (Thread/currentThread))
    (.setLatitude dest-location (prefs/pref-get prefs/PREF_DEST_LAT))
    (.setLongitude dest-location (prefs/pref-get prefs/PREF_DEST_LONG))
    (punch-if-in-dest current-location dest-location)
    (if (max-updates)
      (location/stop-location-updates))))

(defn time-to-get-location [^org.joda.time.DateTime date-time]
  (let [now (utils/get-local-time date-time)]
    (cond (or (pred/saturday? date-time) (pred/sunday? date-time))
          false
          (some #(and (t/within? (begin-time (:begin %)) (end-time (:end %)) now)
                      ((-> % :begin (get-freq-func (prefs/pref-get prefs/PREF_STATE))) now))
                (get-intervals intervals))
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
  [^org.pipo.service this key value]
  (swap! (.state this) into {key value}))

(defn- getfield
  [^org.pipo.service this key]
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
        ^org.pipo.servicehandler service-handler (getfield this :service-handler)]
    (prefs/pref-set prefs/PREF_STATE_SERVICE prefs/SERVICE_RUNNING)
    (log/i (str "Service id: " start-id " started"))

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
