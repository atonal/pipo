(ns org.pipo.main
    (:require [neko.activity :refer [defactivity set-content-view!]]
              [neko.debug :refer [*a]]
              [neko.threading :refer [on-ui]]
              [neko.find-view :refer [find-view]]
              [neko.ui :refer [config]]
              [neko.ui.adapters :refer [cursor-adapter update-cursor]]
              [clj-time.local :as l]
              [org.pipo.database :as db])
    (:import [android.widget AbsListView]))

(def ^:const TEXT_PUNCH_IN "punch in")
(def ^:const TEXT_PUNCH_OUT "punch out")
(def ^:const TEXT_REFRESH "refresh")
(def ^:const TEXT_WIPE "wipe")

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

(defn update-punch-list [ctx]
  (let [^android.widget.ListView lv (find-view ctx ::punch-list)]
    ;; update-cursor called with 1 argument because cursor-adapter was
    ;; initialized with a cursor-fn instead of cursor
    (update-cursor (.getAdapter lv))))

(defn punch-in [ctx]
  (db/punch-in (l/local-now))
  (update-punch-list ctx))

(defn punch-out [ctx]
  (db/punch-out (l/local-now))
  (update-punch-list ctx))

(defn wipe-db [ctx]
  (db/wipe)
  (update-punch-list ctx))

(defn main-layout [ctx]
  [:linear-layout {:orientation :vertical
                   :layout-width :match-parent
                   :layout-height :match-parent}
   [:text-view {:text "PiPo!"}]
   [:list-view {:id ::punch-list
                :adapter (make-punch-adapter ctx)
                :transcript-mode AbsListView/TRANSCRIPT_MODE_ALWAYS_SCROLL
                :layout-height [0 :dp]
                :layout-weight 1}]
   [:linear-layout {:orientation :horizontal
                    :layout-width :match-parent
                    :layout-height :wrap}
    [:button {:id ::punch-in-bt
              :layout-width :wrap
              :layout-height :wrap
              :text TEXT_PUNCH_IN
              :on-click (fn [_] (punch-in ctx))}]
    [:button {:id ::punch-out-bt
              :layout-width :wrap
              :layout-height :wrap
              :text TEXT_PUNCH_OUT
              :on-click (fn [_] (punch-out ctx))}]
    [:button {:id ::refresh-bt
              :layout-width :wrap
              :layout-height :wrap
              :text TEXT_REFRESH
              :on-click (fn [_] (update-punch-list ctx))}]
    [:button {:id ::wipe-bt
              :layout-width :wrap
              :layout-height :wrap
              :text TEXT_WIPE
              :on-click (fn [_] (wipe-db ctx))}]
    ]
   ])

(defactivity org.pipo.MyActivity
  :key :main
  (onCreate [this bundle]
            (.superOnCreate this bundle)
            (on-ui
              (set-content-view!
                this
                (main-layout this)))
            (update-punch-list this)))
