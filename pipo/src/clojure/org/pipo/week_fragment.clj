(ns org.pipo.week-fragment
  (:require [neko.activity :refer [simple-fragment]]
            [neko.debug :refer [*a]]
            [neko.ui :refer [config]]
            [neko.notify :refer [toast]]
            [neko.threading :refer [on-ui]]
            [neko.find-view :refer [find-view]]
            [org.pipo.log :as log]
            [org.pipo.weekview :as weekview]
            [org.pipo.prefs :as prefs]
            [org.pipo.utils :as utils]
            [org.pipo.ui-utils :as ui-utils]
            )
  (:import android.support.v4.view.ViewPager
           android.graphics.Color
           )
  (:gen-class
    :prefix "fragment-"
    :extends android.support.v13.app.FragmentStatePagerAdapter
    :implements [android.support.v4.view.ViewPager$OnPageChangeListener]
    :state state
    :init init
    :constructors {[android.content.Context
                    android.app.FragmentManager
                    android.support.v4.view.ViewPager]
                   [android.app.FragmentManager]}
    ))

;; little functions to safely set the fields.
(defn- setfield
  [^org.pipo.week_fragment this key value]
  (swap! (.state this) into {key value}))

(defn- getfield
  [^org.pipo.week_fragment this key]
  (@(.state this) key))

(defn fragment-init [ctx fm view-pager]
  [[fm] (atom {:middle (/ Integer/MAX_VALUE 2)
               :ctx ctx
               :vp view-pager
               :focused-page 0
               })])

(defn fragment-getItemPosition [this object]
  (log/i "getItemPosition called, for" object "returning" (.getId (.getView object)))
  (.getId (.getView object)))


(defn fragment-getItem [this i]
  (let [current-year (prefs/pref-get prefs/PREF_YEAR)
        current-week (prefs/pref-get prefs/PREF_WEEK)
        year-week (case i
                    0 (utils/get-previous-week current-week current-year)
                    1 {:week current-week :year current-year}
                    2 (utils/get-next-week current-week current-year)
                    nil)
        week (:week year-week)
        year (:year year-week)]

    (log/i "getItem called")
    (let [fragment
          (simple-fragment
            (getfield this :ctx)
            [:linear-layout {:orientation :vertical
                             :id-holder true
                             :id i
                             }
             [:text-view {:id ::fragment-text
                          :text (str "fragment " i)}]
             ;; [::inner-week]
             (weekview/make-week-list (getfield this :ctx) year week)  ;; These get recreated, so no other child views!
             ])]
      (.setRetainInstance fragment true)
      fragment)))

(defn fragment-getCount [this] 3)

(defn- set-page-content [ctx view year week]
  (if (nil? view)
    (log/d "set-page-content view == nil")
    (do
      (log/d  "set-page-content view: " view)
      (config (find-view view ::fragment-text)
              :text (str "fragment " (.getId view)))
      (weekview/update-week-list ctx view year week)
    )
    )
  )

(defn find-child-with-id [id view]
  (first
  (filter
    #(= id (.getId %))
    (for [i (range (.getChildCount view))]
      (.getChildAt view i)))))

;; shift-right
(defn move-to-previous [ctx view-pager]
  {:pre [(= 3 (.getChildCount view-pager))]}
  (let [cur-year (prefs/pref-get prefs/PREF_YEAR)
        cur-week (prefs/pref-get prefs/PREF_WEEK)
        view0 (find-child-with-id 0 view-pager)
        view1 (find-child-with-id 1 view-pager)
        view2 (find-child-with-id 2 view-pager)]

    ; move last to first
    (.setId view0 1)
    (.setId view1 2)
    (.setId view2 0)

    ; update first
    (set-page-content
      ctx
      view2
      (:year (utils/get-previous-week cur-week cur-year))
      (:week (utils/get-previous-week cur-week cur-year)))
    ; )
    )
  ;; this forces the adapter to rearrange the pages, by getItemPosition
  (.notifyDataSetChanged (.getAdapter view-pager))
  )

;; shift-left
(defn move-to-next [ctx view-pager]
  {:pre [(= 3 (.getChildCount view-pager))]}
  (let [cur-year (prefs/pref-get prefs/PREF_YEAR)
        cur-week (prefs/pref-get prefs/PREF_WEEK)
        view0 (find-child-with-id 0 view-pager)
        view1 (find-child-with-id 1 view-pager)
        view2 (find-child-with-id 2 view-pager)]

    ; move first to last
    (.setId view0 2)
    (.setId view1 0)
    (.setId view2 1)

    ; update last
    (set-page-content
      ctx
      view0
      (:year (utils/get-next-week cur-week cur-year))
      (:week (utils/get-next-week cur-week cur-year)))
    )
  ;; this forces the adapter to rearrange the pages, by getItemPosition
  (.notifyDataSetChanged (.getAdapter view-pager))
  )

;; TODO: separate fn for recreating and updating (current) day(s)
(defn update-state [ctx view-pager]
  {:pre [(= 3 (.getChildCount view-pager))]}
  (let [cur-year (prefs/pref-get prefs/PREF_YEAR)
        cur-week (prefs/pref-get prefs/PREF_WEEK)
        view0 (find-child-with-id 0 view-pager)
        view1 (find-child-with-id 1 view-pager)
        view2 (find-child-with-id 2 view-pager)]

    ; update first
    (set-page-content
      ctx
      view0
      (:year (utils/get-previous-week cur-week cur-year))
      (:week (utils/get-previous-week cur-week cur-year)))

    ; update current
    (set-page-content ctx view1 cur-year cur-week)

    ; update last
    (set-page-content
      ctx
      view2
      (:year (utils/get-next-week cur-week cur-year))
      (:week (utils/get-next-week cur-week cur-year)))))

(defn fragment-onPageScrollStateChanged [this state]
  (let [ctx (getfield this :ctx)
        view-pager (getfield this :vp)
        ]
    (if (= state ViewPager/SCROLL_STATE_IDLE)
      (let [focused-page (getfield this :focused-page)
            year (prefs/pref-get prefs/PREF_YEAR)
            week (prefs/pref-get prefs/PREF_WEEK)
            prev-yw (utils/get-previous-week week year)
            next-yw (utils/get-next-week week year)
            ]
        (log/i "fragment-onPageScrollStateChanged IDLE, child count:" (.getChildCount view-pager))
        (log/i "focused: " focused-page)
        (case focused-page
          ;; Moved to previous week
          0 (do
              (log/i "state changed, week " week " -> " (:week prev-yw))
              (prefs/pref-set prefs/PREF_WEEK (:week prev-yw))
              (prefs/pref-set prefs/PREF_YEAR (:year prev-yw))
              ; (update-state ctx view-pager focused-page)
              (move-to-previous ctx view-pager)
              )
          2 (do
              ;; Moved to next week
              (log/i "state changed, week " week " -> " (:week next-yw))
              (prefs/pref-set prefs/PREF_WEEK (:week next-yw))
              (prefs/pref-set prefs/PREF_YEAR (:year next-yw))
              ; (update-state ctx view-pager focused-page)
              (move-to-next ctx view-pager)
              )
          nil)
        )

      (do
        (log/i "fragment-onPageScrollStateChanged __")
        )
      )
  )
  )

(defn fragment-onPageScrolled [this position positionOffset positionOffsetPixels]
  )

(defn fragment-onPageSelected [this position]
  (let [focused-page (getfield this :focused-page)
        year (prefs/pref-get prefs/PREF_YEAR)
        week (prefs/pref-get prefs/PREF_WEEK)
        prev-yw (utils/get-previous-week week year)
        next-yw (utils/get-next-week week year)
        ctx (getfield this :ctx)
        ]

    (case position
      ;; Moved to previous week
      0 (do
          ; (setfield this :direction :previous)
          (ui-utils/update-week-nr-view
            ctx
            :org.pipo.main/year-tv
            (:year prev-yw)
            (:week prev-yw)))
      ;; Moved to next week
      2 (do
          ; (setfield this :direction :next)
          (ui-utils/update-week-nr-view
            ctx
            :org.pipo.main/year-tv
            (:year next-yw)
            (:week next-yw)))
      nil)

    (setfield this :focused-page position)
  (log/i "fragment-onPageSelected, " focused-page " -> " position)
  ))
