(ns org.pipo.main
    (:require [neko.activity :refer [defactivity set-content-view!]]
              [neko.debug :refer [*a]]
              [neko.threading :refer [on-ui]]
              [neko.find-view :refer [find-view]]
              [neko.ui :refer [config make-ui]]
              [neko.ui.adapters :refer [cursor-adapter update-cursor]]
              [neko.data.shared-prefs :refer [defpreferences]]
              [neko.log :as log]
              [neko.notify :refer [toast]]
              [neko.dialog.alert :refer [alert-dialog-builder]]
              [clj-time.local :as l]
              [org.pipo.database :as db]
              [org.pipo.utils :as utils])
    (:import [android.widget AbsListView]
             [android.graphics Color]
             [android.view Gravity]))

(def ^:const TEXT_PUNCH_IN "punch in")
(def ^:const TEXT_PUNCH_OUT "punch out")
(def ^:const TEXT_REFRESH "refresh")
(def ^:const TEXT_WIPE "wipe")
(def ^:const STATE_IN "IN")
(def ^:const STATE_OUT "OUT")
(def ^:const WEEK_DIALOG_ID 0)
(defpreferences pipo-prefs "pipo_sp")

; TODO: get the key from the pref-name
(defmacro defpref [pref-name pref-key default]
  `(def ~(vary-meta pref-name assoc :tag `:const) {:key ~pref-key :default ~default}))

(defpref PREF_STATE :state STATE_OUT)
(defpref PREF_YEAR :year 2015)
(defpref PREF_WEEK :week 51)

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

(defn get-day-color [date]
  (if (utils/date-equals? (l/local-now) date)
    Color/GRAY
    Color/DKGRAY))

(defn get-week-color [year week]
  (let [current (utils/get-current-week)]
    (if (and (= (:year current) year) (= (:week current) week))
      Color/GRAY
      Color/DKGRAY)))

(defn make-days-list []
  (concat
    [:linear-layout {:id ::inner-days
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
                                   + (map db/get-hours-duration
                                          (db/get-hours-by-date date))))
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

(defn update-days-list [ctx]
  (on-ui (.removeAllViews (find-view ctx ::inner-days)))
  (on-ui (.addView (find-view ctx ::inner-days) (make-ui ctx (make-days-list)))))

(defn update-week-view [ctx new-state]
  (let [new-year (pref-get PREF_YEAR new-state)
        new-week (pref-get PREF_WEEK new-state)]
  (set-text ctx ::year-tv (str new-year " / " new-week))
  (on-ui
    (config (find-view ctx ::year-tv)
            :background-color
            (get-week-color new-year new-week)))))

(defn create-watchers [ctx]
  ; (add-watch pipo-prefs :state-watcher
  ;            (fn [key atom old-state new-state]
  ;              (set-text ctx ::state-tv (pref-get PREF_STATE new-state))))
  (add-watch pipo-prefs :year-week-watcher
             (fn [key atom old-state new-state]
               (update-week-view ctx new-state)
               (update-days-list ctx))))

(defn get-punch-cursor []
  (db/get-punches-cursor ""))

(defn get-hours-cursor []
  (db/get-hours-cursor ""))

(defn make-punch-adapter [ctx]
  (cursor-adapter
    ctx
    (fn [] [:linear-layout {:id-holder true}
            [:text-view {:id ::caption-tv}]])
    (fn [view _ data]
      (let [tv (find-view view ::caption-tv)]
        (config tv :text (str data))))
    (fn [] (get-punch-cursor)) ;; cursor-fn
    ))

(defn make-hours-adapter [ctx]
  (cursor-adapter
    ctx
    (fn [] [:linear-layout {:id-holder true}
            [:text-view {:id ::caption-tv}]])
    (fn [view _ data]
      (let [tv (find-view view ::caption-tv)]
        (config tv :text (str data))))
    (fn [] (get-hours-cursor))))

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

(defn punch-in [ctx]
  (db/punch-in (l/local-now))
  ; (update-cursors ctx)
  (update-state ctx))

(defn punch-out [ctx]
  (db/punch-out (l/local-now))
  ; (update-cursors ctx)
  (update-state ctx))

(defn wipe-db [ctx]
  (db/wipe)
  ; (update-cursors ctx)
  (update-state ctx))

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
   [:list-view {:id ::hours-list
                :adapter (make-hours-adapter ctx)
                :transcript-mode AbsListView/TRANSCRIPT_MODE_ALWAYS_SCROLL
                :layout-height [0 :dp]
                :layout-weight 1}]
   [:text-view {:id ::state-tv
                :layout-width :fill
                :layout-height :wrap
                :gravity :right
                :layout-gravity :center
                :text (pref-get PREF_STATE)
                }]
   ])

(defn change-to-current-week []
  (let [current (utils/get-current-week)]
    (pref-set PREF_YEAR (:year current))
    (pref-set PREF_WEEK (:week current))))

(defn week-layout [ctx]
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
   [:linear-layout {:id ::days-layout
                    :orientation :vertical
                    :layout-width :match-parent
                    :layout-height [0 :dp]
                    :layout-weight 1}
    (make-days-list)
    ]
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
              :on-click (fn [_] (on-ui (toast "refresh!" :short)))}]
    [:button {:id ::wipe-bt
              :layout-width :wrap
              :layout-height :wrap
              :text TEXT_WIPE
              :on-click (fn [_] (wipe-db ctx))}]
    ]
   ]
  )

(defn make-dialog-layout [ctx]
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
  (let [dialog-layout (make-dialog-layout ctx)]
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

(defactivity org.pipo.MyActivity
  :key :main
  (onCreate [this bundle]
            (.superOnCreate this bundle)
            (on-ui
              (set-content-view!
                this
                ; (main-layout this)))
                (week-layout this)))
            (create-watchers this)
            (update-state this)
            ; (update-cursors this)
            )
  (onPrepareDialog
    [this id dialog dialog-bundle]
    (cond
      (= id WEEK_DIALOG_ID)
      (.removeDialog this id)))
  (onCreateDialog [this id dialog-bundle]
    (cond
      (= id WEEK_DIALOG_ID)
      (create-week-dialog this)
      :else (toast "Invalid ID" :short)))
  )
