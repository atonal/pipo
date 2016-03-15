(ns org.pipo.day
  (:require [neko.activity :refer [defactivity set-content-view!]]
            [neko.ui.adapters :refer [cursor-adapter update-cursor]]
            [neko.debug :refer [*a]]
            [neko.find-view :refer [find-view]]
            [neko.ui :refer [config make-ui]]
            [neko.threading :refer [on-ui]]
            [neko.notify :refer [toast]]
            [neko.dialog.alert :refer [alert-dialog-builder]]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [org.pipo.database :as db]
            [org.pipo.utils :as utils]
            [org.pipo.log :as log]
            [org.pipo.ui-utils :as ui-utils]
            [org.pipo.prefs :as prefs]
            )
  (:import [android.widget AbsListView]
           [android.view Gravity]
           neko.data.sqlite.TaggedCursor
           android.graphics.Color
           android.os.Bundle
           ))

(def ^:const EXTRA_DATE "org.pipo.EXTRA_DATE")
(def ^:const PUNCH_IN_DIALOG_ID 0)
(def ^:const PUNCH_OUT_DIALOG_ID 1)
(def ^:const DATE_TAG "date")

(def cursors (atom {:punch nil :work nil}))

(defn get-punch-cursor [date]
  (db/get-punches-by-date-cursor date))

(defn get-work-cursor [date]
  (db/get-work-by-date-cursor date))

(defn close-cursor [cursor-kw]
  (let [^TaggedCursor cursor (cursor-kw @cursors)]
    (if (not (nil? cursor))
      (do
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

(defn update-work [ctx date]
  (db/update-days-work date)
  (update-punch-list ctx date)
  (prefs/update-state))

(defn toggle-validity-and-update [ctx id date]
  (db/punch-toggle-validity id)
  (update-work ctx date))

(defn work-id-that-starts-at [date id]
  (first (filter #(= id (:start_id %)) (db/get-work-by-date date))))

(defn work-id-that-stops-at [date id]
  (first (filter #(= id (:stop_id %)) (db/get-work-by-date date))))

(defn get-punch-color [pred]
  (if pred
    Color/GRAY
    Color/DKGRAY))

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
             [:text-view {:id ::work-tv
                          :layout-width [0 :dp]
                          :layout-height :fill
                          :layout-weight 1
                          :gravity :center_vertical}]
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
            [:text-view {:id ::lunch-tv
                         :layout-width [0 :dp]
                         :layout-height :fill
                         :layout-weight 2
                         :gravity :center_vertical}]
             [:text-view {:id ::time-tv
                          :layout-width [0 :dp]
                          :layout-height :fill
                          :layout-weight 4
                          :gravity (bit-or Gravity/RIGHT Gravity/CENTER_VERTICAL)}]
             ]
            ]
      )
    (fn [view _ data]
      (let [punch-view (find-view view ::punch-view)
            work-tv (find-view view ::work-tv)
            id-tv (find-view view ::id-tv)
            type-tv (find-view view ::type-tv)
            method-tv (find-view view ::method-tv)
            validity-tv (find-view view ::validity-tv)
            lunch-tv (find-view view ::lunch-tv)
            time-tv (find-view view ::time-tv)
            work-start (work-id-that-starts-at date (db/get-id data))
            work-stop (work-id-that-stops-at date (db/get-id data))]
        (log/d (str "start-id " (db/get-id work-start) ", stop-id " (db/get-id work-stop)))
        (config punch-view
                :on-click (fn [_] (toggle-validity-and-update ctx (db/get-id data) date))
                :background-color (get-punch-color (or (not (nil? work-stop))
                                                       (not (nil? work-start)))))
        (config work-tv :text (cond (not (nil? work-start)) "START"
                                    (not (nil? work-stop)) "END"
                                    :else ""))
        (config id-tv :text (str "id:" (db/get-id data)))
        (config type-tv :text (str (db/get-type data)))
        (config method-tv :text (str (db/get-punch-method data)))
        (config validity-tv :text (str (db/get-validity data)))
        (config lunch-tv :text (if (not (nil? work-start)) (str (db/get-lunch work-start)) ""))
        (config time-tv :text (str (utils/date-to-str
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

(defn make-punch-dialog-layout [ctx]
  (make-ui
    ctx
    [:linear-layout {:id-holder true
                     :layout-width :fill
                     :layout-height :wrap
                     :padding-left [40 :px]
                     :padding-right [60 :px]
                     :orientation :vertical}
     [:edit-text {:id ::hour-et
                  :layout-width :fill
                  :layout-height :wrap
                  :layout-gravity :right
                  :input-type :number
                  :hint "Hour"
                  }
      ]
     [:edit-text {:id ::minute-et
                  :layout-width :fill
                  :layout-height :wrap
                  :layout-gravity :right
                  :input-type :number
                  :hint "Minute"
                  }
      ]]))

(defn create-punch-dialog [ctx date inout]
  (let [^android.view.ViewGroup dialog-layout (make-punch-dialog-layout ctx)]
    (-> ctx
        (alert-dialog-builder
          {:message (str "add punch " inout)
           :cancelable true
           :positive-text "Add"
           :positive-callback (fn [dialog res]
                                (let [hour (read-string
                                             (ui-utils/get-text
                                               dialog-layout
                                               ::hour-et))
                                      minute (read-string
                                               (ui-utils/get-text
                                                 dialog-layout
                                                 ::minute-et))
                                      punch-date-time (utils/from-local-time-zone
                                                        (t/date-time
                                                          (t/year date)
                                                          (t/month date)
                                                          (t/day date)
                                                          hour
                                                          minute))]
                                  (cond
                                    (= inout "in") (db/punch-in-manual
                                                     punch-date-time)
                                    (= inout "out") (db/punch-out-manual
                                                      punch-date-time))
                                  (update-work ctx punch-date-time)))
           :negative-text "Cancel"
           :negative-callback (fn [_ _] ())})
        (.setView dialog-layout)
        .create)))

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
     [:linear-layout {:orientation :horizontal
                      :layout-width :fill
                      :layout-height :wrap
                      }
      [:button {:id ::add-punch-in-bt
                :layout-width [0 :dp]
                :layout-weight 1
                :layout-height [50 :dp]
                :text "Add punch in"
                :on-click (fn [_] (let [date-bundle (Bundle.)]
                                    (do
                                      (.putString
                                        date-bundle
                                        DATE_TAG
                                        (utils/date-to-str-date date))
                                      (on-ui
                                        (.showDialog
                                          ctx
                                          PUNCH_IN_DIALOG_ID
                                          date-bundle))
                                      true)))
                }]
      [:button {:id ::add-punch-out-bt
                :layout-width [0 :dp]
                :layout-weight 1
                :layout-height [50 :dp]
                :text "Add punch out"
                :on-click (fn [_] (let [date-bundle (Bundle.)]
                                    (do
                                      (.putString
                                        date-bundle
                                        DATE_TAG
                                        (utils/date-to-str-date date))
                                      (on-ui
                                        (.showDialog
                                          ctx
                                          PUNCH_OUT_DIALOG_ID
                                          date-bundle))
                                      true)))
                }]
       ]
     ]))


(defactivity org.pipo.DayActivity
  :key :day
  (onCreate
    [this bundle]
    (let [intent (.getIntent this)
          date (utils/to-local-time-zone
                 (c/from-long
                   (.getLongExtra intent EXTRA_DATE 0)))]
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
  (onPrepareDialog
    [this id dialog dialog-bundle]
    (.removeDialog this id))
  (onCreateDialog
    [this id dialog-bundle]
    (cond
      (= id PUNCH_IN_DIALOG_ID)
      (create-punch-dialog
        this
        (utils/str-to-date-date
          (.getString dialog-bundle DATE_TAG))
        "in")
      (= id PUNCH_OUT_DIALOG_ID)
      (create-punch-dialog
        this
        (utils/str-to-date-date
          (.getString dialog-bundle DATE_TAG))
        "out")
      :else (toast "Invalid ID" :short)))
  )
