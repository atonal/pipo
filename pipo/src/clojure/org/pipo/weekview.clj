(ns org.pipo.weekview
  (:require
    [neko.resource :as res]
    [neko.threading :refer [on-ui]]
    [neko.ui :refer [config]]
    [neko.find-view :refer [find-view]]
    [org.pipo.utils :as utils]
    [org.pipo.prefs :as prefs]
    [org.pipo.database :as db]
    [org.pipo.log :as log]
    [clj-time.local :as l]
    [clj-time.core :as t]
    [clj-time.coerce :as c]
    )
  (:import
    android.view.Gravity
    android.graphics.drawable.ColorDrawable
    android.graphics.Color
    android.app.Activity
    android.content.Intent
    android.view.animation.AnimationUtils
    org.joda.time.DateTime
    )
  )

(res/import-all)

(def ^:const MAX_DOTS 3)
(def ^:const EXTRA_DATE "org.pipo.EXTRA_DATE")

(defn make-dot-drawable [ctx i work-count]
  (let [plus (res/get-drawable ctx R$drawable/plus)
        circle (res/get-drawable ctx R$drawable/circle)]
    (if (<= i work-count)
      (if (and (= i MAX_DOTS) (> work-count MAX_DOTS))
        plus
        circle)
      (ColorDrawable. Color/TRANSPARENT))))

(defn make-dot-id [i]
  (keyword (str "dot-" i)) ;; TODO: with namespace, aka ::dot-1
  ; (keyword (str *ns*) (str "dot-" 1))
  )

(defn make-dot [ctx local-date i work-count]
  [:image-view (merge
                 {
                  :id (make-dot-id i)
                  :padding-left [3 :dp]
                  :padding-right [3 :dp]
                  :layout-width [15 :dp] ;; 3dp + 9dp + 3dp
                  :layout-height :fill
                  :scale-type :center
                  }
                 {:image-drawable (make-dot-drawable ctx i work-count)})
   ])

(defn get-work-hours-for-date [^DateTime date]
  (reduce
    + (map db/get-work-hours
           (db/get-work-by-date date))))

(defn add-current-work [^DateTime date]
  (if (utils/date-equals? (l/local-now) date)
    (db/get-time-in-for-date (l/local-now))
    0))

(defn date-text [local-date]
  (utils/date-to-str-day local-date)
  )

(defn time-text [local-date]
  ((prefs/get-hour-formatter)
   (+ (get-work-hours-for-date local-date)
      (add-current-work local-date)))
  )

(defn get-day-color [date]
  (if (utils/date-equals? (l/local-now) date)
    Color/GRAY
    Color/DKGRAY))

(defn start-day-activity [^Activity ctx ^DateTime date]
  (let [^Intent intent (Intent. ctx org.pipo.DayActivity)]
    (.putExtra intent EXTRA_DATE ^Long (c/to-long date))
    (.startActivity ctx intent)))

(defn make-day-layout [ctx local-date]
  [:linear-layout {
                   :id ::linear1
                   :id-holder true
                   :orientation :horizontal
                   :layout-width :fill
                   :layout-height [0 :dp]
                   :layout-weight 1
                   :padding [4 :px]
                   :layout-margin [1 :dp]
                   :on-click (fn [_] (start-day-activity ctx local-date))
                   :background-color (get-day-color local-date)
                   }
   [:text-view {:text (date-text local-date)
                :id ::date-tv
                :gravity :center_vertical
                :layout-width [0 :dp]
                :layout-height :fill
                :layout-weight 1
                :padding-left [20 :px]
                }]
   [:linear-layout {
                    :id ::linear2
                    :orientation :horizontal
                    :layout-width :wrap
                    :layout-height :fill
                    }
    ;; TODO: can this be macroed?
    (make-dot ctx local-date 1 (count (db/get-work-by-date local-date)))
    (make-dot ctx local-date 2 (count (db/get-work-by-date local-date)))
    (make-dot ctx local-date 3 (count (db/get-work-by-date local-date)))
    ]
   [:text-view {:id ::time-tv
                :text (time-text local-date)
                :padding-right [20 :px]
                :padding-left [20 :px]
                :gravity (bit-or Gravity/RIGHT Gravity/CENTER_VERTICAL)
                :layout-width [0 :dp]
                :layout-height :fill
                :layout-weight 2
                }]
   ]
  )

(defn make-week-list [^android.content.Context ctx]
  `[:linear-layout {:id ::inner-week
                    :orientation :vertical
                    :layout-width :match-parent
                    :layout-height :match-parent
                    }
    ~@(map (fn [^DateTime date]
             (let [local-date (utils/to-local-time-zone date)]
               (make-day-layout ctx local-date)
               ))
           (let [year (prefs/pref-get prefs/PREF_YEAR)
                 week (prefs/pref-get prefs/PREF_WEEK)]
             (utils/week-from-week-number week year)))
    ]
  )

(defn update-day-layout [ctx day-view local-date]
  (on-ui
    (config day-view
            :on-click (fn [_] (start-day-activity ctx local-date))
            :background-color (get-day-color local-date)
            )
    (config (find-view day-view ::date-tv)
            :text (date-text local-date)
            )
    (config (find-view day-view ::time-tv)
            :text (time-text local-date)
            )
    (doseq [i (range 1 (+ MAX_DOTS 1))]
      (config (find-view day-view (make-dot-id i))
              :image-drawable (make-dot-drawable ctx i (count (db/get-work-by-date local-date)))
              )
      )
    )
  )

(defn update-animation [ctx day-view local-date]
  (let [end-of-day (utils/day-end local-date)
        work-count (count (db/get-work-by-date local-date))
        current-time-in (db/get-time-in-for-date end-of-day)]
    (log/d "update-animation, time since punch:" current-time-in)
    (on-ui
      (doseq [i (range 1 (+ MAX_DOTS 1))]
        (log/d "update "(make-dot-id i))
        (let [dot-view (find-view day-view (make-dot-id i))]
          (if (> current-time-in 0) ;; Work ongoing
            (do
              (config dot-view :image-drawable (make-dot-drawable ctx i (+ work-count 1)))
              (log/d "i: " i ", work-count: " work-count)
              (if (or (= i (+ work-count 1)) (= i MAX_DOTS))
                (let [animation (AnimationUtils/loadAnimation ctx R$anim/tween)]
                  (log/d "start animation")
                  (.startAnimation dot-view animation)
                  )
                )
              )
            (do
              (log/d "stop animation")
              (.clearAnimation dot-view)
              )))))))

(defn update-week-list [ctx viewcontainer year week]
  (dorun
    (map (fn [^DateTime date]
           (let [local-date (utils/to-local-time-zone date)
                 date-index (- (t/day-of-week date) 1)
                 layout (.getChildAt (find-view viewcontainer ::inner-week) date-index)
                 ]
             (update-day-layout ctx layout local-date)
             (update-animation ctx layout local-date)
             ))

         ; (let [year (prefs/pref-get prefs/PREF_YEAR)
               ; week (prefs/pref-get prefs/PREF_WEEK)]
           (utils/week-from-week-number week year))
         ; )
    )

  )
