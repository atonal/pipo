(ns org.pipo.day
    (:require [neko.activity :refer [defactivity set-content-view!]]
              [neko.ui.adapters :refer [cursor-adapter update-cursor]]
              [neko.debug :refer [*a]]
              [neko.find-view :refer [find-view]]
              [neko.ui :refer [config]]
              [neko.threading :refer [on-ui]]
              [neko.notify :refer [toast]]
              [clj-time.coerce :as c]
              [org.pipo.database :as db]
              [org.pipo.utils :as utils]
              )
    (:import [android.widget AbsListView]
             ))

(def ^:const EXTRA_DATE "org.pipo.EXTRA_DATE")

(defn update-punch-list [ctx]
  (let [^android.widget.ListView lv (find-view ctx ::punch-list)]
    ;; update-cursor called with 1 argument because cursor-adapter was
    ;; initialized with a cursor-fn instead of cursor
    (update-cursor (.getAdapter lv))))

(defn update-hours-list [ctx]
  (let [^android.widget.ListView lv (find-view ctx ::hours-list)]
    ;; update-cursor called with 1 argument because cursor-adapter was
    ;; initialized with a cursor-fn instead of cursor
    (update-cursor (.getAdapter lv))))

(defn update-cursors [ctx]
  (update-punch-list ctx)
  (update-hours-list ctx))

(defn get-punch-cursor [date]
  (db/get-punches-cursor-by-date date))

(defn get-hours-cursor [date]
  (db/get-hours-cursor-by-date date))

(defn make-punch-adapter [ctx date]
  (cursor-adapter
    ctx
    (fn [] [:linear-layout {:id-holder true}
            [:text-view {:id ::caption-tv}]])
    (fn [view _ data]
      (let [tv (find-view view ::caption-tv)]
        (config tv :text (str data))))
    (fn [] (get-punch-cursor date)) ;; cursor-fn
    ))

(defn make-hours-adapter [ctx date]
  (cursor-adapter
    ctx
    (fn [] [:linear-layout {:id-holder true}
            [:text-view {:id ::caption-tv}]])
    (fn [view _ data]
      (let [tv (find-view view ::caption-tv)]
        (config tv :text (str data))))
    (fn [] (get-hours-cursor date))))

(defn main-layout [ctx date]
  [:linear-layout {:orientation :vertical
                   :layout-width :match-parent
                   :layout-height :match-parent}
   [:text-view {:text (str "Work hours on " (utils/date-to-str-date date))}]
   [:list-view {:id ::punch-list
                :adapter (make-punch-adapter ctx date)
                :transcript-mode AbsListView/TRANSCRIPT_MODE_ALWAYS_SCROLL
                :layout-height [0 :dp]
                :layout-weight 1}]
   [:list-view {:id ::hours-list
                :adapter (make-hours-adapter ctx date)
                :transcript-mode AbsListView/TRANSCRIPT_MODE_ALWAYS_SCROLL
                :layout-height [0 :dp]
                :layout-weight 1}]
   ])


(defactivity org.pipo.DayActivity
  :key :day
  (onCreate
    [this bundle]
    (let [intent (.getIntent this)
          date (c/from-long (.getLongExtra intent EXTRA_DATE 0))]
      (.superOnCreate this bundle)
      (on-ui
        (set-content-view!
          this
          (main-layout this date)))
      ; (create-watchers this)
      ; (update-state this)
      ; (update-cursors this)
      )
    )
  )
