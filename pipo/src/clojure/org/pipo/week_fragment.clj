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

(defn- int-to-year-week [i]
  )

;; little functions to safely set the fields.
(defn- setfield
  [^org.pipo.week_fragment this key value]
  (swap! (.state this) into {key value}))

(defn- getfield
  [^org.pipo.week_fragment this key]
  (@(.state this) key))

(defn fragment-init [ctx fm view-pager]
  ; mViewPager.setOnPageChangeListener(this);

  [[fm] (atom {:middle (/ Integer/MAX_VALUE 2)
               :ctx ctx
               :vp view-pager
               :focused-page 0
               })])

(defn fragment-getItem [this i]
    (let
      [yw (cond (= i 0) (utils/get-previous-week
                          (prefs/pref-get prefs/PREF_WEEK)
                          (prefs/pref-get prefs/PREF_YEAR))
                (= i 1) {:week (prefs/pref-get prefs/PREF_WEEK)
                         :year (prefs/pref-get prefs/PREF_YEAR)}
                (= i 2) (utils/get-next-week
                          (prefs/pref-get prefs/PREF_WEEK)
                          (prefs/pref-get prefs/PREF_YEAR)))
       week (:week yw)
       year (:year yw)
       ]

    (log/d "getItem called")
    (simple-fragment
      (getfield this :ctx)
      [:linear-layout {:orientation :vertical
                       :id-holder true
                       :id i}
       [:text-view {:id ::fragment-text
                    :text (str "fragment " i)}]
       (weekview/make-week-list (getfield this :ctx) year week)  ;; These get recreated, so no other child views!
       ])
    )
    )
    ; }

    ; @Override
    ; public int getCount() {
(defn fragment-getCount [this]
  ; java.lang.Integer
  ; Integer/MAX_VALUE
  3
    )

    ; @Override
    ; public CharSequence getPageTitle(int position) {
    ;     return "OBJECT " + (position + 1);
    ; }

; (defn fragment-instantiateItem [this container position]


(defn- set-page-content [ctx view year week]
  (if (nil? view)
    (do
      (config (find-view view ::fragment-text)
              :text (str "fragment " (.getId view)))
      (weekview/update-week-list ctx view year week)
    )
    )
  )

(defn update-state [ctx view-pager]
  (do
    (log/i "fragment-onPageScrollStateChanged IDLE")

      (doseq [i (range (.getChildCount view-pager))]
        (let [view (.getChildAt view-pager i)
              id (.getId view)
              cur-year (prefs/pref-get prefs/PREF_YEAR)
              cur-week (prefs/pref-get prefs/PREF_WEEK)
              ]

          (if (nil? view) (log/d "view is nil"))
          (if (nil? id) (log/d "id is nil"))

          ; final View v = viewPager.getChildAt(i);
          ; if (v == null)
          ;     continue;

          ; // reveal correct child position
          ; Integer tag = (Integer)v.getTag();
          ; if (tag == null)
          ;     continue;

          (cond
            (= id 0) (set-page-content
                       ctx
                       view
                       (:year (utils/get-previous-week cur-week cur-year))
                       (:week (utils/get-previous-week cur-week cur-year))
                       )
            (= id 1) (set-page-content
                       ctx
                       view
                       cur-year
                       cur-week
                       )
            (= id 2) (set-page-content
                       ctx
                       view
                       (:year (utils/get-next-week cur-week cur-year))
                       (:week (utils/get-next-week cur-week cur-year))
                       )
            )
          )
        )




    ;TODO shuffle the views
    (log/i "setCurrentItem")
    (.setCurrentItem view-pager 1 false))
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
        (if (= focused-page 0) ;; Moved to previous week
          (do
            (log/i "state changed, week " week " -> " (:week prev-yw))
            (prefs/pref-set prefs/PREF_WEEK (:week prev-yw))
            (prefs/pref-set prefs/PREF_YEAR (:year prev-yw))
            )
          )
        (if (= focused-page 2) ;; Moved to next week
          (do
            (log/i "state changed, week " week " -> " (:week next-yw))
            (prefs/pref-set prefs/PREF_WEEK (:week next-yw))
            (prefs/pref-set prefs/PREF_YEAR (:year next-yw))
            )
          )
        )


      ; (update-state ctx view-pager)

    (log/i "fragment-onPageScrollStateChanged __")
    )
  )
  )

(defn fragment-onPageScrolled [this position positionOffset positionOffsetPixels]
  ; (log/d "fragment-onPageScrolled")
  )

(defn get-week-color [year week]
  (let [current (utils/get-current-week)]
    (if (and (= (:year current) year) (= (:week current) week))
      Color/GRAY
      Color/DKGRAY)))

(defn update-week-nr-view [ctx year week]
  (log/i "update-week-nr-view" year " " week)
    (ui-utils/set-text ctx :org.pipo.main/year-tv (str year " / " week))
    (on-ui
      (config (find-view ctx :org.pipo.main/year-tv)
              :background-color
              (get-week-color year week))))

(defn fragment-onPageSelected [this position]
  (let [focused-page (getfield this :focused-page)
        year (prefs/pref-get prefs/PREF_YEAR)
        week (prefs/pref-get prefs/PREF_WEEK)
        prev-yw (utils/get-previous-week week year)
        next-yw (utils/get-next-week week year)
        ctx (getfield this :ctx)
        ]

    (if (= position 0) ;; Moved to previous week
      (do
        (update-week-nr-view ctx (:year prev-yw) (:week prev-yw))
        )
      )
    (if (= position 2) ;; Moved to next week
      (do
        (update-week-nr-view ctx (:year next-yw) (:week next-yw))
        )
      )


    (setfield this :focused-page position)
  (log/i "fragment-onPageSelected, " focused-page " -> " position)
  ))
