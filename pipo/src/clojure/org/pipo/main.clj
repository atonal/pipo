(ns org.pipo.main
  (:require [neko.activity :refer [defactivity set-content-view!]]
            [neko.debug :refer [*a]]
            [neko.threading :refer [on-ui]]
            [neko.find-view :refer [find-view]]
            [neko.ui :refer [config make-ui]]
            [org.pipo.log :as log]
            [neko.notify :refer [toast]]
            [neko.dialog.alert :refer [alert-dialog-builder]]
            [clj-time.local :as l]
            [clj-time.coerce :as c]
            [org.pipo.prefs :as prefs]
            [org.pipo.database :as db]
            [org.pipo.utils :as utils]
            [org.pipo.broadcastreceiver :as tick]
            [org.pipo.location :as location]
            [org.pipo.ui-utils :as ui-utils])
  (:import [android.view ViewGroup Gravity]
           android.graphics.Color
           android.text.InputType
           android.app.Activity
           android.content.Intent
           java.lang.Long
           org.joda.time.DateTime))

(def ^:const TEXT_PUNCH_IN "punch in")
(def ^:const TEXT_PUNCH_OUT "punch out")
(def ^:const TEXT_SERVICE_START "Start")
(def ^:const TEXT_SERVICE_STOP "Stop")
(def ^:const TEXT_WIPE "wipe")
(def ^:const TEXT_SET_GPS "Set GPS")
(def ^:const WEEK_DIALOG_ID 0)
(def ^:const GPS_DIALOG_ID 1)
(def ^:const EXTRA_DATE "org.pipo.EXTRA_DATE")
(def ^:const HOUR_FORMATTERS {:dec utils/long-to-decimal :hm utils/long-to-hm})
(def tick-receiver (atom nil))

(defn get-hour-formatter-kw []
  (keyword (prefs/pref-get prefs/PREF_HOUR_FORMATTER)))

(defn get-hour-formatter []
  ((get-hour-formatter-kw) HOUR_FORMATTERS))

(defn set-hour-formatter [fmt]
  ;; pre is-string?
  (if (not (nil? ((keyword fmt) HOUR_FORMATTERS)))
    (prefs/pref-set prefs/PREF_HOUR_FORMATTER fmt)))

; TODO: more generic toggle function, if more that two formatters
(defn toggle-hour-formatter []
  (if (= :dec (get-hour-formatter-kw))
    (set-hour-formatter "hm")
    (set-hour-formatter "dec")))

(defn get-day-color [date]
  (if (utils/date-equals? (l/local-now) date)
    Color/GRAY
    Color/DKGRAY))

(defn get-week-color [year week]
  (let [current (utils/get-current-week)]
    (if (and (= (:year current) year) (= (:week current) week))
      Color/GRAY
      Color/DKGRAY)))

(defn start-day-activity [^Activity ctx ^DateTime date]
  (let [^Intent intent (Intent. ctx org.pipo.DayActivity)]
    (.putExtra intent EXTRA_DATE ^Long (c/to-long date))
    (.startActivity ctx intent)))

(defn get-work-hours-for-date [^DateTime date]
  (reduce
    + (map db/get-work-hours
           (db/get-work-by-date date))))

(defn add-current-work [^DateTime date]
  (if (utils/date-equals? (l/local-now) date)
    (db/get-time-since-latest-punch-in (l/local-now))
    0))

(defn make-week-list [ctx]
  (concat
    [:linear-layout {:id ::inner-week
                     :orientation :vertical
                     :layout-width :match-parent
                     :layout-height :match-parent
                     }
     ]
    (map (fn [^DateTime date]
           (let [local-date (utils/to-local-time-zone date)]
             [:linear-layout {:orientation :horizontal
                              :layout-width :fill
                              :layout-height [0 :dp]
                              :layout-weight 1
                              :padding [4 :px]
                              :on-click (fn [_] (start-day-activity ctx local-date))
                              }
              [:text-view {:text (utils/date-to-str-day local-date)
                           :gravity :center_vertical
                           :layout-width [0 :dp]
                           :layout-height :fill
                           :layout-weight 1
                           :padding-left [20 :px]
                           :background-color (get-day-color local-date)
                           }]
              [:text-view {:text ((get-hour-formatter)
                                  (+ (get-work-hours-for-date local-date)
                                     (add-current-work local-date)))
                           :padding-right [20 :px]
                           :padding-left [20 :px]
                           :gravity (bit-or Gravity/RIGHT Gravity/CENTER_VERTICAL)
                           :layout-width :wrap
                           :layout-height :fill
                           :background-color (get-day-color local-date)
                           }]
              ]))
         (let [year (prefs/pref-get prefs/PREF_YEAR)
               week (prefs/pref-get prefs/PREF_WEEK)]
           (utils/week-from-week-number week year)))))

(defn update-week-list [ctx]
  (let [week-list-view ^ViewGroup (find-view ctx ::inner-week)]
    (on-ui (.removeAllViews week-list-view))
    (on-ui (.addView week-list-view (make-ui ctx (make-week-list ctx))))))

(defn update-week-nr-view [ctx new-state]
  (let [new-year (prefs/pref-get prefs/PREF_YEAR new-state)
        new-week (prefs/pref-get prefs/PREF_WEEK new-state)]
    (ui-utils/set-text ctx ::year-tv (str new-year " / " new-week))
    (on-ui
      (config (find-view ctx ::year-tv)
              :background-color
              (get-week-color new-year new-week)))))

(defn punch-in []
  (if (db/punch-in-manual (l/local-now))
    (prefs/update-state)))

(defn punch-out []
  (if (db/punch-out-manual (l/local-now))
    (prefs/update-state)))

(defn update-state-ui [ctx new-state]
  (let [state (prefs/pref-get prefs/PREF_STATE new-state)]
    (if (= state prefs/STATE_IN)
      (do
        (on-ui (config (find-view ctx ::punch-out-bt) :text-color Color/WHITE))
        (on-ui (config (find-view ctx ::punch-out-bt) :on-click (fn [_] (punch-out))))
        (on-ui (config (find-view ctx ::punch-out-bt) :on-long-click (fn [_] ())))

        (on-ui (config (find-view ctx ::punch-in-bt) :text-color Color/GRAY))
        (on-ui
          (config
            (find-view ctx ::punch-in-bt)
            :on-click
            (fn [_]
              (do
                (on-ui (toast (str "Long click to override!") :short))
                true))))
        (on-ui
          (config
            (find-view ctx ::punch-in-bt)
            :on-long-click
            (fn [_]
              (do
                (punch-in)  ;; TODO: invalidate previous punch in
                (on-ui
                  (toast (str "Previous punch in overridden!") :short))
                true)))))
      (do
        (on-ui (config (find-view ctx ::punch-in-bt) :text-color Color/WHITE))
        (on-ui (config (find-view ctx ::punch-in-bt) :on-click (fn [_] (punch-in))))
        (on-ui (config (find-view ctx ::punch-in-bt) :on-long-click (fn [_] ())))

        (on-ui (config (find-view ctx ::punch-out-bt) :text-color Color/GRAY))
        (on-ui
          (config
            (find-view ctx ::punch-out-bt)
            :on-click
            (fn [_]
              (do
                (on-ui (toast (str "Long click to override!") :short))
                true))))
        (on-ui
          (config
            (find-view ctx ::punch-out-bt)
            :on-long-click
            (fn [_]
              (do
                (punch-out)  ;; TODO: invalidate previous punch out
                (on-ui
                  (toast (str "Previous punch out overridden!") :short))
                true))))))))

(declare service-start)
(defn service-stop [^Activity ctx service]
  (.stopService ctx service)
  (prefs/pref-set prefs/PREF_STATE_SERVICE prefs/SERVICE_STOPPED)
  )

(defn service-start [^Activity ctx service]
  (.startService ctx service))

(defn update-service-ui [ctx new-state service]
  (let [state (prefs/pref-get prefs/PREF_STATE_SERVICE new-state)]
    (if (= state prefs/SERVICE_RUNNING)
      (do
        (ui-utils/set-text ctx ::service-bt TEXT_SERVICE_STOP)
        (on-ui
          (config
            (find-view ctx ::service-bt)
            :on-click
            (fn [_] (service-stop ctx service)))))
      (do
        (ui-utils/set-text ctx ::service-bt TEXT_SERVICE_START)
        (on-ui
          (config
            (find-view ctx ::service-bt)
            :on-click
            (fn [_] (service-start ctx service))))))))

(defn update-uis [ctx service & pref-state]
  (let [state (or (first pref-state) @(prefs/get-prefs))]
    (update-week-nr-view ctx state)
    (update-state-ui ctx state)
    (update-service-ui ctx state service)
    (update-week-list ctx)))

(defn create-watchers [ctx service]
  (add-watch (prefs/get-prefs) :year-week-watcher
             (fn [key atom old-state new-state]
               (log/d "pref updated:" new-state)
               (update-uis ctx service new-state)))
  (add-watch (location/get-location-data) :location-watcher
             (fn [key atom old-state new-state]
               (ui-utils/set-text ctx ::location-lat-tv (str "lat: " (:lat new-state)))
               (ui-utils/set-text ctx ::location-long-tv (str "long: " (:long new-state)))))
  )

(defn wipe-db []
  (db/wipe)
  (prefs/update-state))


(defn change-to-current-week []
  (let [current (utils/get-current-week)]
    (prefs/pref-set prefs/PREF_YEAR (:year current))
    (prefs/pref-set prefs/PREF_WEEK (:week current))))

(defn week-layout [^Activity ctx service]
  [:linear-layout {:orientation :vertical
                   :layout-width :match-parent
                   :layout-height :match-parent
                   :padding [10 :px]
                   }
   [:linear-layout {:id ::top-row-layout
                    :orientation :horizontal
                    :layout-width :match-parent
                    :layout-height :wrap}
    [:button {:id ::prev-bt
              :layout-width :wrap
              :layout-height :wrap
              :text "prev"
              :on-click (fn [_] (let [year (prefs/pref-get prefs/PREF_YEAR)
                                      week (prefs/pref-get prefs/PREF_WEEK)
                                      previous-week (utils/get-previous-week
                                                      week
                                                      year)]
                                  (prefs/pref-set prefs/PREF_YEAR (:year previous-week))
                                  (prefs/pref-set prefs/PREF_WEEK (:week previous-week))))}]
    [:linear-layout {:id ::middle-layout
                     :orientation :horizontal
                     :layout-width [0 :dp]
                     :layout-height :fill
                     :gravity :center
                     :layout-weight 1}
     [:linear-layout {:id ::week-container
                      :orientation :horizontal
                      :layout-width :wrap
                      :layout-height :fill
                      :padding [4 :px]
                      }
      [:text-view {:id ::year-tv
                   :layout-width :wrap
                   :layout-height :fill
                   :padding-left [20 :px]
                   :padding-right [20 :px]
                   :gravity :center_vertical
                   :background-color (get-week-color
                                       (prefs/pref-get prefs/PREF_YEAR)
                                       (prefs/pref-get prefs/PREF_WEEK))
                   :on-click (fn [_] (change-to-current-week))
                   :on-long-click (fn [_] (do
                                            (on-ui
                                              (.showDialog ctx WEEK_DIALOG_ID))
                                            true))
                   :text (str (prefs/pref-get prefs/PREF_YEAR) " / " (prefs/pref-get prefs/PREF_WEEK))}]
      ]
     ]
    [:button {:id ::next-bt
              :layout-width :wrap
              :layout-height :wrap
              :text "next"
              :on-click (fn [_] (let [year (prefs/pref-get prefs/PREF_YEAR)
                                      week (prefs/pref-get prefs/PREF_WEEK)
                                      next-week (utils/get-next-week
                                                  week
                                                  year)]
                                  (prefs/pref-set prefs/PREF_YEAR (:year next-week))
                                  (prefs/pref-set prefs/PREF_WEEK (:week next-week))))}]
    [:button {:id ::toggle-fmt-bt
              :layout-width :wrap
              :layout-height :wrap
              :text "fmt"
              :on-click (fn [_] (toggle-hour-formatter))}]
    ]
   [:linear-layout {:id ::week-layout
                    :orientation :vertical
                    :layout-width :match-parent
                    :layout-height [0 :dp]
                    :layout-weight 1}
    (make-week-list ctx)
    ]
   [:linear-layout {:id ::location-layout
                    :layout-width :wrap
                    :layout-height :wrap
                    :padding-right [10 :px]
                    :layout-gravity :top
                    :orientation :horizontal}
    [:text-view {:id ::location-lat-tv
                 :text (str "lat: " (location/get-latitude))
                 :text-size [10 :dp]}]
    [:text-view {:id ::location-long-tv
                 :text (str "long: " (location/get-longitude))
                 :text-size [10 :dp]}]]
   [:linear-layout {:orientation :horizontal
                    :layout-width :match-parent
                    :layout-height :wrap}
    [:button {:id ::punch-in-bt
              :layout-width :wrap
              :layout-height :wrap
              :text TEXT_PUNCH_IN
              :on-click (fn [_] (punch-in))}]
    [:button {:id ::punch-out-bt
              :layout-width :wrap
              :layout-height :wrap
              :text TEXT_PUNCH_OUT
              :on-click (fn [_] (punch-out))}]
    [:button {:id ::service-bt
              :layout-width :wrap
              :layout-height :wrap
              :text TEXT_SERVICE_START
              :on-click (fn [_] (service-start ctx service))}]
    ; [:button {:id ::wipe-bt
    ;           :layout-width :wrap
    ;           :layout-height :wrap
    ;           :text TEXT_WIPE
    ;           :on-click (fn [_] (wipe-db))}]
    [:button {:id ::wipe-bt
              :layout-width :wrap
              :layout-height :wrap
              :text TEXT_SET_GPS
              :on-click (fn [_] (on-ui (.showDialog ctx GPS_DIALOG_ID)))}]
    ]
]
)

(defn make-week-dialog-layout [ctx]
  (make-ui
    ctx
    [:linear-layout {:id-holder true
                     :layout-width :fill
                     :layout-height :wrap
                     :padding-left [40 :px]
                     :padding-right [60 :px]
                     :orientation :vertical}
     [:edit-text {:id ::year-et
                  :layout-width :fill
                  :layout-height :wrap
                  :layout-gravity :right
                  :input-type :number
                  :hint "Year"
                  :text (str (prefs/pref-get prefs/PREF_YEAR))
                  }
      ]
     [:edit-text {:id ::week-et
                  :layout-width :fill
                  :layout-height :wrap
                  :layout-gravity :right
                  :input-type :number
                  :hint "Week"
                  :text (str (prefs/pref-get prefs/PREF_WEEK))
                  }
      ]]))

(defn make-gps-dialog-layout [ctx]
  (make-ui
    ctx
    [:linear-layout {:id-holder true
                     :layout-width :fill
                     :layout-height :wrap
                     :padding-left [40 :px]
                     :padding-right [60 :px]
                     :orientation :vertical}
     [:edit-text {:id ::lat-et
                  :layout-width :fill
                  :layout-height :wrap
                  :layout-gravity :right
                  :input-type (bit-or InputType/TYPE_CLASS_NUMBER
                                      InputType/TYPE_NUMBER_FLAG_DECIMAL
                                      InputType/TYPE_NUMBER_FLAG_SIGNED)
                  :hint "latitude"
                  :text (str (prefs/pref-get prefs/PREF_DEST_LAT))
                  }
      ]
     [:edit-text {:id ::long-et
                  :layout-width :fill
                  :layout-height :wrap
                  :layout-gravity :right
                  :input-type (bit-or InputType/TYPE_CLASS_NUMBER
                                      InputType/TYPE_NUMBER_FLAG_DECIMAL
                                      InputType/TYPE_NUMBER_FLAG_SIGNED)
                  :hint "longitude"
                  :text (str (prefs/pref-get prefs/PREF_DEST_LONG))
                  }
      ]]))

(defn week-input-valid? [year week]
  (cond
    (not (integer? year))
    (do
      (on-ui (toast "Invalid year" :short)) false)
    (not (integer? week))
    (do
      (on-ui (toast "Invalid week" :short))
      false)
    (> week (utils/weeks-in-year year))
    (do
      (on-ui (toast (str "Not that many weeks in year " year) :short))
      false)
    :else true))

(defn make-week-dialog-callback [dialog-layout]
  (fn [dialog res]
    (let [year (read-string (ui-utils/get-text dialog-layout ::year-et))
          week (read-string (ui-utils/get-text dialog-layout ::week-et))]
      (if (week-input-valid? year week)
        (do
          (prefs/pref-set prefs/PREF_YEAR year)
          (prefs/pref-set prefs/PREF_WEEK week))))))

(defn create-week-dialog [ctx]
  (let [^android.view.ViewGroup dialog-layout (make-week-dialog-layout ctx)]
    (-> ctx
        (alert-dialog-builder
          {:message "Week to display"
           :cancelable true
           :positive-text "Display"
           :positive-callback (make-week-dialog-callback dialog-layout)
           :negative-text "Cancel"
           :negative-callback (fn [_ _] ())})
        (.setView dialog-layout)
        .create)))

(defn make-gps-dialog-callback [dialog-layout]
  (fn [dialog res]
    (let [latitude (read-string (ui-utils/get-text dialog-layout ::lat-et))
          longitude (read-string (ui-utils/get-text dialog-layout ::long-et))]
      (prefs/pref-set prefs/PREF_DEST_LAT latitude)
      (prefs/pref-set prefs/PREF_DEST_LONG longitude))))

(defn create-gps-dialog [ctx]
  (let [^android.view.ViewGroup dialog-layout (make-gps-dialog-layout ctx)]
    (-> ctx
        (alert-dialog-builder
          {:message "GPS"
           :cancelable true
           :positive-text "Set"
           :positive-callback (make-gps-dialog-callback dialog-layout)
           :negative-text "Cancel"
           :negative-callback (fn [_ _] ())})
        (.setView dialog-layout)
        .create)))

(defn make-tick-func [ctx]
  (fn []
    (update-week-list ctx)
    (log/d "main tick thread id " (Thread/currentThread))))

(defactivity org.pipo.MyActivity
  :key :main
  (onCreate
    [this bundle]
    (let [service (Intent. this org.pipo.service)]
      (.superOnCreate this bundle)
      (utils/init-time-zone this)
      (on-ui
        (set-content-view!
          this
          (week-layout this service)))
      (create-watchers this service)
      (prefs/update-state)
      (log/d "main thread id " (Thread/currentThread))
      ))
  (onPrepareDialog
    [this id dialog dialog-bundle]
    (.removeDialog this id))
  (onCreateDialog
    [this id dialog-bundle]
    (cond
      (= id WEEK_DIALOG_ID)
      (create-week-dialog this)
      (= id GPS_DIALOG_ID)
      (create-gps-dialog this)
      :else (toast "Invalid ID" :short)))
  (onStart
    [this]
    (.superOnStart this)
    (reset! tick-receiver (tick/register-receiver this (make-tick-func this) nil))
    )
  (onResume
    [this]
    (let [service (Intent. this org.pipo.service)]
      (.superOnResume this)
      (update-uis this service)
      ))
  (onPause
    [this]
    (.superOnPause this)
    )
  (onStop
    [this]
    (.superOnStop this)
    (tick/unregister-receiver this @tick-receiver)
    (reset! tick-receiver nil)
    )
  (onDestroy
    [this]
    (.superOnDestroy this)
    )
  )

; (prefs/pref-set prefs/PREF_STATE_SERVICE prefs/SERVICE_STOPPED)
