(ns org.pipo.ui-utils
  (:require
    [org.pipo.utils :as utils]
    [org.pipo.prefs :as prefs]
    [neko.threading :refer [on-ui]]
    [neko.ui :refer [config]]
    [neko.find-view :refer [find-view]])
  (:import android.graphics.Color
    ))

(defn set-text [ctx elmt s]
  (on-ui (config (find-view ctx elmt) :text s)))

(defn get-text [ctx elmt]
  (str (.getText ^android.widget.TextView (find-view ctx elmt))))

(defn get-week-color [year week]
  (let [current (utils/get-current-week)]
    (if (and (= (:year current) year) (= (:week current) week))
      Color/GRAY
      Color/DKGRAY)))

(defn update-week-nr-view
  ([ctx view-id year week]
   (set-text ctx view-id (str year " / " week))
   (on-ui
     (config (find-view ctx view-id)
             :background-color
             (get-week-color year week))))
  ([ctx view-id new-state]
   (let [new-year (prefs/pref-get prefs/PREF_YEAR new-state)
         new-week (prefs/pref-get prefs/PREF_WEEK new-state)]
     (update-week-nr-view ctx view-id new-year new-week))))
