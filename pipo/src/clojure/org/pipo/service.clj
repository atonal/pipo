(ns org.pipo.service
  (:require
    [neko.threading :refer [on-ui]]
    [neko.resource :as res]
    [neko.notify :refer [toast notification fire cancel]]
    [org.pipo.log :as log]
    [org.pipo.prefs :as prefs]
    [org.pipo.broadcastreceiver :as tick]
    [org.pipo.location :as location])
  (:import [android.app Service Notification]
           android.preference.PreferenceManager)
  (:gen-class
    :prefix "-"
    :extends android.app.Service
    :state state
    :init init
    ))

(res/import-all)

(defn tick-func []
  (on-ui (toast (str "Time changed! (from Service)") :short)))

(defn -init []
  [[] (atom {:data "state-data"})])

(defn create-notification []
  (let [mynotification
        ^Notification (notification {:icon R$drawable/ic_launcher
                                     :ticker-text "Activate location updates"
                                     :content-title "PiPo"
                                     :content-text "Location updates are being sent"
                                     :action [:activity "org.pipo.MAIN"]})]
    ; Set the notification persistent
    (set! (. mynotification flags) android.app.Notification/FLAG_ONGOING_EVENT)
    (fire :notification-key mynotification)))

(defn -onCreate [this]
  (create-notification)
  (on-ui (toast "Service created" :short))
  )

(defn -onStartCommand [^org.pipo.service this intent flags start-id]
  (let [state (.state this)]
    (prefs/pref-set-service this prefs/PREF_STATE_SERVICE prefs/SERVICE_RUNNING)
    (on-ui (toast "Service started" :short))
    (location/start-location-updates this)
    (tick/register-receiver this tick-func)
    Service/START_STICKY
    ))

(defn -onDestroy [this]
  (cancel :notification-key)
  (prefs/pref-set-service this prefs/PREF_STATE_SERVICE prefs/SERVICE_STOPPED)
  (on-ui (toast "Service destroyed" :short))
  (location/stop-location-updates)
  )
