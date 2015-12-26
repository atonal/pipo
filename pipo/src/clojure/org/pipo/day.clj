(ns org.pipo.day
    (:require [neko.activity :refer [defactivity set-content-view!]]
              [neko.ui.adapters :refer [cursor-adapter update-cursor]]
              [neko.find-view :refer [find-view]]
              [neko.ui :refer [config]]
              [neko.threading :refer [on-ui]]
              [org.pipo.database :as db]
              )
    (:import [android.widget AbsListView]
             ))

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
   ])


(defactivity org.pipo.MyActivity
  :key :main
  (onCreate
    [this bundle]
      (.superOnCreate this bundle)
      (on-ui
        (set-content-view!
          this
          (main-layout this)))
      ; (create-watchers this)
      ; (update-state this)
      ; (update-cursors this)
     )
  )
