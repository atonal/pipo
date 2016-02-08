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

;; TODO: is this class needed?
(defn handler-handleMessage [this msg]
  (log/d "handle message thread id " (Thread/currentThread))
  (log/d (str "handler started with " (.-arg1 msg)))
  )
