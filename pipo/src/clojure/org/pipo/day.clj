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

(defn update-work-list [ctx]
  (let [^android.widget.ListView lv (find-view ctx ::work-list)]
    ;; update-cursor called with 1 argument because cursor-adapter was
    ;; initialized with a cursor-fn instead of cursor
    (update-cursor (.getAdapter lv))))

(defn update-cursors [ctx]
  (update-punch-list ctx)
  (update-work-list ctx))

(defn get-punch-cursor [date]
  (db/get-punches-by-date-cursor date))

(defn get-work-cursor [date]
  (db/get-work-by-date-cursor date))

(defn make-punch-adapter [ctx date]
  (cursor-adapter
    ctx
    (fn [] [:linear-layout {:id-holder true
                            :orientation :horizontal
                            :layout-width :fill
                            :layout-height :wrap}
             [:text-view {:id ::id-tv
                          :layout-width [0 :dp]
                          :layout-weight 1}]
             [:text-view {:id ::type-tv
                          :layout-width [0 :dp]
                          :layout-weight 1}]
             [:text-view {:id ::method-tv
                          :layout-width [0 :dp]
                          :layout-weight 1}]
             [:text-view {:id ::time-tv
                          :layout-width [0 :dp]
                          :layout-weight 4
                          :gravity :right}]
             ]
    )
    (fn [view _ data]
      (let [id-tv (find-view view ::id-tv)
            type-tv (find-view view ::type-tv)
            method-tv (find-view view ::method-tv)
            time-tv (find-view view ::time-tv)]
        (config id-tv :text (str "id:" (db/get-id data)))
        (config type-tv :text (str (db/get-type data)))
        (config method-tv :text (str (db/get-punch-method data)))
        (config time-tv :text (str (utils/date-to-str-full
                                     (c/from-long
                                       (db/get-time data)))))
        ))
    (fn [] (get-punch-cursor date)) ;; cursor-fn
    ))

(defn make-work-adapter [ctx date]
  (cursor-adapter
    ctx
    (fn [] [:linear-layout {:id-holder true
                            :orientation :horizontal
                            :layout-width :fill
                            :layout-height :wrap}
             [:text-view {:id ::id-tv
                          :layout-width [0 :dp]
                          :layout-weight 2}]
             [:text-view {:id ::validity-tv
                          :layout-width [0 :dp]
                          :layout-weight 2}]
             [:text-view {:id ::start-tv
                          :layout-width [0 :dp]
                          :layout-weight 2}]
             [:text-view {:id ::stop-tv
                          :layout-width [0 :dp]
                          :layout-weight 2}]
             [:text-view {:id ::lunch-tv
                          :layout-width [0 :dp]
                          :layout-weight 3}]
             [:text-view {:id ::date-tv
                          :layout-width [0 :dp]
                          :layout-weight 3
                          :gravity :right}]
             ])
    (fn [view _ data]
      (let [id-tv (find-view view ::id-tv)
            validity-tv (find-view view ::validity-tv)
            start-tv (find-view view ::start-tv)
            stop-tv (find-view view ::stop-tv)
            lunch-tv (find-view view ::lunch-tv)
            date-tv (find-view view ::date-tv)]
        (config id-tv :text (str "id:" (db/get-id data)))
        (config validity-tv :text (str (db/get-validity data)))
        (config start-tv :text (str "start:" (db/get-start-id data)))
        (config stop-tv :text (str "stop:" (db/get-stop-id data)))
        (config lunch-tv :text (str (db/get-lunch data)))
        (config date-tv :text (str (db/get-date data)))
        ))
    (fn [] (get-work-cursor date))))

(defn main-layout [ctx date]
  [:linear-layout {:orientation :vertical
                   :layout-width :match-parent
                   :layout-height :match-parent
                   :padding-left [10 :px]
                   :padding-right [10 :px]}
   [:text-view {:text (str "Work hours on " (utils/date-to-str-date date))
                }]
   [:list-view {:id ::punch-list
                :adapter (make-punch-adapter ctx date)
                :transcript-mode AbsListView/TRANSCRIPT_MODE_ALWAYS_SCROLL
                :layout-width :fill
                :layout-height [0 :dp]
                :layout-weight 1}]
   [:list-view {:id ::work-list
                :adapter (make-work-adapter ctx date)
                :transcript-mode AbsListView/TRANSCRIPT_MODE_ALWAYS_SCROLL
                :layout-width :fill
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
