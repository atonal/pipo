(ns org.pipo.week-fragment
  (:require [neko.activity :refer [simple-fragment]]
            [neko.debug :refer [*a]]
            [org.pipo.log :as log]
            )
  (:import android.support.v4.view.ViewPager)
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
               })])

    ; public DemoCollectionPagerAdapter(FragmentManager fm) {
    ;     super(fm);
    ; }

    ; @Override
    ; public Fragment getItem(int i) {
(defn fragment-getItem [this i]
    ;     Fragment fragment = new DemoObjectFragment();
    ;     Bundle args = new Bundle();
    ;     // Our object is just an integer :-P
    ;     args.putInt(DemoObjectFragment.ARG_OBJECT, i + 1);
    ;     fragment.setArguments(args);
    ;     return fragment;
    (simple-fragment (getfield this :ctx) [:linear-layout {:orientation :vertical}
                      [:text-view {:text (str "fragment " i)}]
                      ])
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


(defn fragment-onPageScrollStateChanged [this state]
  (log/d "fragment-onPageScrollStateChanged")
  (if (= state ViewPager/SCROLL_STATE_IDLE)
    ;TODO shuffle the views
    (.setCurrentItem (getfield this :vp) 1 false))
  )

(defn fragment-onPageScrolled [this position positionOffset positionOffsetPixels]
  ; (log/d "fragment-onPageScrolled")
  )

(defn fragment-onPageSelected [this position]
  (log/d "fragment-onPageSelected")
  )
