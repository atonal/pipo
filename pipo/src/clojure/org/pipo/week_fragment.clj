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
  (filter
    #(= id (.getId %))
    (for [i (range (.getChildCount view))]
      (.getChildAt view i))))

(defn update-state [ctx view-pager focused-page]
  (do
    (log/i "fragment-onPageScrollStateChanged IDLE, child count:" (.getChildCount view-pager))
      (log/i "focused-page:" focused-page)


    (if (= 3 (.getChildCount view-pager))
(let [
              cur-year (prefs/pref-get prefs/PREF_YEAR)
              cur-week (prefs/pref-get prefs/PREF_WEEK)]


  (let [view0 (find-child-with-id 0 view-pager)
        view1 (find-child-with-id 1 view-pager)
        view2 (find-child-with-id 2 view-pager)]
    (log/i "VIEW 2: " view2))

  (let [fuu (for [i (range (.getChildCount view-pager))]
              (.getChildAt view-pager i))]
      (log/i "fuu:" fuu)
      (let [bar (sort-by #(.getId %) fuu)]
      (log/i "sorted fuu:" bar)
      (log/i "interleaved: " (vec (interleave ['view0 'view1 'view2] bar)))

      ; (let (vec (interleave ['view0 'view1 'view2] bar))
      ;   (log/i "VIEW0: " view0))
)
      )



      (case focused-page
        ;; move to previous
        0 (do
      (log/i "move last to first")
            ; remove last
            (let [view-to-move (.getChildAt view-pager 2)]


      (let [
            view0 (.getChildAt view-pager 0)
            view1 (.getChildAt view-pager 1)
            view2 (.getChildAt view-pager 2)
            ]
        (log/i "view0: (" (.getId view0)") " view0)
        (log/i "view1: (" (.getId view1)") " view1)
        (log/i "view2: (" (.getId view2)") " view2)
        )


              (.removeViewAt view-pager 2)
              ; put it in front
              (.addView view-pager view-to-move 0)


      (let [
            view0 (.getChildAt view-pager 0)
            view1 (.getChildAt view-pager 1)
            view2 (.getChildAt view-pager 2)
            ]
        (log/i "view0: (" (.getId view0)") " view0)
        (log/i "view1: (" (.getId view1)") " view1)
        (log/i "view2: (" (.getId view2)") " view2)
        )


; 08-24 22:04:17.636 14250 14250 I pipo    : move last to first
; 08-24 22:04:17.737 14250 14250 I pipo    : view0: ( 1 )  #object[android.widget.LinearLayout 0x34a84d5 android.widget.LinearLayout{34a84d5 V.E...... ........ 0,0-700,784 #1}]
; 08-24 22:04:17.753 14250 14250 I pipo    : view1: ( 0 )  #object[android.widget.LinearLayout 0xcf1a4ea android.widget.LinearLayout{cf1a4ea V.E...... ........ -700,0-0,784 #0}]
; 08-24 22:04:17.770 14250 14250 I pipo    : view2: ( 2 )  #object[android.widget.LinearLayout 0xa1eb3db android.widget.LinearLayout{a1eb3db V.E...... ......ID 700,0-1400,784 #2}]
; 08-24 22:04:17.807 16010 16024 E linker  : "/system/bin/app_process32": ignoring 2-entry DT_PREINIT_ARRAY in shared library!
; 08-24 22:04:17.856 14250 14250 I pipo    : view0: ( 2 )  #object[android.widget.LinearLayout 0xa1eb3db android.widget.LinearLayout{a1eb3db V.E...... ......ID 700,0-1400,784 #2}]
; 08-24 22:04:17.871 14250 14250 I pipo    : view1: ( 1 )  #object[android.widget.LinearLayout 0x34a84d5 android.widget.LinearLayout{34a84d5 V.E...... ........ 0,0-700,784 #1}]
; 08-24 22:04:17.887 14250 14250 I pipo    : view2: ( 0 )  #object[android.widget.LinearLayout 0xcf1a4ea android.widget.LinearLayout{cf1a4ea V.E...... ........ -700,0-0,784 #0}]


              ; update it
              (set-page-content
                ctx
                view-to-move
                (:year (utils/get-previous-week cur-week cur-year))
                (:week (utils/get-previous-week cur-week cur-year)))
              )
            )
        ;; move to next
        2 (do
      (log/i "move first to last")
            ; remove first
            (let [view-to-move (.getChildAt view-pager 0)]
              (.removeViewAt view-pager 0)
              ; put it in last
              (.addView view-pager view-to-move 2)
              ; update it
              ;; TODO
              (set-page-content
                ctx
                view-to-move
                (:year (utils/get-next-week cur-week cur-year))
                (:week (utils/get-next-week cur-week cur-year)))
              )
            )
        nil)
      )
(log/i "childCount != 3")
)


      ; (doseq [i (range (.getChildCount view-pager))]
      ;   (let [view (.getChildAt view-pager i)
      ;         id (.getId view)
      ;         cur-year (prefs/pref-get prefs/PREF_YEAR)
      ;         cur-week (prefs/pref-get prefs/PREF_WEEK)
      ;         ]

      ;     (if (nil? view) (log/d "view is nil")) ;; TODO: if nil, skip rest
      ;     (if (nil? id) (log/d "id is nil")) ;; TODO: if nil, skip rest

      ;     (case id
      ;       0 (set-page-content
      ;           ctx
      ;           view
      ;           (:year (utils/get-previous-week cur-week cur-year))
      ;           (:week (utils/get-previous-week cur-week cur-year)))
      ;       1 (set-page-content
      ;           ctx
      ;           view
      ;           cur-year
      ;           cur-week)
      ;       2 (set-page-content
      ;           ctx
      ;           view
      ;           (:year (utils/get-next-week cur-week cur-year))
      ;           (:week (utils/get-next-week cur-week cur-year)))
      ;       nil)
      ;     )
      ;   )

    ; (log/i "setCurrentItem")
    ; (log/i "child count:" (.getChildCount view-pager))
    ; (.setCurrentItem view-pager 1 false)
    (log/i "new child count:" (.getChildCount view-pager))
)
  )

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

        (log/d "focused: " focused-page)
        (case focused-page
          ;; Moved to previous week
          0 (do
              (log/i "state changed, week " week " -> " (:week prev-yw))
              (prefs/pref-set prefs/PREF_WEEK (:week prev-yw))
              (prefs/pref-set prefs/PREF_YEAR (:year prev-yw))
        (update-state ctx view-pager (getfield this :focused-page))
              )
          2 (do
              ;; Moved to next week
              (log/i "state changed, week " week " -> " (:week next-yw))
              (prefs/pref-set prefs/PREF_WEEK (:week next-yw))
              (prefs/pref-set prefs/PREF_YEAR (:year next-yw))
        (update-state ctx view-pager (getfield this :focused-page))
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
