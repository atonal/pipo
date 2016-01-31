(ns org.pipo.service
  (:require
    [neko.threading :refer [on-ui]]
    [neko.debug :refer [*a]]
    [neko.resource :as res]
    [neko.notify :refer [toast notification fire cancel]]
    [org.pipo.log :as log]
    [org.pipo.prefs :as prefs]
    [org.pipo.broadcastreceiver :as tick]
    [org.pipo.servicehandler :as handler]
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

(def tick-receiver (atom nil))

(res/import-all)

(defn tick-func []
  (on-ui (toast (str "Time changed! (from Service)") :short)))

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
    (set! (.-arg1 msg) start-id)
    (.sendMessage service-handler msg)
    (reset! tick-receiver (tick/register-receiver this tick-func))
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
