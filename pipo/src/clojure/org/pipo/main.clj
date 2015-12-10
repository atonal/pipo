(ns org.pipo.main
    (:require [neko.activity :refer [defactivity set-content-view!]]
              [neko.debug :refer [*a]]
              [neko.threading :refer [on-ui]]
              [neko.find-view :refer [find-view]]
              [neko.ui :refer [config]]
              [neko.ui.adapters :refer [cursor-adapter update-cursor]]
              [org.pipo.database :as db]))

(defn get-my-cursor []
  (db/get-punches "time >= 555"))

(defn make-punch-adapter [ctx]
  (cursor-adapter
    ctx
    (fn [] [:linear-layout {:id-holder true}
            [:text-view {:id ::caption-tv}]])
    (fn [view _ data]
      (let [tv (find-view view ::caption-tv)]
        (config tv :text (str data))))
    (fn [] (get-my-cursor)) ;; cursor-fn
    ))



(defn main-layout [ctx]
  [:linear-layout {:orientation :vertical
                   :layout-width :match-parent
                   :layout-height :match-parent}
   [:text-view {:text "PiPo!"}]
   [:list-view {:id ::punch-list
                :adapter (make-punch-adapter ctx)
                :layout-height [0 :dp]
                :layout-weight 1}]])

(defactivity org.pipo.MyActivity
  :key :main

  (onCreate [this bundle]
    (.superOnCreate this bundle)
    (on-ui
      (set-content-view!
        this
        (main-layout this)))
    (let [^android.widget.ListView lv (find-view this ::punch-list)]
      (update-cursor (.getAdapter lv))) ;; called with 1 argument because cursor-adapter was initialized with a cursor-fn instead of cursor
    ))
