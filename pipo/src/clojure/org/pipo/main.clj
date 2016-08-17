(ns org.pipo.main
  (:require [neko.activity :refer [defactivity set-content-view!]]
            [neko.debug :refer [*a]]
            [neko.threading :refer [on-ui]]
            [neko.find-view :refer [find-view]]
            [neko.ui :refer [config make-ui]]
            [neko.doc :as docc]
            [neko.-utils :refer [int-id]]
            [org.pipo.log :as log]
            [neko.notify :refer [toast]]
            [neko.dialog.alert :refer [alert-dialog-builder]]
            [clj-time.local :as l]
            [clj-time.coerce :as c]
            [clj-time.core :as t]
            [org.pipo.prefs :as prefs]
            [org.pipo.database :as db]
            [org.pipo.utils :as utils]
            [org.pipo.week-fragment :as week-fragment]
            [org.pipo.broadcastreceiver :as tick]
            [org.pipo.location :as location]
            [org.pipo.weekview :as weekview]
            [org.pipo.ui-utils :as ui-utils])
  (:import [android.view ViewGroup Gravity]
           android.graphics.Color
           android.text.InputType
           android.app.Activity
           android.content.Intent
           java.lang.Long
           org.joda.time.DateTime
           ; org.pipo.week_fragment
           ; android.support.v4.app.FragmentManager
           android.support.v4.view.ViewPager))

(def ^:const TEXT_PUNCH_IN "punch in")
(def ^:const TEXT_PUNCH_OUT "punch out")
(def ^:const TEXT_SERVICE_START "Start")
(def ^:const TEXT_SERVICE_STOP "Stop")
(def ^:const TEXT_WIPE "wipe")
(def ^:const TEXT_SET_GPS "GPS")
(def ^:const WEEK_DIALOG_ID 0)
(def ^:const GPS_DIALOG_ID 1)
(def tick-receiver (atom nil))

(defn get-week-color [year week]
  (let [current (utils/get-current-week)]
    (if (and (= (:year current) year) (= (:week current) week))
      Color/GRAY
      Color/DKGRAY)))

(defn update-week-nr-view
  ([ctx view-id year week]
   (ui-utils/set-text ctx view-id (str year " / " week))
   (on-ui
     (config (find-view ctx view-id)
             :background-color
             (get-week-color year week))))
  ([ctx view-id new-state]
   (let [new-year (prefs/pref-get prefs/PREF_YEAR new-state)
         new-week (prefs/pref-get prefs/PREF_WEEK new-state)]
     (update-week-nr-view ctx view-id new-year new-week))))

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
                (db/punch-toggle-validity (db/get-id (db/get-latest-punch-in)))
                (punch-in)
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
                (db/punch-toggle-validity (db/get-id (db/get-latest-punch-out)))
                (punch-out)
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

(defn get-view-pager [ctx]
  (.getChildAt (find-view ctx ::swipe) 0) ;; TODO: any way to replace getChildAt with find-view?
  )

(defn get-pager-adapter [ctx view-pager]
  (org.pipo.week_fragment. ctx (.getFragmentManager ctx) view-pager)
  )

(defn update-uis [ctx service & pref-state]
  (let [state (or (first pref-state) @(prefs/get-prefs))]
    (log/i "update-uis")
    (update-week-nr-view ctx ::year-tv state)
    (update-state-ui ctx state)
    (update-service-ui ctx state service)
    ; (weekview/update-week-list ctx)
    (week-fragment/update-state ctx (get-view-pager ctx))
    ))

(defn create-watchers [ctx service]
  (add-watch (prefs/get-prefs) :year-week-watcher
             (fn [key atom old-state new-state]
               (log/i "pref updated:" new-state)
               (update-uis ctx service new-state)))
  (add-watch (location/get-location-data) :location-watcher
             (fn [key atom old-state new-state]
               (ui-utils/set-text ctx ::location-lat-tv (str "lat: " (:lat new-state)))
               (ui-utils/set-text ctx ::location-long-tv (str "long: " (:long new-state)))))
  )

(defn wipe-db []
  (db/wipe)
  (prefs/update-state))


(defn change-to-current-week [ctx]
  (let [current (utils/get-current-week)]
    ; (week-fragment/update-week-nr-view ctx (:year current) (:week current))
    (update-week-nr-view ctx ::year-tv (:year current) (:week current))
      ; (on-ui (toast (str "update week to" (:week current)) :short))
    (prefs/pref-set prefs/PREF_YEAR (:year current))
    (prefs/pref-set prefs/PREF_WEEK (:week current))
    ))

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
    [:button {:id ::gps-bt
              :layout-width :wrap
              :layout-height :wrap
              :text TEXT_SET_GPS
              :on-click (fn [_] (on-ui (.showDialog ctx GPS_DIALOG_ID)))}]
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
                   :on-click (fn [_] (change-to-current-week ctx))
                   :on-long-click (fn [_] (do
                                            (on-ui
                                              (.showDialog ctx WEEK_DIALOG_ID))
                                            true))
                   :text (str (prefs/pref-get prefs/PREF_YEAR) " / " (prefs/pref-get prefs/PREF_WEEK))}]
      ]
     ]
    [:button {:id ::toggle-fmt-bt
              :layout-width :wrap
              :layout-height :wrap
              :text "fmt"
              :on-click (fn [_] (prefs/toggle-hour-formatter))}]
    ]
   [:linear-layout {:id ::swipe
                    :id-holder true
                    :orientation :horizontal
                    :layout-width :match-parent
                    :layout-height [0 :dp]
                    :layout-weight 1}
    (let [view-pager (ViewPager. ctx)]
      (.setId view-pager (int-id ::pager))
      view-pager
      )
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
              :layout-width [0 :dp]
              :layout-weight 1
              :layout-height :wrap
              :text TEXT_PUNCH_IN
              :on-click (fn [_] (punch-in))}]
    [:button {:id ::punch-out-bt
              :layout-width [0 :dp]
              :layout-weight 1
              :layout-height :wrap
              :text TEXT_PUNCH_OUT
              :on-click (fn [_] (punch-out))}]
    [:button {:id ::service-bt
              :layout-width [0 :dp]
              :layout-weight 1
              :layout-height :wrap
              :text TEXT_SERVICE_START
              :on-click (fn [_] (service-start ctx service))}]
    ; [:button {:id ::wipe-bt
    ;           :layout-width :wrap
    ;           :layout-height :wrap
    ;           :text TEXT_WIPE
    ;           :on-click (fn [_] (wipe-db))}]
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

(defn make-week-dialog-callback [ctx dialog-layout]
  (fn [dialog res]
    (let [year (read-string (ui-utils/get-text dialog-layout ::year-et))
          week (read-string (ui-utils/get-text dialog-layout ::week-et))]
      (if (week-input-valid? year week)
        (do
          (prefs/pref-set prefs/PREF_YEAR year)
          (prefs/pref-set prefs/PREF_WEEK week)
          ; (week-fragment/update-state ctx (get-view-pager ctx))
          )))))

(defn create-week-dialog [ctx]
  (let [^android.view.ViewGroup dialog-layout (make-week-dialog-layout ctx)]
    (-> ctx
        (alert-dialog-builder
          {:message "Week to display"
           :cancelable true
           :positive-text "Display"
           :positive-callback (make-week-dialog-callback ctx dialog-layout)
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
    ; (weekview/update-week-list ctx)
    (week-fragment/update-state ctx (get-view-pager ctx))
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

      (let [
            view-pager (get-view-pager this)
            pager-adapter (get-pager-adapter this view-pager)
            ]

        (.setAdapter view-pager pager-adapter)
        (.setOnPageChangeListener view-pager pager-adapter)
        (.setCurrentItem view-pager 1 false)
        )


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
    )
  (onResume
    [this]
    (let [service (Intent. this org.pipo.service)]
      (.superOnResume this)
      (reset! tick-receiver (tick/register-receiver this (make-tick-func this) nil))
      (update-uis this service)
      ))
  (onPause
    [this]
    (.superOnPause this)
    (tick/unregister-receiver this @tick-receiver)
    (reset! tick-receiver nil)
    )
  (onStop
    [this]
    (.superOnStop this)
    )
  (onDestroy
    [this]
    (.superOnDestroy this)
    )
  )

; (prefs/pref-set prefs/PREF_STATE_SERVICE prefs/SERVICE_STOPPED)
