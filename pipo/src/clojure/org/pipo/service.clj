(ns org.pipo.service
  (:require
    [neko.threading :refer [on-ui]]
    [neko.resource :as res]
    [neko.notify :refer [toast notification fire cancel]])
  (:import [android.app Service])
  (:gen-class
    :prefix "-"
    :extends android.app.Service
    :state state
    :init init
    ))

(res/import-all)

(defn -init []
  [[] (atom {:data "state-data"})])

(defn create-notification []
  (let [mynotification (notification {:icon R$drawable/ic_launcher
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

(defn -onStartCommand [this a e w]
  (on-ui (toast "Service started" :short))
  Service/START_STICKY
  )

(defn -onDestroy [this]
  (cancel :notification-key)
  (on-ui (toast "Service destroyed" :short))
  )
