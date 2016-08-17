(ns org.pipo.broadcastreceiver
  (:require
    [neko.threading :refer [on-ui]]
    [neko.notify :refer [toast ]]
    [org.pipo.log :as log])
  (:gen-class
    :prefix "tick-"
    :extends android.content.BroadcastReceiver
    :state state
    :init "init"
    :constructors {[clojure.lang.IFn] []}
    ))

(defn register-receiver [^android.content.Context ctx func handler]
  (let [intent-filter (android.content.IntentFilter.)
        receiver (org.pipo.broadcastreceiver. func)]
    (.addAction intent-filter android.content.Intent/ACTION_TIME_TICK)
    (.registerReceiver ctx receiver intent-filter nil handler)
    receiver))

(defn unregister-receiver [^android.content.Context ctx receiver]
  (.unregisterReceiver ctx receiver))

(defn- setfield
  [^org.pipo.broadcastreceiver this key value]
  (swap! (.state this) into {key value}))

(defn- getfield
  [^org.pipo.broadcastreceiver this key]
  (@(.state this) key))

(defn tick-init [func]
  [[] (atom {:func-to-run func})])

(defn tick-onReceive [this ctx intent]
  (let [func (getfield this :func-to-run)]
    (if-not (nil? func)
      (func)
      (on-ui (toast "func was nil" :short)))))
