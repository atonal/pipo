(ns org.pipo.main
    (:require [neko.activity :refer [defactivity set-content-view!]]
              [neko.debug :refer [*a]]
              [neko.threading :refer [on-ui]]
              [neko.find-view :refer [find-view]]
              [neko.ui :refer [config make-ui]]
              [neko.data.shared-prefs :refer [defpreferences]]
              [neko.log :as log]
              [neko.notify :refer [toast]]
              [neko.dialog.alert :refer [alert-dialog-builder]]
              [clj-time.local :as l]
              [clj-time.coerce :as c]
              [org.pipo.database :as db]
              [org.pipo.utils :as utils]
              [org.pipo.location :as location])
    (:import [android.graphics Color]
             [android.view Gravity]
             [android.text InputType]))

(def ^:const TEXT_PUNCH_IN "punch in")
(def ^:const TEXT_PUNCH_OUT "punch out")
(def ^:const TEXT_SERVICE_START "Start")
(def ^:const TEXT_SERVICE_STOP "Stop")
(def ^:const TEXT_WIPE "wipe")
(def ^:const TEXT_SET_GPS "Set GPS")
(def ^:const STATE_IN "IN")
(def ^:const STATE_OUT "OUT")
(def ^:const WEEK_DIALOG_ID 0)
(def ^:const GPS_DIALOG_ID 1)
(def ^:const EXTRA_DATE "org.pipo.EXTRA_DATE")
(def ^:const RADIUS_M 100)
(def location-atom (atom {:lat "" :long ""}))

(defpreferences pipo-prefs "pipo_sp")

; TODO: get the key from the pref-name
(defmacro defpref [pref-name pref-key default]
  `(def ~(vary-meta pref-name assoc :tag `:const) {:key ~pref-key :default ~default}))

(defpref PREF_STATE :state STATE_OUT)
(defpref PREF_YEAR :year 2015)
(defpref PREF_WEEK :week 51)
(defpref PREF_DEST_LAT :lat 0)
(defpref PREF_DEST_LONG :long 0)

(defn pref-set-named [pref-atom pref-name new-val]
  (swap! pref-atom assoc (:key pref-name) new-val))

(defn pref-set [pref-name new-val]
  (pref-set-named pipo-prefs pref-name new-val))

(defn pref-get [pref-name & pref-state]
  (let [pref (or (first pref-state) @pipo-prefs)]
    (or
      ((:key pref-name) pref)
      (:default pref-name))))

(defn set-text [ctx elmt s]
  (on-ui (config (find-view ctx elmt) :text s)))

(defn get-text [ctx elmt]
  (str (.getText ^android.widget.TextView (find-view ctx elmt))))

(defn set-latitude [new-lat]
  (swap! location-atom assoc :lat (str new-lat)))

(defn get-latitude []
  (or (:lat @location-atom) "unknown"))

(defn set-longitude [new-long]
  (swap! location-atom assoc :long (str new-long)))

(defn get-longitude []
  (or (:long @location-atom) "unknown"))

(defn set-location [latitude longitude]
  (set-latitude latitude)
  (set-longitude longitude))

(defn update-state [ctx]
  (let [type-latest (db/get-type (db/get-latest-punch))]
    (if (= type-latest db/IN)
      (do
        (pref-set PREF_STATE STATE_IN)
        (on-ui (config (find-view ctx ::punch-in-bt) :enabled false))
        (on-ui (config (find-view ctx ::punch-out-bt) :enabled true)))
      (do
        (pref-set PREF_STATE STATE_OUT)
        (on-ui (config (find-view ctx ::punch-in-bt) :enabled true))
        (on-ui (config (find-view ctx ::punch-out-bt) :enabled false))))))

(defn make-on-location-fn [ctx]
  (fn [^android.location.Location location]
    (let [latitude (.getLatitude ^android.location.Location location)
          longitude (.getLongitude ^android.location.Location location)
          dest-location (android.location.Location. "pipo")]
      (set-location latitude longitude)
      (.setLatitude dest-location (pref-get PREF_DEST_LAT))
      (.setLongitude dest-location (pref-get PREF_DEST_LONG))
      (let [distance (.distanceTo location dest-location)]
        (on-ui (toast (str "distance: " distance) :short))
        (cond (and (= (pref-get PREF_STATE) STATE_OUT)
                   (< distance RADIUS_M))
              (do
                (db/punch-in-gps (l/local-now))
                (on-ui (toast "GPS punch in" :short))
                (update-state ctx))
              (and (= (pref-get PREF_STATE) STATE_IN)
                   (> distance RADIUS_M))
              (do
                (db/punch-out-gps (l/local-now))
                (on-ui (toast "GPS punch out" :short))
                (update-state ctx))
              :else
              (log/w (str "no GPS punch, state: " (pref-get PREF_STATE) ", distance: " distance))
              )))))

(defn get-day-color [date]
  (if (utils/date-equals? (l/local-now) date)
    Color/GRAY
    Color/DKGRAY))

(defn get-week-color [year week]
  (let [current (utils/get-current-week)]
    (if (and (= (:year current) year) (= (:week current) week))
      Color/GRAY
      Color/DKGRAY)))


(defn start-day-activity [ctx ^org.joda.time.DateTime date]
  (let [intent (android.content.Intent. ctx org.pipo.DayActivity)]
    (.putExtra intent EXTRA_DATE (c/to-long date))
    (.startActivity ctx intent)))

(defn make-week-list [ctx]
  (concat
    [:linear-layout {:id ::inner-week
                     :orientation :vertical
                     :layout-width :match-parent
                     :layout-height :match-parent
                     }
     ]
    (map (fn [^org.joda.time.DateTime date]
           [:linear-layout {:orientation :horizontal
                            :layout-width :fill
                            :layout-height [0 :dp]
                            :layout-weight 1
                            :padding [4 :px]
                            :on-click (fn [_] (start-day-activity ctx date))
                            }
            [:text-view {:text (utils/date-to-str-day date)
                         :gravity :center_vertical
                         :layout-width [0 :dp]
                         :layout-height :fill
                         :layout-weight 1
                         :padding-left [20 :px]
                         :background-color (get-day-color date)
                         }]
            [:text-view {:text (utils/long-to-hms
                                 (reduce
                                   + (map db/get-work-hours
                                          (db/get-work-by-date date))))
                         :padding-right [20 :px]
                         :padding-left [20 :px]
                         :gravity (bit-or Gravity/RIGHT Gravity/CENTER_VERTICAL)
                         :layout-width :wrap
                         :layout-height :fill
                         :background-color (get-day-color date)
                         }]
            ])
         (let [year (pref-get PREF_YEAR)
               week (pref-get PREF_WEEK)]
           (utils/week-from-week-number week year)))))

(defn update-week-list [ctx]
  (on-ui (.removeAllViews (find-view ctx ::inner-week)))
  (on-ui (.addView (find-view ctx ::inner-week) (make-ui ctx (make-week-list ctx)))))

(defn update-week-nr-view [ctx new-state]
  (let [new-year (pref-get PREF_YEAR new-state)
        new-week (pref-get PREF_WEEK new-state)]
  (set-text ctx ::year-tv (str new-year " / " new-week))
  (on-ui
    (config (find-view ctx ::year-tv)
            :background-color
            (get-week-color new-year new-week)))))

(defn create-watchers [ctx]
  (add-watch pipo-prefs :year-week-watcher
             (fn [key atom old-state new-state]
               (update-week-nr-view ctx new-state)
               (update-week-list ctx)))
  (add-watch location-atom :location-watcher
             (fn [key atom old-state new-state]
               (set-text ctx ::location-lat-tv (str "lat: " (:lat new-state)))
               (set-text ctx ::location-long-tv (str "long: " (:long new-state)))))
  )

(defn punch-in [ctx]
  (db/punch-in-manual (l/local-now))
  (update-state ctx))

(defn punch-out [ctx]
  (db/punch-out-manual (l/local-now))
  (update-state ctx))

(defn wipe-db [ctx]
  (db/wipe)
  (update-state ctx))


(defn change-to-current-week []
  (let [current (utils/get-current-week)]
    (pref-set PREF_YEAR (:year current))
    (pref-set PREF_WEEK (:week current))))

(declare service-start)
(defn service-stop [ctx service]
  (.stopService ctx service)
  (set-text ctx ::service-bt TEXT_SERVICE_START)
  (on-ui (config (find-view ctx ::service-bt) :on-click (fn [_] (service-start ctx service))))
  (location/stop-location-updates)
  )

(defn service-start [ctx service]
  (.startService ctx service)
  (set-text ctx ::service-bt TEXT_SERVICE_STOP)
  (on-ui (config (find-view ctx ::service-bt) :on-click (fn [_] (service-stop ctx service))))
  (location/start-location-updates (make-on-location-fn ctx))
  )

(defn week-layout [ctx service]
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
              :on-click (fn [_] (let [year (pref-get PREF_YEAR)
                                      week (pref-get PREF_WEEK)
                                      previous-week (utils/get-previous-week
                                                      week
                                                      year)]
                                  (pref-set PREF_YEAR (:year previous-week))
                                  (pref-set PREF_WEEK (:week previous-week))))}]
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
                                       (pref-get PREF_YEAR)
                                       (pref-get PREF_WEEK))
                   :on-click (fn [_] (change-to-current-week))
                   :on-long-click (fn [_] (do
                                            (on-ui
                                              (.showDialog ctx WEEK_DIALOG_ID))
                                            true))
                   :text (str (pref-get PREF_YEAR) " / " (pref-get PREF_WEEK))}]
      ]
     ]
    [:button {:id ::next-bt
              :layout-width :wrap
              :layout-height :wrap
              :text "next"
              :on-click (fn [_] (let [year (pref-get PREF_YEAR)
                                      week (pref-get PREF_WEEK)
                                      next-week (utils/get-next-week
                                                  week
                                                  year)]
                                  (pref-set PREF_YEAR (:year next-week))
                                  (pref-set PREF_WEEK (:week next-week))))}]
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
                 :text (str "lat: " (get-latitude))
                 :text-size [10 :dp]}]
    [:text-view {:id ::location-long-tv
                 :text (str "long: " (get-longitude))
                 :text-size [10 :dp]}]]
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
    [:button {:id ::service-bt
              :layout-width :wrap
              :layout-height :wrap
              :text TEXT_SERVICE_START
              :on-click (fn [_] (service-start ctx service))}]
    ; [:button {:id ::wipe-bt
    ;           :layout-width :wrap
    ;           :layout-height :wrap
    ;           :text TEXT_WIPE
    ;           :on-click (fn [_] (wipe-db ctx))}]
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
                  :text (str (pref-get PREF_YEAR))
                  }
      ]
     [:edit-text {:id ::week-et
                  :layout-width :fill
                  :layout-height :wrap
                  :layout-gravity :right
                  :input-type :number
                  :hint "Week"
                  :text (str (pref-get PREF_WEEK))
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
                  :text (str (pref-get PREF_DEST_LAT))
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
                  :text (str (pref-get PREF_DEST_LONG))
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

(defn create-week-dialog [ctx]
  (let [dialog-layout (make-week-dialog-layout ctx)]
    (-> ctx
        (alert-dialog-builder
          {:message "Week to display"
           :cancelable true
           :positive-text "Set"
           :positive-callback (fn [dialog res]
                                (let [year (read-string
                                             (get-text dialog-layout ::year-et))
                                      week (read-string
                                             (get-text dialog-layout ::week-et))]
                                  (if (week-input-valid? year week)
                                    (do
                                      (pref-set PREF_YEAR year)
                                      (pref-set PREF_WEEK week)))))
           :negative-text "Cancel"
           :negative-callback (fn [_ _] ())})
        (.setView dialog-layout)
        .create)))

(defn create-gps-dialog [ctx]
  (let [dialog-layout (make-gps-dialog-layout ctx)]
    (-> ctx
        (alert-dialog-builder
          {:message "GPS"
           :cancelable true
           :positive-text "Set"
           :positive-callback (fn [dialog res]
                                (let [latitude (read-string
                                                 (get-text dialog-layout ::lat-et))
                                      longitude (read-string
                                                  (get-text dialog-layout ::long-et))]
                                  (pref-set PREF_DEST_LAT latitude)
                                  (pref-set PREF_DEST_LONG longitude)))
           :negative-text "Cancel"
           :negative-callback (fn [_ _] ())})
        (.setView dialog-layout)
        .create)))

(defactivity org.pipo.MyActivity
  :key :main
  (onCreate
    [this bundle]
    (let [service (android.content.Intent. this org.pipo.service)]
      (.superOnCreate this bundle)
      (on-ui
        (set-content-view!
          this
          ; (main-layout this)))
          (week-layout this service)))
      (create-watchers this)
      (update-state this)
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
  )
