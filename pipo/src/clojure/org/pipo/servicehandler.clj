(ns org.pipo.servicehandler
  (:require
    [neko.threading :refer [on-ui]]
    [neko.notify :refer [toast]]
    [org.pipo.location :as location]
    [org.pipo.log :as log]
    )
  (:import [android.os Message])
(:gen-class
  :prefix "handler-"
  :extends android.os.Handler
  :state state
  :init init))

(defn handler-init [looper]
  [[looper] (atom {:data "state-data"})])

(defn handler-handleMessage [this msg]
  (log/d "handle message thread id " (Thread/currentThread))
  (on-ui (toast (str "handler started with " (.-arg1 msg))))
  (log/d "sleep 10")
  (Thread/sleep 10000)
  (location/start-location-updates)
  (log/d "location updates started")
  )
