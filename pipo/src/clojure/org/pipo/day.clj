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
            [org.pipo.log :as log]
            )
  (:import [android.widget AbsListView]
           [android.view Gravity]
           android.graphics.Color
           ))

(def ^:const EXTRA_DATE "org.pipo.EXTRA_DATE")

(def cursors (atom {:punch nil :work nil}))

(defn get-punch-cursor [date]
  (db/get-punches-by-date-cursor date))

(defn get-work-cursor [date]
  (db/get-work-by-date-cursor date))

(defn close-cursor [cursor-kw]
  (let [cursor (cursor-kw @cursors)]
    (if (not (nil? cursor))
      (do
        (log/d (str "closing " cursor-kw " cursor"))
        (.close cursor)
        (swap! cursors assoc cursor-kw nil)))))

(defn update-punch-list [ctx date]
  (let [^android.widget.ListView lv (find-view ctx ::punch-list)
        new-cursor (get-punch-cursor date)]
    (update-cursor (.getAdapter lv) new-cursor)
    (close-cursor :punch)
    (swap! cursors assoc :punch new-cursor)
    ))

; (defn update-work-list [ctx]
;   (let [^android.widget.ListView lv (find-view ctx ::work-list)]
;     (update-cursor (.getAdapter lv) new-cursor)))

; (defn update-cursors [ctx]
;   (log/d "update cursors")
;   (update-punch-list ctx)
;   (update-work-list ctx))

(defn toggle-validity-and-update [ctx id date]
  (db/punch-toggle-validity id)
  ;; update cursor
  (update-punch-list ctx date)
  )

(defn make-punch-adapter [ctx date cursor]
  (cursor-adapter
    ctx
    (fn [] [:linear-layout {:id-holder true
                            :orientation :horizontal
                            :layout-width :fill
                            :layout-height [50 :dp]
                            }
            [:linear-layout {:id ::punch-view
                             :orientation :horizontal
                             :layout-width :fill
                             :layout-height :fill
                             }
             [:text-view {:id ::id-tv
                          :layout-width [0 :dp]
                          :layout-height :fill
                          :layout-weight 1
                          :gravity :center_vertical}]
             [:text-view {:id ::type-tv
                          :layout-width [0 :dp]
                          :layout-height :fill
                          :layout-weight 1
                          :gravity :center_vertical}]
             [:text-view {:id ::method-tv
                          :layout-width [0 :dp]
                          :layout-height :fill
                          :layout-weight 2
                          :gravity :center_vertical}]
             [:text-view {:id ::validity-tv
                          :layout-width [0 :dp]
                          :layout-height :fill
                          :layout-weight 2
                          :gravity :center_vertical}]
             [:text-view {:id ::time-tv
                          :layout-width [0 :dp]
                          :layout-height :fill
                          :layout-weight 6
                          :gravity (bit-or Gravity/RIGHT Gravity/CENTER_VERTICAL)}]
             ]
            ]
      )
    (fn [view _ data]
      (let [punch-view (find-view view ::punch-view)
            id-tv (find-view view ::id-tv)
            type-tv (find-view view ::type-tv)
            method-tv (find-view view ::method-tv)
            validity-tv (find-view view ::validity-tv)
            time-tv (find-view view ::time-tv)]
        (config punch-view :on-click (fn [_] (toggle-validity-and-update ctx (db/get-id data) date)))
        (config id-tv :text (str "id:" (db/get-id data)))
        (config type-tv :text (str (db/get-type data)))
        (config method-tv :text (str (db/get-punch-method data)))
        (config validity-tv :text (str (db/get-validity data)))
        (config time-tv :text (str (utils/date-to-str-full
                                     (utils/to-local-time-zone
                                       (c/from-long
                                         (db/get-time data))))))
        ))
    cursor
    ))

(defn make-work-adapter [ctx date cursor]
  (cursor-adapter
    ctx
    (fn [] [:linear-layout {:id-holder true
                            :orientation :horizontal
                            :layout-width :fill
                            :layout-height [50 :dp]}
            [:text-view {:id ::id-tv
                         :layout-width [0 :dp]
                         :layout-height :fill
                         :layout-weight 2
                         :gravity :center_vertical}]
            [:text-view {:id ::validity-tv
                         :layout-width [0 :dp]
                         :layout-height :fill
                         :layout-weight 2
                         :gravity :center_vertical}]
            [:text-view {:id ::start-tv
                         :layout-width [0 :dp]
                         :layout-height :fill
                         :layout-weight 2
                         :gravity :center_vertical}]
            [:text-view {:id ::stop-tv
                         :layout-width [0 :dp]
                         :layout-height :fill
                         :layout-weight 2
                         :gravity :center_vertical}]
            [:text-view {:id ::lunch-tv
                         :layout-width [0 :dp]
                         :layout-height :fill
                         :layout-weight 3
                         :gravity :center_vertical}]
            [:text-view {:id ::date-tv
                         :layout-width [0 :dp]
                         :layout-height :fill
                         :layout-weight 3
                         :gravity (bit-or Gravity/RIGHT Gravity/CENTER_VERTICAL)}]
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
    cursor))

(defn main-layout [ctx date cursors]
  (let [punch-cursor (:punch cursors)
        work-cursor (:work cursors)]
    [:linear-layout {:orientation :vertical
                     :layout-width :match-parent
                     :layout-height :match-parent
                     :padding-left [10 :px]
                     :padding-right [10 :px]}
     [:text-view {:text (str "Work hours on " (utils/date-to-str-date date))
                  }]
     [:linear-layout {:orientation :vertical
                      :layout-width :fill
                      :layout-height [0 :dp]
                      :layout-weight 1
                      :padding-bottom [4 :px]}
      [:list-view {:id ::punch-list
                   :adapter (make-punch-adapter ctx date punch-cursor)
                   :transcript-mode AbsListView/TRANSCRIPT_MODE_ALWAYS_SCROLL
                   :layout-width :fill
                   :layout-height :match-parent
                   }]]
     [:linear-layout {:orientation :vertical
                      :layout-width :fill
                      :layout-height [2 :dp]
                      :background-color Color/GRAY}]
     [:linear-layout {:orientation :vertical
                      :layout-width :fill
                      :layout-height [0 :dp]
                      :layout-weight 1
                      :padding-top [4 :px]
                      :padding-bottom [10 :px]}
      [:list-view {:id ::work-list
                   :adapter (make-work-adapter ctx date work-cursor)
                   :transcript-mode AbsListView/TRANSCRIPT_MODE_ALWAYS_SCROLL
                   :layout-width :fill
                   :layout-height :match-parent }]]
     ]))


(defactivity org.pipo.DayActivity
  :key :day
  (onCreate
    [this bundle]
    (let [intent (.getIntent this)
          date (utils/to-local-time-zone (c/from-long (.getLongExtra intent EXTRA_DATE 0)))]
      (.superOnCreate this bundle)
      (swap! cursors assoc :punch (get-punch-cursor date))
      (swap! cursors assoc :work (get-work-cursor date))
      (on-ui
        (set-content-view!
          this
          (main-layout this date @cursors)))
      ; (update-cursors this)
      )
    (log/d "day thread id " (Thread/currentThread))
    )
  (onStart
    [this]
    (.superOnStart this)
    )
  (onResume
    [this]
    (.superOnResume this)
    )
  (onPause
    [this]
    (.superOnPause this)
    )
  (onStop
    [this]
    (.superOnStop this)
    )
  (onDestroy
    [this]
    (.superOnDestroy this)
    (close-cursor :punch)
    (close-cursor :work)
    )
  )
