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

(defn register-receiver [ctx func]
  (let [intent-filter (android.content.IntentFilter.)
        receiver (org.pipo.broadcastreceiver. func)]
    (-> intent-filter (.addAction android.content.Intent/ACTION_TIME_TICK))
    (-> ctx (.registerReceiver receiver intent-filter))
    receiver))

(defn unregister-receiver [ctx receiver]
  (-> ctx (.unregisterReceiver receiver)))

(defn- setfield
  [this key value]
  (swap! (.state this) into {key value}))

(defn- getfield
  [this key]
  (@(.state this) key))

(defn tick-init [func]
  [[] (atom {:func-to-run func})])

(defn tick-onReceive [this ctx intent]
  (let [func (getfield this :func-to-run)]
    (if (not (nil? func))
      (func)
      (on-ui (toast "func was nil" :short)))))
