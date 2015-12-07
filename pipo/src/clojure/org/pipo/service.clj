(ns org.pipo.service
  (:require
    [neko.threading :refer [on-ui]]
    [neko.resource :as res]
    [neko.notify :refer [toast notification fire cancel]])
  (:gen-class
    :prefix service-
    :extends android.app.Service
    :state state
    :init init
    ))

(res/import-all)

(defn service-init []
  [[] (atom {:data "Hello"})])

(defn create-notification []
  (let [mynotification (notification {:icon R$drawable/ic_launcher
                                      :ticker-text "Activate location updates"
                                      :content-title "TourTracker"
                                      :content-text "Location updates are being sent"
                                      :action [:activity "org.tourtracker.MAIN"]})]
    ; Set the notification persistent
    (set! (. mynotification flags) android.app.Notification/FLAG_ONGOING_EVENT)
    (fire :notification-key mynotification)))

(defn service-onCreate [this]
  (create-notification)
  (on-ui (toast "Service created" :short))
  )

(defn service-onStartCommand [this a e w]
  (on-ui (toast "Service started" :short))
  1 ; START_STICKY
  )

(defn service-onDestroy [this]
  (cancel :notification-key)
  (on-ui (toast "Service destroyed" :short))
  )
